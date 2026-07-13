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
     * Hostnames a <b>native Ollama provider</b> is trusted to reach even when private targets are
     * not globally opted in. Ollama is nearly always run on the same host or Docker host as the
     * platform itself, so requiring every Ollama user to flip
     * {@code BROKSFORGE_MODEL_ALLOW_PRIVATE_TARGETS} — which would also open every OTHER outbound
     * call (Custom REST agents included) to arbitrary private targets — is the wrong trade-off.
     * This allowlist is deliberately narrow: only these three well-known "the Ollama daemon is
     * local" hostnames, and only when the caller has independently established the target is a
     * native Ollama endpoint (provider type or resolved adapter), never a blanket bypass.
     */
    private static final Set<String> TRUSTED_OLLAMA_HOSTNAMES = Set.of(
            "localhost", "127.0.0.1", "host.docker.internal");

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
        return check(rawUrl, allowPrivateTargets, false);
    }

    /**
     * Decides whether {@code rawUrl} may be contacted.
     *
     * @param rawUrl              the target URL
     * @param allowPrivateTargets when true, private/loopback targets are permitted for ANY target
     * @param trustedOllamaTarget when true, {@code localhost}/{@code 127.0.0.1}/
     *                            {@code host.docker.internal} are permitted regardless of
     *                            {@code allowPrivateTargets} — set this only when the caller has
     *                            already confirmed the target is a native Ollama provider (by
     *                            provider type or resolved adapter), never based on the URL alone.
     *                            Every other private/loopback/link-local target, and every other
     *                            provider type, is still blocked exactly as before.
     */
    public Decision check(String rawUrl, boolean allowPrivateTargets, boolean trustedOllamaTarget) {
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
        if (trustedOllamaTarget && TRUSTED_OLLAMA_HOSTNAMES.contains(host.toLowerCase(Locale.ROOT))) {
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
