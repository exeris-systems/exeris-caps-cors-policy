package eu.exeris.caps.corspolicy.api;

import eu.exeris.caps.corspolicy.internal.CorsPolicySettings;
import eu.exeris.caps.corspolicy.internal.DefaultCorsPolicy;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The public way to obtain a {@link CorsPolicy}.
 *
 * <p>A consuming cap or SKU builds its policy here and never names a type from
 * {@code ..corspolicy.internal}, which the cap-tier Wall (ADR-055) closes to it. The
 * configuration record behind the builder stays private to this cap: the builder is the
 * surface, the record is an implementation detail.
 *
 * {@snippet :
 * CorsPolicy policy = CorsPolicies.builder()
 *         .allowOrigin("https://app.example.com")
 *         .methods(List.of("GET", "POST", "DELETE"))
 *         .headers(List.of("Content-Type", "Authorization"))
 *         .allowCredentials(true)
 *         .maxAge(Duration.ofMinutes(10))
 *         .build();
 * }
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><b>Nothing is allowed by default.</b> A builder with no origin does not build. There
 *       is no wildcard default and no factory that implies one: the wildcard {@code "*"} is
 *       served only when {@link Builder#allowAnyOrigin()} is called, or {@code "*"} is passed
 *       to {@link Builder#allowOrigin(String)} or {@link Builder#allowOrigins(Collection)},
 *       by whoever wrote the configuration.</li>
 *   <li><b>Wildcard and credentials are refused together</b>, at {@link Builder#build()}.
 *       Browsers reject {@code Access-Control-Allow-Origin: *} on a credentialed response, so
 *       a configuration asking for both cannot be honoured and fails early rather than
 *       dropping one half at request time. A wildcard also cannot be combined with explicit
 *       origins: the result would be ambiguous.</li>
 *   <li><b>Origins match exactly and case-sensitively.</b> An origin is
 *       {@code scheme://host[:port]}. A trailing slash, a path, a query, user information, a
 *       {@code *} inside an origin (such as {@code https://*.example.com}) and the literal
 *       {@code null} origin are refused when configured, because each either never matches or
 *       grants more than it appears to.</li>
 *   <li><b>Methods and header names must be HTTP tokens</b> (RFC 9110). {@code "*"} is not
 *       accepted for either; a policy that listed it would deny everything while looking
 *       permissive.</li>
 *   <li><b>Defaults are narrow.</b> Methods default to {@code GET}, {@code HEAD} and
 *       {@code POST}; request headers default to none; credentials are off; the preflight
 *       lifetime is ten minutes.</li>
 *   <li><b>Configuration errors are exceptions, decisions are not.</b> A setter throws
 *       {@link IllegalArgumentException} for an invalid value and {@link Builder#build()}
 *       throws {@link IllegalStateException} for an invalid combination. A built policy never
 *       throws to signal a denial.</li>
 *   <li><b>A built policy is immutable and thread-safe</b>, and independent of the builder:
 *       later builder calls do not affect it. Its {@link CorsPolicy#evaluate} allocates
 *       nothing per request.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class CorsPolicies {

    private static final String WILDCARD = "*";

    /** {@code scheme://host[:port]}: no path, query, fragment, user information, whitespace or {@code *}. */
    private static final Pattern ORIGIN =
            Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://[^/?#@*\\s]+");

    /** An HTTP token (RFC 9110 section 5.6.2) other than the lone {@code *}. */
    private static final Pattern TOKEN = Pattern.compile("[!#$%&'+.^_`|~0-9A-Za-z-]+");

    private CorsPolicies() {
    }

    /**
     * Starts a configuration with no origin, methods {@code GET}, {@code HEAD} and
     * {@code POST}, no request headers, credentials off and a ten-minute preflight lifetime.
     *
     * @return a new builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the configuration of one {@link CorsPolicy}. Not thread-safe; use one builder
     * per thread. {@link #build()} may be called more than once.
     */
    public static final class Builder {

        private final Set<String> origins = new LinkedHashSet<>();
        private Set<String> methods = new LinkedHashSet<>(Set.of("GET", "HEAD", "POST"));
        private Set<String> headers = new LinkedHashSet<>();
        private Duration maxAge = Duration.ofMinutes(10);
        private boolean allowCredentials;

        private Builder() {
        }

        /**
         * Allows one origin, or every origin when {@code origin} is {@code "*"}.
         *
         * @param origin {@code scheme://host[:port]}, or {@code "*"}; never {@code null}
         * @return this builder
         * @throws IllegalArgumentException if the value is not a valid origin, see
         *                                  {@link CorsPolicies}
         */
        public Builder allowOrigin(String origin) {
            Objects.requireNonNull(origin, "origin");
            if (!origin.equals(WILDCARD)) {
                requireOrigin(origin);
            }
            origins.add(origin);
            return this;
        }

        /**
         * Allows several origins; each is validated as by {@link #allowOrigin(String)}. Nothing
         * is added if any of them is invalid.
         *
         * @param origins the origins; never {@code null}, no {@code null} element
         * @return this builder
         * @throws IllegalArgumentException if an element is not a valid origin
         */
        public Builder allowOrigins(Collection<String> origins) {
            Objects.requireNonNull(origins, "origins");
            for (String origin : origins) {
                Objects.requireNonNull(origin, "origin");
                if (!origin.equals(WILDCARD)) {
                    requireOrigin(origin);
                }
            }
            this.origins.addAll(origins);
            return this;
        }

        /**
         * Allows every origin. The wildcard is served as {@code Access-Control-Allow-Origin: *},
         * and only without credentials; this is the one place a wildcard is written down.
         *
         * @return this builder
         */
        public Builder allowAnyOrigin() {
            origins.add(WILDCARD);
            return this;
        }

        /**
         * Replaces the allowed methods. Matching and advertising are case-insensitive and use
         * the same upper-cased set.
         *
         * @param methods HTTP method tokens; never {@code null}, no {@code null} element
         * @return this builder
         * @throws IllegalArgumentException if an element is not an HTTP token
         */
        public Builder methods(Collection<String> methods) {
            this.methods = tokens(methods, "method", false);
            return this;
        }

        /**
         * Replaces the allowed request headers, matched case-insensitively. The default is none.
         *
         * @param headers header names; never {@code null}, no {@code null} element
         * @return this builder
         * @throws IllegalArgumentException if an element is not an HTTP token
         */
        public Builder headers(Collection<String> headers) {
            this.headers = tokens(headers, "header", true);
            return this;
        }

        /**
         * Sets how long a browser may cache a preflight result.
         *
         * @param maxAge a non-negative duration, or {@code null} to advertise nothing
         * @return this builder
         * @throws IllegalArgumentException if {@code maxAge} is negative
         */
        public Builder maxAge(Duration maxAge) {
            if (maxAge != null && maxAge.isNegative()) {
                throw new IllegalArgumentException("maxAge must not be negative: " + maxAge);
            }
            this.maxAge = maxAge;
            return this;
        }

        /**
         * Sets whether credentialed requests are permitted. Off by default. Cannot be combined
         * with the wildcard origin.
         *
         * @param allowCredentials whether to emit {@code Access-Control-Allow-Credentials: true}
         * @return this builder
         */
        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        /**
         * Validates the configuration and builds the policy.
         *
         * @return an immutable, thread-safe policy; never {@code null}
         * @throws IllegalStateException if no origin was configured, if the wildcard is
         *                               combined with explicit origins, if the wildcard is
         *                               combined with credentials, or if no method is allowed
         */
        public CorsPolicy build() {
            if (origins.isEmpty()) {
                throw new IllegalStateException(
                        "no origin is allowed: call allowOrigin(..) with an explicit origin, "
                                + "or allowAnyOrigin() if a wildcard is really meant");
            }
            boolean wildcard = origins.contains(WILDCARD);
            if (wildcard && origins.size() > 1) {
                throw new IllegalStateException(
                        "the wildcard origin \"*\" cannot be combined with explicit origins: "
                                + origins);
            }
            if (wildcard && allowCredentials) {
                throw new IllegalStateException(
                        "allowCredentials(true) is incompatible with the wildcard origin \"*\" — "
                                + "browsers reject a credentialed response carrying it. "
                                + "List the origins explicitly, or turn credentials off.");
            }
            if (methods.isEmpty()) {
                throw new IllegalStateException("no method is allowed: every request would be denied");
            }
            return new DefaultCorsPolicy(new CorsPolicySettings(
                    origins, methods, headers, maxAge, allowCredentials));
        }

        private static void requireOrigin(String origin) {
            if (origin.equals("null")) {
                throw new IllegalArgumentException(
                        "the origin \"null\" is sent by sandboxed documents; allowing it grants them access");
            }
            if (!ORIGIN.matcher(origin).matches()) {
                throw new IllegalArgumentException(
                        "not an origin (expected scheme://host[:port], exact, no path or trailing slash): \""
                                + origin + "\"");
            }
        }

        private static Set<String> tokens(Collection<String> values, String what, boolean keepCase) {
            Objects.requireNonNull(values, what + "s");
            // Methods are kept upper-cased and headers de-duplicated ignoring case, so two
            // spellings of one name are not advertised twice.
            Set<String> out = keepCase
                    ? new TreeSet<>(String.CASE_INSENSITIVE_ORDER)
                    : new LinkedHashSet<>();
            for (String value : values) {
                Objects.requireNonNull(value, what);
                if (!TOKEN.matcher(value).matches()) {
                    throw new IllegalArgumentException(
                            "not an HTTP " + what + " token: \"" + value + "\"");
                }
                out.add(keepCase ? value : value.toUpperCase(Locale.ROOT));
            }
            return out;
        }
    }
}
