package eu.exeris.caps.corspolicy.internal;

import eu.exeris.caps.corspolicy.api.CorsDecision;
import eu.exeris.caps.corspolicy.api.CorsPolicy;
import eu.exeris.caps.corspolicy.api.CorsRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The stock {@link CorsPolicy}: exact-match origins with an optional wildcard,
 * case-insensitive header matching, and fail-closed defaults.
 *
 * <p>Immutable and thread-safe. Every decision this policy can return is built at
 * construction: the set of configured origins is finite, and for a configured origin the
 * allowed decision depends on nothing but whether the request is a preflight. {@code evaluate}
 * therefore matches and returns a stored instance, and allocates nothing — no decision, no
 * iterator, no case-converted copy of the request's strings.
 *
 * @since 0.1.0
 */
public final class DefaultCorsPolicy implements CorsPolicy {

    private final boolean anyOrigin;
    /** Root-locale-uppercased allowed methods, the same values that are advertised. */
    private final String[] methods;
    /** Configured request-header names; compared with an ASCII case fold. */
    private final String[] headers;
    private final CorsDecision wildcardSimple;
    private final CorsDecision wildcardPreflight;
    private final Map<String, CorsDecision> simpleByOrigin;
    private final Map<String, CorsDecision> preflightByOrigin;

    public DefaultCorsPolicy(CorsPolicySettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.anyOrigin = settings.allowedOrigins().contains(CorsPolicySettings.ANY_ORIGIN);
        if (anyOrigin && settings.allowCredentials()) {
            // Settings refuse this already; a policy built from a hand-made settings object
            // must not be able to echo an origin under a wildcard configuration either.
            throw new IllegalArgumentException(
                    "allowCredentials=true is incompatible with the wildcard origin \"*\"");
        }

        // Sorted so the emitted header value is byte-stable across runs — a set's
        // iteration order is not a contract, and an unstable Access-Control-Allow-Methods
        // would churn caches and diffs for no reason.
        List<String> advertisedMethods = settings.allowedMethods().stream()
                .map(m -> m.toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        List<String> advertisedHeaders = settings.allowedHeaders().stream().sorted().toList();

        // Match against the same normalised form that gets advertised. Comparing against the
        // raw configured set lets the two disagree: settings of ["get"] would advertise GET
        // in Access-Control-Allow-Methods and then deny it. Advertising a method the policy
        // refuses is worse than either behaviour on its own, and a deny is indistinguishable
        // from "never configured", so the operator gets no signal.
        this.methods = advertisedMethods.toArray(String[]::new);
        this.headers = advertisedHeaders.toArray(String[]::new);

        Duration maxAge = settings.maxAge();
        boolean credentials = settings.allowCredentials();
        if (anyOrigin) {
            // The wildcard is only ever emitted uncredentialed (refused above), so the value
            // is the literal "*" for every request.
            this.wildcardSimple = allowed(CorsPolicySettings.ANY_ORIGIN, List.of(), List.of(), null, false);
            this.wildcardPreflight = allowed(CorsPolicySettings.ANY_ORIGIN, advertisedMethods,
                    advertisedHeaders, maxAge, false);
            this.simpleByOrigin = Map.of();
            this.preflightByOrigin = Map.of();
        } else {
            this.wildcardSimple = null;
            this.wildcardPreflight = null;
            // Echo the concrete origin, never "*": the decision for origin O is the one whose
            // allowed-origin is O. Origin matching is the map lookup — exact and case-sensitive.
            var simple = new HashMap<String, CorsDecision>();
            var preflight = new HashMap<String, CorsDecision>();
            for (String origin : settings.allowedOrigins()) {
                simple.put(origin, allowed(origin, List.of(), List.of(), null, credentials));
                preflight.put(origin, allowed(origin, advertisedMethods, advertisedHeaders, maxAge, credentials));
            }
            this.simpleByOrigin = Map.copyOf(simple);
            this.preflightByOrigin = Map.copyOf(preflight);
        }
    }

    private static CorsDecision allowed(String origin, List<String> methods, List<String> headers,
                                        Duration maxAge, boolean credentials) {
        return new CorsDecision(true, origin, methods, headers, maxAge, credentials);
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
        boolean preflight = request.preflight();
        CorsDecision decision;
        if (anyOrigin) {
            decision = preflight ? wildcardPreflight : wildcardSimple;
        } else {
            decision = (preflight ? preflightByOrigin : simpleByOrigin).get(request.origin());
            if (decision == null) {
                return CorsDecision.denied();
            }
        }
        if (!methodAllowed(request.method())) {
            return CorsDecision.denied();
        }
        if (preflight && !headersAllowed(request.requestedHeaders())) {
            return CorsDecision.denied();
        }
        return decision;
    }

    private boolean methodAllowed(String method) {
        for (String allowed : methods) {
            if (equalsUpper(allowed, method)) {
                return true;
            }
        }
        return false;
    }

    private boolean headersAllowed(List<String> requested) {
        // Indexed, not iterated: an Iterator is an allocation per request.
        for (int i = 0, n = requested.size(); i < n; i++) {
            if (!headerAllowed(requested.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean headerAllowed(String name) {
        for (String allowed : headers) {
            if (equalsAsciiIgnoreCase(allowed, name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code candidate} is {@code upper} once its ASCII lower-case letters are raised.
     * Only ASCII is folded: a method is a token, and a non-ASCII character that a locale-aware
     * conversion would map onto an ASCII letter (a dotless i, a long s) must not make a
     * different method match. No intermediate string is built.
     */
    private static boolean equalsUpper(String upper, String candidate) {
        int n = upper.length();
        if (candidate.length() != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (upperAscii(candidate.charAt(i)) != upper.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Header names are case-insensitive per RFC 9110; a client may send "content-type"
     * against a configured "Content-Type". The fold is ASCII-only and locale-free, which avoids
     * the Turkish-I trap, where {@code "I".toLowerCase()} under tr-TR yields a dotless i and a
     * configured "If-Match" stops matching itself.
     */
    private static boolean equalsAsciiIgnoreCase(String a, String b) {
        int n = a.length();
        if (b.length() != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (upperAscii(a.charAt(i)) != upperAscii(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static char upperAscii(char c) {
        return (c >= 'a' && c <= 'z') ? (char) (c - ('a' - 'A')) : c;
    }
}
