package com.broksforge.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupFailureDiagnosticsLoggerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logbackLogger;

    @BeforeEach
    void attachAppender() {
        logbackLogger = (Logger) LoggerFactory.getLogger(StartupFailureDiagnosticsLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        logbackLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void detachAppender() {
        logbackLogger.detachAppender(appender);
    }

    @Test
    void logsTheFullCauseChainAndTheRootCauseSpecifically() {
        Exception root = new IllegalStateException("connection refused to jdbc:postgresql://user:s3cr3t@localhost:5432/db");
        Exception middle = new RuntimeException("Failed to configure a DataSource", root);
        Exception outer = new RuntimeException("Error creating bean 'entityManagerFactory'", middle);

        new StartupFailureDiagnosticsLogger().onApplicationEvent(
                new ApplicationFailedEvent(new SpringApplication(), new String[0],
                        new GenericApplicationContext(), outer));

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        String joined = String.join("\n", messages);

        assertThat(joined).contains("STARTUP FAILED");
        assertThat(joined).contains("java.lang.IllegalStateException");
        assertThat(joined).contains("3 level(s)");
        assertThat(joined).contains("Error creating bean 'entityManagerFactory'");
        assertThat(joined).contains("Failed to configure a DataSource");
        assertThat(joined).doesNotContain("s3cr3t");
        assertThat(joined).contains("***");

        // The root cause's stack trace is logged as a throwable argument, not string-formatted
        // into the message — assert it was attached to the right log event instead.
        boolean rootCauseThrowableLogged = appender.list.stream()
                .anyMatch(e -> e.getThrowableProxy() != null
                        && "java.lang.IllegalStateException".equals(e.getThrowableProxy().getClassName()));
        assertThat(rootCauseThrowableLogged).isTrue();
    }
}
