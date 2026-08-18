package eu.exeris.caps.corspolicy.api;

import java.util.List;
import java.util.Objects;

/**
 * The cross-origin-relevant slice of an inbound request.
 *
 * <p>Deliberately not a view over any HTTP request type. A consuming cap projects
 * whatever it owns into this record, which keeps {@link CorsPolicy} transport-blind
 * and keeps this cap's compile classpath free of any host-runtime package — the
 * first cap-tier Wall prohibition (ADR-024 predicate 4).
 *
 * @param origin           the {@code Origin} header value, or {@code null} when absent.
 *                         Absent means same-origin, which is not this cap's business.
 * @param method           the HTTP method being attempted. For a preflight this is the
 *                         value of {@code Access-Control-Request-Method}, <em>not</em>
 *                         {@code OPTIONS} — the distinction is the usual source of
 *                         subtly wrong CORS implementations.
 * @param requestedHeaders headers the client declared via
 *                         {@code Access-Control-Request-Headers}; empty for a simple
 *                         request. Never {@code null}.
 * @param preflight        whether this is a preflight ({@code OPTIONS} carrying
 *                         {@code Access-Control-Request-Method}) rather than an actual
 *                         request.
 * @since 0.1.0
 */
public record CorsRequest(
        String origin,
        String method,
        List<String> requestedHeaders,
        boolean preflight) {

    /**
     * Normalizes on construction so downstream code never branches on null: a null
     * header list becomes empty, and the list is defensively copied to an immutable
     * one. {@code origin} stays nullable on purpose — "no Origin header" is a
     * meaningful, distinct state that an empty string would blur.
     */
    public CorsRequest {
        Objects.requireNonNull(method, "method");
        requestedHeaders = requestedHeaders == null ? List.of() : List.copyOf(requestedHeaders);
    }

    /** A simple (non-preflight) request with no declared headers. */
    public static CorsRequest simple(String origin, String method) {
        return new CorsRequest(origin, method, List.of(), false);
    }

    /** A preflight for {@code method}, declaring {@code requestedHeaders}. */
    public static CorsRequest preflight(String origin, String method, List<String> requestedHeaders) {
        return new CorsRequest(origin, method, requestedHeaders, true);
    }

    /** Whether the request carried an {@code Origin} header at all. */
    public boolean isCrossOrigin() {
        return origin != null && !origin.isBlank();
    }
}
