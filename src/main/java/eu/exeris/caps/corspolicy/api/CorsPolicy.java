package eu.exeris.caps.corspolicy.api;

/**
 * Decides whether a cross-origin request is permitted, and what the response must
 * say about it.
 *
 * <p>This is the whole of the cap's public surface — the only type another cap may
 * name. Implementations live in {@code ..corspolicy.internal} and are not reachable
 * across a cap boundary; the cap-tier Wall enforces that mechanically (ADR-055).
 *
 * <h2>What this deliberately is not</h2>
 * <p>A pure function, not a filter, interceptor, or handler. It reads no socket,
 * writes no header, and knows nothing about HTTP transport — a consuming Gateway cap
 * applies the {@link CorsDecision} to whatever response type it owns.
 *
 * <p>That is a boundary decision rather than minimalism. CORS is a response-header
 * concern, and the kernel exposes no response-header seam to bind to: ADR-061's
 * {@code HttpRoutePolicy} governs route <em>authorization</em>, which is a different
 * axis. Inventing a binding here would put transport knowledge inside a cap, which is
 * exactly what the Wall exists to prevent. Keeping the surface a decision function
 * means this cap needs no kernel SPI at all, and its jar carries no runtime
 * dependency of any kind.
 *
 * <p>Implementations must be thread-safe and free of request-scoped state: a single
 * instance serves every concurrent request in a composition.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface CorsPolicy {

    /**
     * Evaluates one cross-origin request.
     *
     * @param request the request to judge; never {@code null}
     * @return the decision; never {@code null}. A denial is a normal return value,
     *         never an exception — a rejected origin is an expected outcome, not a
     *         failure of this call.
     */
    CorsDecision evaluate(CorsRequest request);
}
