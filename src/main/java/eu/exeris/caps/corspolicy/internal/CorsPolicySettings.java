package eu.exeris.caps.corspolicy.internal;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link DefaultCorsPolicy}.
 *
 * <p>Internal on purpose. A sibling cap that wanted to construct one of these would be
 * reaching past {@code CorsPolicy} into this cap's implementation, which the Wall
 * refuses (ADR-055). Configuration reaches a cap through its composition, not through
 * a cross-cap import.
 *
 * @param allowedOrigins exact origins to allow, or the single entry {@code "*"} for any
 * @param allowedMethods methods to permit
 * @param allowedHeaders request headers to permit, matched case-insensitively
 * @param maxAge         preflight cache lifetime, or {@code null} to advertise none
 * @param allowCredentials whether to permit credentialed requests
 * @since 0.1.0
 */
public record CorsPolicySettings(
        Set<String> allowedOrigins,
        Set<String> allowedMethods,
        Set<String> allowedHeaders,
        Duration maxAge,
        boolean allowCredentials) {

    /** The wildcard origin token. */
    static final String ANY_ORIGIN = "*";

    public CorsPolicySettings {
        allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
        allowedMethods = allowedMethods == null ? Set.of() : Set.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null ? Set.of() : Set.copyOf(allowedHeaders);

        // Fetch spec: a wildcard origin and credentials are mutually exclusive. A browser
        // rejects "Access-Control-Allow-Origin: *" on a credentialed request, so a config
        // asking for both cannot be honoured. Failing here beats silently dropping one
        // half at request time and leaving an operator to wonder which.
        if (allowCredentials && allowedOrigins.contains(ANY_ORIGIN)) {
            throw new IllegalArgumentException(
                    "allowCredentials=true is incompatible with the wildcard origin \"*\" — "
                            + "browsers reject a credentialed response carrying it. "
                            + "List the origins explicitly, or turn credentials off.");
        }
    }

    /** Permissive defaults for local development. Never credentialed — see above. */
    public static CorsPolicySettings permissive() {
        return new CorsPolicySettings(
                Set.of(ANY_ORIGIN),
                Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                Set.of("Content-Type", "Authorization"),
                Duration.ofMinutes(10),
                false);
    }

    /** Allows exactly {@code origins}, with credentials enabled. */
    public static CorsPolicySettings forOrigins(List<String> origins) {
        return new CorsPolicySettings(
                Set.copyOf(origins),
                Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                Set.of("Content-Type", "Authorization"),
                Duration.ofMinutes(10),
                true);
    }
}
