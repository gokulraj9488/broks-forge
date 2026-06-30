package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API key configuration bound from {@code broksforge.security.api-key.*}.
 *
 * @param prefix short, human-recognisable prefix prepended to every generated
 *               key (the secret part is hashed; the prefix is stored in clear
 *               text only so keys can be displayed and looked up efficiently)
 */
@ConfigurationProperties(prefix = "broksforge.security.api-key")
public record ApiKeyProperties(String prefix) {
}
