package com.broksforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Brok's Forge API.
 *
 * <p>Brok's Forge is a modular monolith. Each feature lives under
 * {@code com.broksforge.modules} and is wired through clearly defined service
 * boundaries so that individual modules can later be extracted into independent
 * microservices without rewriting their internals.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BroksForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BroksForgeApplication.class, args);
    }
}
