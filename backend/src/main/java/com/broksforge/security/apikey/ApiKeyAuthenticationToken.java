package com.broksforge.security.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Authentication holding an {@link ApiKeyPrincipal}. Always created in an
 * already-authenticated state because it is only instantiated after the key has
 * been verified.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    public static final String ROLE_API_KEY = "ROLE_API_KEY";

    private final transient ApiKeyPrincipal principal;

    public ApiKeyAuthenticationToken(ApiKeyPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority(ROLE_API_KEY)));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public ApiKeyPrincipal getPrincipal() {
        return principal;
    }
}
