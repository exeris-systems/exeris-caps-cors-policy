package eu.exeris.caps.corspolicy.internal;

import eu.exeris.caps.corspolicy.api.CorsDecision;
import eu.exeris.caps.corspolicy.api.CorsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultCorsPolicy")
class DefaultCorsPolicyTest {

    private static final String APP = "https://app.example.com";
    private static final String EVIL = "https://evil.example.com";

    private static DefaultCorsPolicy credentialed(String... origins) {
        return new DefaultCorsPolicy(CorsPolicySettings.forOrigins(List.of(origins)));
    }

    @Nested
    @DisplayName("origin matching")
    class OriginMatching {

        @Test
        @DisplayName("allows a configured origin and echoes it back")
        void allowsConfiguredOrigin() {
            CorsDecision d = credentialed(APP).evaluate(CorsRequest.simple(APP, "GET"));

            assertThat(d.allowed()).isTrue();
            assertThat(d.allowedOrigin()).isEqualTo(APP);
        }

        @Test
        @DisplayName("denies an origin that is not configured")
        void deniesUnknownOrigin() {
            assertThat(credentialed(APP).evaluate(CorsRequest.simple(EVIL, "GET")).allowed())
                    .isFalse();
        }

        @Test
        @DisplayName("treats an absent Origin as out of scope, not as a rejection")
        void absentOriginIsOutOfScope() {
            CorsDecision d = credentialed(APP).evaluate(CorsRequest.simple(null, "GET"));

            // Denied here means "no CORS headers apply" — a same-origin request. The
            // headers must be empty so a consumer cannot mistake this for a 403 signal.
            assertThat(d.allowed()).isFalse();
            assertThat(d.headers()).isEmpty();
        }

        @Test
        @DisplayName("wildcard settings allow any origin and emit * when uncredentialed")
        void wildcardEmitsStar() {
            CorsDecision d = new DefaultCorsPolicy(CorsPolicySettings.permissive())
                    .evaluate(CorsRequest.simple(EVIL, "GET"));

            assertThat(d.allowed()).isTrue();
            assertThat(d.allowedOrigin()).isEqualTo("*");
        }
    }

    @Nested
    @DisplayName("method and header matching")
    class MethodAndHeaders {

        @Test
        @DisplayName("denies a method outside the configured set")
        void deniesUnknownMethod() {
            assertThat(credentialed(APP).evaluate(CorsRequest.simple(APP, "TRACE")).allowed())
                    .isFalse();
        }

        @Test
        @DisplayName("matches the method case-insensitively")
        void methodIsCaseInsensitive() {
            assertThat(credentialed(APP).evaluate(CorsRequest.simple(APP, "get")).allowed())
                    .isTrue();
        }

        @Test
        @DisplayName("matches requested headers case-insensitively, per RFC 9110")
        void headersAreCaseInsensitive() {
            CorsDecision d = credentialed(APP).evaluate(
                    CorsRequest.preflight(APP, "POST", List.of("content-type", "AUTHORIZATION")));

            assertThat(d.allowed()).isTrue();
        }

        @Test
        @DisplayName("denies a preflight declaring an unconfigured header")
        void deniesUnknownHeader() {
            CorsDecision d = credentialed(APP).evaluate(
                    CorsRequest.preflight(APP, "POST", List.of("X-Smuggled")));

            assertThat(d.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("preflight versus simple")
    class PreflightShape {

        @Test
        @DisplayName("advertises methods, headers and max-age only on a preflight")
        void advertisesOnlyOnPreflight() {
            DefaultCorsPolicy policy = credentialed(APP);

            CorsDecision simple = policy.evaluate(CorsRequest.simple(APP, "GET"));
            assertThat(simple.allowedMethods()).isEmpty();
            assertThat(simple.allowedHeaders()).isEmpty();
            assertThat(simple.maxAge()).isNull();

            CorsDecision pre = policy.evaluate(CorsRequest.preflight(APP, "POST", List.of()));
            assertThat(pre.allowedMethods()).isNotEmpty();
            assertThat(pre.maxAge()).isEqualTo(Duration.ofMinutes(10));
        }

        @Test
        @DisplayName("advertises methods in a stable order across instances")
        void advertisedOrderIsStable() {
            // Set iteration order is not a contract; an unstable header value would
            // churn caches and diffs. Two independently-built policies must agree.
            var a = credentialed(APP).evaluate(CorsRequest.preflight(APP, "POST", List.of()));
            var b = credentialed(APP).evaluate(CorsRequest.preflight(APP, "POST", List.of()));

            assertThat(a.allowedMethods()).isSorted().isEqualTo(b.allowedMethods());
        }
    }

    @Nested
    @DisplayName("rendered headers")
    class Headers {

        @Test
        @DisplayName("always varies on Origin when allowing")
        void variesOnOrigin() {
            var headers = credentialed(APP).evaluate(CorsRequest.simple(APP, "GET")).headers();

            // Without this a shared cache can serve one origin's allow-header to another.
            assertThat(headers).containsEntry("Vary", "Origin");
        }

        @Test
        @DisplayName("emits nothing at all when denied")
        void deniedEmitsNothing() {
            // Never "Access-Control-Allow-Origin: null" — the literal string "null" is a
            // valid origin that sandboxed documents send, so emitting it grants access
            // rather than denying it.
            assertThat(credentialed(APP).evaluate(CorsRequest.simple(EVIL, "GET")).headers())
                    .isEmpty();
        }

        @Test
        @DisplayName("marks credentials when configured")
        void marksCredentials() {
            var headers = credentialed(APP).evaluate(CorsRequest.simple(APP, "GET")).headers();

            assertThat(headers).containsEntry("Access-Control-Allow-Credentials", "true");
        }
    }

    @Nested
    @DisplayName("settings validation")
    class Settings {

        @Test
        @DisplayName("refuses the wildcard-origin plus credentials combination")
        void refusesWildcardWithCredentials() {
            // Browsers reject a credentialed response carrying "*", so this configuration
            // cannot be honoured. Failing at construction beats silently dropping one
            // half of it at request time.
            assertThatThrownBy(() -> new CorsPolicySettings(
                    Set.of("*"), Set.of("GET"), Set.of(), null, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("wildcard");
        }
    }
}
