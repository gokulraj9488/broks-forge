package com.broksforge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logs the effective datasource URL (password redacted), active profile, and Flyway/JPA
 * configuration <em>before</em> Spring attempts to build the DataSource, run Flyway, or
 * create the JPA {@code EntityManagerFactory} — all of which happen ahead of the first
 * repository bean.
 *
 * <p>This exists because a DataSource/Flyway/EntityManagerFactory failure surfaces to the
 * log only as a downstream symptom — e.g. {@code UnsatisfiedDependencyException} on the
 * first {@code @Repository} bean that needs {@code entityManagerFactory}, with a message
 * like {@code "Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'"}. That
 * symptom names whichever repository happened to be instantiated first — it says nothing
 * about *why* the EntityManagerFactory itself failed. The real cause is always further up
 * the same stack trace, in a {@code Caused by:} for the datasource/Flyway/Hibernate
 * bootstrap. Printing the resolved configuration immediately before that bootstrap begins
 * means the log directly above any such failure already answers "which database, which
 * profile, what was Flyway/Hibernate configured to do" without needing to reproduce the
 * failure with debug logging.</p>
 *
 * <p>Registered via {@link org.springframework.boot.SpringApplication#addListeners} in
 * {@code BroksForgeApplication.main} (not a {@code @Component}) specifically so it fires on
 * {@link ApplicationEnvironmentPreparedEvent} — after property sources resolve but before
 * any bean, including the DataSource, is created. A regular Spring-managed bean has no such
 * guarantee: its own creation order relative to the DataSource/EntityManagerFactory is not
 * something application code controls.</p>
 */
public class StartupDiagnosticsLogger implements ApplicationListener<ApplicationEnvironmentPreparedEvent>,
        Ordered {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnosticsLogger.class);

    // Matches credentials embedded in a URL's authority (jdbc:postgresql://user:pass@host:port/db)
    // and common credential-bearing query parameters, so both are redacted before logging.
    private static final Pattern USER_INFO = Pattern.compile("://([^/@]+)@");
    private static final Pattern CREDENTIAL_QUERY_PARAM =
            Pattern.compile("(?i)([?&](?:password|pwd|secret|token|apikey)=)[^&]*");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();

        String activeProfiles = env.getActiveProfiles().length == 0
                ? "(none active — defaulting to '" + String.join(",", env.getDefaultProfiles()) + "')"
                : String.join(",", env.getActiveProfiles());

        String datasourceUrl = env.getProperty("spring.datasource.url");
        String maskedUrl = maskCredentials(datasourceUrl);

        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto", "(unset)");
        String flywayEnabled = env.getProperty("spring.flyway.enabled", "true");
        String flywayLocations = env.getProperty("spring.flyway.locations", "(default)");
        String flywayBaselineOnMigrate = env.getProperty("spring.flyway.baseline-on-migrate", "false");

        log.info("=================== Startup diagnostics ===================");
        log.info("Active profile(s):        {}", activeProfiles);
        log.info("Datasource URL:            {}", maskedUrl == null ? "(NOT SET)" : maskedUrl);
        log.info("Datasource username set:   {}", env.containsProperty("spring.datasource.username"));
        log.info("JPA ddl-auto:              {}", ddlAuto);
        log.info("Flyway enabled:            {}", flywayEnabled);
        log.info("Flyway locations:          {}", flywayLocations);
        log.info("Flyway baseline-on-migrate:{}", flywayBaselineOnMigrate);
        log.info("=============================================================");
        log.info("If startup fails below with a repository/EntityManagerFactory error "
                + "(e.g. \"Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'\"), "
                + "that is a downstream symptom — search this log for the first \"Caused by:\" "
                + "emitted by Flyway, HikariCP, or Hibernate between this banner and that error; "
                + "the datasource/profile printed above is what produced it.");
    }

    /** Redacts credentials embedded in the URL's authority or in a credential-bearing query param. */
    static String maskCredentials(String url) {
        if (url == null) {
            return null;
        }
        String result = url;
        Matcher userInfo = USER_INFO.matcher(result);
        if (userInfo.find()) {
            result = result.substring(0, userInfo.start(1)) + "***" + result.substring(userInfo.end(1));
        }
        result = CREDENTIAL_QUERY_PARAM.matcher(result).replaceAll("$1***");
        return result;
    }
}
