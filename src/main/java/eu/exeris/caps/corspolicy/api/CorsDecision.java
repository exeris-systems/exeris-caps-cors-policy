package eu.exeris.caps.corspolicy.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The outcome of evaluating a {@link CorsRequest}: whether to allow it, and the exact
 * response headers that decision implies.
 *
 * <p>{@link #headers()} exists so a consuming cap does not re-derive the header names
 * and re-introduce the spelling bugs this cap is meant to centralize — but it returns
 * a plain {@code Map}, so applying it stays the consumer's job and this cap still
 * touches no response object.
 *
 * @param allowed        whether the request is permitted at all
 * @param allowedOrigin  the value for {@code Access-Control-Allow-Origin}, or
 *                       {@code null} when denied
 * @param allowedMethods methods to advertise on a preflight; empty otherwise
 * @param allowedHeaders headers to advertise on a preflight; empty otherwise
 * @param maxAge         how long a preflight result may be cached, or {@code null} to
 *                       advertise nothing
 * @param credentials    whether {@code Access-Control-Allow-Credentials: true} applies
 * @since 0.1.0
 */
public record CorsDecision(
        boolean allowed,
        String allowedOrigin,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        Duration maxAge,
        boolean credentials) {

    /** The single denial value. Carries no headers — a denied request is told nothing. */
    private static final CorsDecision DENIED =
            new CorsDecision(false, null, List.of(), List.of(), null, false);

    public CorsDecision {
        allowedMethods = allowedMethods == null ? List.of() : List.copyOf(allowedMethods);
        allowedHeaders = allowedHeaders == null ? List.of() : List.copyOf(allowedHeaders);
    }

    /** Denies the request. Shared instance — the type is immutable and identity-free. */
    public static CorsDecision denied() {
        return DENIED;
    }

    /**
     * Renders the decision as response headers.
     *
     * <p>Empty when denied: the correct response to a disallowed origin is to omit the
     * CORS headers entirely and let the browser refuse, not to send a header saying
     * "no". Emitting {@code Access-Control-Allow-Origin: null} is a real footgun — the
     * literal string {@code "null"} is a valid origin that sandboxed documents send,
     * so it can grant access rather than deny it.
     *
     * @return an immutable map, possibly empty; never {@code null}
     */
    public Map<String, String> headers() {
        if (!allowed || allowedOrigin == null) {
            return Map.of();
        }
        var out = new java.util.LinkedHashMap<String, String>();
        out.put("Access-Control-Allow-Origin", allowedOrigin);
        if (credentials) {
            out.put("Access-Control-Allow-Credentials", "true");
        }
        if (!allowedMethods.isEmpty()) {
            out.put("Access-Control-Allow-Methods", String.join(", ", allowedMethods));
        }
        if (!allowedHeaders.isEmpty()) {
            out.put("Access-Control-Allow-Headers", String.join(", ", allowedHeaders));
        }
        if (maxAge != null) {
            out.put("Access-Control-Max-Age", Long.toString(maxAge.toSeconds()));
        }
        // Any origin-dependent response must vary on Origin, or a shared cache will
        // serve one origin's allow-header to another. Unconditional on the allow path.
        out.put("Vary", "Origin");
        return Map.copyOf(out);
    }
}
