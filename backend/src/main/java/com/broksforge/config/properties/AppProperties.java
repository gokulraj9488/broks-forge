package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * General application configuration bound from {@code broksforge.app.*}.
 *
 * @param publicUrl externally reachable base URL of the web application, used
 *                  when building links inside e-mails (verification, password
 *                  reset, etc.)
 */
@ConfigurationProperties(prefix = "broksforge.app")
public record AppProperties(String publicUrl) {
}
