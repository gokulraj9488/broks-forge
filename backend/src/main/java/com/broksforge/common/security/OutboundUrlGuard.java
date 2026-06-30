package com.broksforge.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Server-Side Request Forgery (SSRF) guard for outbound calls the platform makes
 * to user-supplied agent endpoints (health probes, and future invocation).
 *
 * <p>Registration accepts syntactically valid URLs (an organization may legitimately
 * register an internal agent); this guard is the <em>runtime</em> control that
 * decides, per call, whether a target may be reached. By default private,
 * loopback, link-local and cloud-metadata addresses are blocked. Operators can
 * opt in to private targets (e.g. for local development) via configuration.</p>
 */
@Slf4j
@Component
public class OutboundUrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "metadata.google.internal", "metadata", "localhost");

    /**
     * The outcome of a guard check.
     *
     * @param allowed whether the target may be contacted
     * @param reason  human-readable explanation when {@code allowed} is false
     */
    public record Decision(boolean allowed, String reason) {
        static Decision allow() {
            return new Decision(true, null);
        }

        static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    /**
     * Decides whether {@code rawUrl} may be contacted.
     *
     * @param rawUrl              the target URL
     * @param allowPrivateTargets when true, private/loopback targets are permitted
     */
    public Decision check(String rawUrl, boolean allowPrivateTargets) {
        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            return Decision.deny("Endpoint URL is not a valid URI");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return Decision.deny("Only http and https endpoints are supported");
        }
        if (uri.getUserInfo() != null) {
            return Decision.deny("Endpoint URL must not contain embedded credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Decision.deny("Endpoint URL has no host");
        }
        if (allowPrivateTargets) {
            return Decision.allow();
        }
        if (BLOCKED_HOSTNAMES.contains(host.toLowerCase(Locale.ROOT))
                || host.toLowerCase(Locale.ROOT).endsWith(".internal")
                || host.toLowerCase(Locale.ROOT).endsWith(".local")) {
            return Decision.deny("Target host is not permitted by network policy");
        }

        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return Decision.deny("Target host could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                return Decision.deny("Target resolves to a private or reserved address");
            }
        }
        return Decision.allow();
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local addresses (fc00::/7) are not flagged by isSiteLocalAddress.
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
