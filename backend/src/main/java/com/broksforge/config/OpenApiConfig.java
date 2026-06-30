package com.broksforge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger definition. Two security schemes are advertised:
 * <ul>
 *     <li><b>bearerAuth</b> &mdash; user JWT access tokens</li>
 *     <li><b>apiKeyAuth</b> &mdash; programmatic project API keys</li>
 * </ul>
 * The Swagger UI is served at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String API_KEY_SCHEME = "apiKeyAuth";

    @Bean
    public OpenAPI broksForgeOpenAPI(@Value("${server.port:8080}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Brok's Forge API")
                        .description("The Engineering Platform for AI Agents. Multi-tenant agent registry, "
                                + "datasets, prompts, provider-agnostic evaluation, benchmarking, regression "
                                + "detection, analytics, reporting, and the AI Engineering Advisor "
                                + "(recommendations, root-cause analysis, AI debugger, knowledge graph).")
                        .version("1.0.0")
                        .contact(new Contact().name("Brok's Forge").email("support@broksforge.dev"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url("http://localhost:" + port).description("Local")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token obtained from /api/v1/auth/login"))
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Project API key in the form bf_<id>.<secret>")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
