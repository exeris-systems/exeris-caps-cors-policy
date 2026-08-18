package eu.exeris.caps.corspolicy.internal;

import eu.exeris.caps.corspolicy.api.CorsDecision;
import eu.exeris.caps.corspolicy.api.CorsPolicy;
import eu.exeris.caps.corspolicy.api.CorsRequest;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The stock {@link CorsPolicy}: exact-match origins with an optional wildcard,
 * case-insensitive header matching, and fail-closed defaults.
 *
 * <p>Immutable and thread-safe. All matching sets are precomputed at construction, so
 * {@code evaluate} allocates only the decision itself.
 *
 * @since 0.1.0
 */
public final class DefaultCorsPolicy implements CorsPolicy {

    private final CorsPolicySettings settings;
    private final boolean anyOrigin;
    /** Case-insensitive view of the allowed request headers, for O(log n) lookup. */
    private final Set<String> headersCaseInsensitive;
    /** Root-locale-uppercased view of the allowed methods, matching what is advertised. */
    private final Set<String> methodsNormalized;
    private final List<String> advertisedMethods;
    private final List<String> advertisedHeaders;

    public DefaultCorsPolicy(CorsPolicySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.anyOrigin = settings.allowedOrigins().contains(CorsPolicySettings.ANY_ORIGIN);

        // Header names are case-insensitive per RFC 9110; a client may send
        // "content-type" against a configured "Content-Type". Root-locale comparison
        // avoids the Turkish-I trap, where "I".toLowerCase() under tr-TR yields a
        // dotless i and a configured "If-Match" stops matching itself.
        var ci = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        ci.addAll(settings.allowedHeaders());
        this.headersCaseInsensitive = ci;

        // Sorted so the emitted header value is byte-stable across runs — a set's
        // iteration order is not a contract, and an unstable Access-Control-Allow-Methods
        // would churn caches and diffs for no reason.
        this.advertisedMethods = settings.allowedMethods().stream()
                .map(m -> m.toUpperCase(Locale.ROOT)).sorted().toList();

        // Match against the same normalised form that gets advertised. Comparing an
        // uppercased request method against the raw configured set lets the two disagree:
        // settings of ["get"] would advertise GET in Access-Control-Allow-Methods and then
        // deny it, because "GET" is not in ["get"]. Advertising a method the policy refuses
        // is worse than either behaviour on its own, and deny is indistinguishable from
        // "never configured", so the operator gets no signal.
        this.methodsNormalized = Set.copyOf(this.advertisedMethods);
        this.advertisedHeaders = settings.allowedHeaders().stream().sorted().toList();
    }

    @Override
    public CorsDecision evaluate(CorsRequest request) {
        Objects.requireNonNull(request, "request");

        // No Origin header: same-origin, and none of this cap's business. Denied here
        // means "no CORS headers apply", not "reject the request" — the consumer must
        // not turn an empty decision into a 403.
        if (!request.isCrossOrigin()) {
            return CorsDecision.denied();
        }
        if (!originAllowed(request.origin())) {
            return CorsDecision.denied();
        }
        if (!methodAllowed(request.method())) {
            return CorsDecision.denied();
        }
        if (request.preflight() && !headersAllowed(request.requestedHeaders())) {
            return CorsDecision.denied();
        }

        // Echo the concrete origin rather than "*" whenever credentials are on. The
        // wildcard is rejected outright by browsers on a credentialed response, and
        // echoing is the only correct alternative. Settings already refuse the
        // wildcard+credentials combination, so this is belt-and-braces.
        String allowOrigin = (anyOrigin && !settings.allowCredentials())
                ? CorsPolicySettings.ANY_ORIGIN
                : request.origin();

        return new CorsDecision(
                true,
                allowOrigin,
                request.preflight() ? advertisedMethods : List.of(),
                request.preflight() ? advertisedHeaders : List.of(),
                request.preflight() ? settings.maxAge() : null,
                settings.allowCredentials());
    }

    private boolean originAllowed(String origin) {
        return anyOrigin || settings.allowedOrigins().contains(origin);
    }

    private boolean methodAllowed(String method) {
        return methodsNormalized.contains(method.toUpperCase(Locale.ROOT));
    }

    private boolean headersAllowed(List<String> requested) {
        return headersCaseInsensitive.containsAll(requested);
    }
}
