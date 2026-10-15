package eu.exeris.caps.corspolicy.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CorsPolicies")
class CorsPoliciesTest {

    private static final String APP = "https://app.example.com";
    private static final String EVIL = "https://evil.example.com";

    private static CorsPolicies.Builder forApp() {
        return CorsPolicies.builder().allowOrigin(APP);
    }

    @Nested
    @DisplayName("nothing is allowed by default")
    class FailClosedDefaults {

        @Test
        @DisplayName("a builder with no origin does not build, and there is no implicit wildcard")
        void noOriginDoesNotBuild() {
            assertThatThrownBy(() -> CorsPolicies.builder().build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no origin");
        }

        @Test
        @DisplayName("an unconfigured origin is denied")
        void unconfiguredOriginDenied() {
            CorsDecision d = forApp().build().evaluate(CorsRequest.simple(EVIL, "GET"));

            assertThat(d.allowed()).isFalse();
            assertThat(d.headers()).isEmpty();
        }

        @Test
        @DisplayName("defaults allow GET, HEAD and POST only, with no request headers and no credentials")
        void narrowDefaults() {
            CorsPolicy policy = forApp().build();

            assertThat(policy.evaluate(CorsRequest.simple(APP, "GET")).allowed()).isTrue();
            assertThat(policy.evaluate(CorsRequest.simple(APP, "DELETE")).allowed()).isFalse();
            assertThat(policy.evaluate(CorsRequest.preflight(APP, "POST", List.of("Content-Type"))).allowed())
                    .isFalse();

            CorsDecision pre = policy.evaluate(CorsRequest.preflight(APP, "POST", List.of()));
            assertThat(pre.allowedMethods()).containsExactly("GET", "HEAD", "POST");
            assertThat(pre.allowedHeaders()).isEmpty();
            assertThat(pre.credentials()).isFalse();
            assertThat(pre.maxAge()).isEqualTo(Duration.ofMinutes(10));
        }
    }

    @Nested
    @DisplayName("wildcard")
    class Wildcard {

        @Test
        @DisplayName("is served only when written, and then as * without credentials")
        void servedWhenWritten() {
            for (CorsPolicy policy : List.of(
                    CorsPolicies.builder().allowAnyOrigin().build(),
                    CorsPolicies.builder().allowOrigin("*").build(),
                    CorsPolicies.builder().allowOrigins(List.of("*")).build())) {
                CorsDecision d = policy.evaluate(CorsRequest.simple(EVIL, "GET"));

                assertThat(d.allowed()).isTrue();
                assertThat(d.allowedOrigin()).isEqualTo("*");
                assertThat(d.credentials()).isFalse();
                assertThat(d.headers()).containsEntry("Vary", "Origin");
            }
        }

        @Test
        @DisplayName("is refused together with credentials, at build")
        void refusedWithCredentials() {
            assertThatThrownBy(() -> CorsPolicies.builder().allowAnyOrigin().allowCredentials(true).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("wildcard");
            assertThatThrownBy(() -> CorsPolicies.builder().allowCredentials(true).allowOrigin("*").build())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("is refused together with explicit origins, in either order")
        void refusedWithExplicitOrigins() {
            assertThatThrownBy(() -> forApp().allowAnyOrigin().build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be combined");
            assertThatThrownBy(() -> CorsPolicies.builder().allowAnyOrigin().allowOrigin(APP).build())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("origin validation")
    class Origins {

        @Test
        @DisplayName("accepts scheme://host[:port], including an IPv6 host and a port")
        void acceptsOrigins() {
            assertThatCode(() -> CorsPolicies.builder()
                    .allowOrigins(List.of("https://a.example", "http://localhost:3000", "http://[::1]:8080"))
                    .build()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("refuses what would never match or would grant more than it says")
        void refusesMalformed() {
            for (String bad : List.of(
                    "", " ", APP + "/", APP + "/path", APP + "?q=1", APP + "#f", "app.example.com",
                    "//app.example.com", "https://", "https://*.example.com", "https://a b.example",
                    "https://user@app.example.com", "null", "**")) {
                assertThatThrownBy(() -> CorsPolicies.builder().allowOrigin(bad), "origin [" + bad + "]")
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("adds nothing from a collection that contains an invalid origin")
        void collectionIsAllOrNothing() {
            CorsPolicies.Builder builder = CorsPolicies.builder();

            assertThatThrownBy(() -> builder.allowOrigins(List.of(APP, "not-an-origin")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("matches exactly and case-sensitively: no folding, no trailing slash, no suffix")
        void matchesExactly() {
            CorsPolicy policy = forApp().build();

            assertThat(policy.evaluate(CorsRequest.simple(APP, "GET")).allowed()).isTrue();
            assertThat(policy.evaluate(CorsRequest.simple("https://APP.example.com", "GET")).allowed()).isFalse();
            assertThat(policy.evaluate(CorsRequest.simple(APP + "/", "GET")).allowed()).isFalse();
            assertThat(policy.evaluate(CorsRequest.simple("https://evil.app.example.com", "GET")).allowed())
                    .isFalse();
            assertThat(policy.evaluate(CorsRequest.simple(APP + ".evil.example", "GET")).allowed()).isFalse();
            assertThat(policy.evaluate(CorsRequest.simple("null", "GET")).allowed()).isFalse();
        }

        @Test
        @DisplayName("echoes the concrete origin, per origin, when several are configured")
        void echoesConcreteOrigin() {
            CorsPolicy policy = forApp().allowOrigin("https://admin.example.com").build();

            assertThat(policy.evaluate(CorsRequest.simple(APP, "GET")).allowedOrigin()).isEqualTo(APP);
            assertThat(policy.evaluate(CorsRequest.simple("https://admin.example.com", "GET")).allowedOrigin())
                    .isEqualTo("https://admin.example.com");
        }
    }

    @Nested
    @DisplayName("methods, headers, max-age and credentials")
    class OtherSettings {

        @Test
        @DisplayName("methods(..) replaces the defaults and is normalised, sorted and de-duplicated")
        void methodsReplaceDefaults() {
            CorsPolicy policy = forApp().methods(List.of("put", "GET", "get", "Delete")).build();

            CorsDecision pre = policy.evaluate(CorsRequest.preflight(APP, "DELETE", List.of()));
            assertThat(pre.allowed()).isTrue();
            assertThat(pre.allowedMethods()).containsExactly("DELETE", "GET", "PUT");
            assertThat(policy.evaluate(CorsRequest.simple(APP, "POST")).allowed()).isFalse();
        }

        @Test
        @DisplayName("headers(..) are matched case-insensitively and advertised once")
        void headersMatchedCaseInsensitively() {
            CorsPolicy policy = forApp().headers(List.of("Content-Type", "content-type", "Authorization")).build();

            CorsDecision pre = policy.evaluate(
                    CorsRequest.preflight(APP, "GET", List.of("CONTENT-TYPE", "authorization")));
            assertThat(pre.allowed()).isTrue();
            assertThat(pre.allowedHeaders()).containsExactly("Authorization", "Content-Type");
            assertThat(policy.evaluate(CorsRequest.preflight(APP, "GET", List.of("X-Other"))).allowed()).isFalse();
        }

        @Test
        @DisplayName("header matching holds under a Turkish default locale")
        void turkishLocale() {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                CorsPolicy policy = forApp().headers(List.of("If-Match")).methods(List.of("get")).build();

                assertThat(policy.evaluate(CorsRequest.preflight(APP, "GET", List.of("IF-MATCH", "if-match")))
                        .allowed()).isTrue();
            } finally {
                Locale.setDefault(previous);
            }
        }

        @Test
        @DisplayName("refuses a method or header that is not a token, and refuses * for either")
        void refusesNonTokens() {
            for (String bad : List.of("", "GE T", "GET,POST", "*", "X-Á", "a:b")) {
                assertThatThrownBy(() -> CorsPolicies.builder().methods(List.of(bad)), "method [" + bad + "]")
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> CorsPolicies.builder().headers(List.of(bad)), "header [" + bad + "]")
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("refuses an empty method set at build, since every request would be denied")
        void refusesNoMethods() {
            assertThatThrownBy(() -> forApp().methods(List.of()).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no method");
        }

        @Test
        @DisplayName("maxAge: advertised in seconds, null advertises nothing, negative is refused")
        void maxAge() {
            CorsRequest preflight = CorsRequest.preflight(APP, "GET", List.of());

            assertThat(forApp().maxAge(Duration.ofSeconds(90)).build().evaluate(preflight).headers())
                    .containsEntry("Access-Control-Max-Age", "90");
            assertThat(forApp().maxAge(null).build().evaluate(preflight).headers())
                    .doesNotContainKey("Access-Control-Max-Age");
            assertThatThrownBy(() -> forApp().maxAge(Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("credentials with explicit origins echo the origin and mark the response")
        void credentialsWithExplicitOrigins() {
            CorsDecision d = forApp().allowCredentials(true).build().evaluate(CorsRequest.simple(APP, "GET"));

            assertThat(d.allowedOrigin()).isEqualTo(APP);
            assertThat(d.headers())
                    .containsEntry("Access-Control-Allow-Credentials", "true")
                    .containsEntry("Vary", "Origin");
        }
    }

    @Nested
    @DisplayName("the built policy")
    class BuiltPolicy {

        @Test
        @DisplayName("is independent of the builder it came from")
        void independentOfBuilder() {
            CorsPolicies.Builder builder = forApp();
            CorsPolicy first = builder.build();
            builder.allowOrigin(EVIL).methods(List.of("DELETE"));

            assertThat(first.evaluate(CorsRequest.simple(EVIL, "GET")).allowed()).isFalse();
            assertThat(first.evaluate(CorsRequest.simple(APP, "GET")).allowed()).isTrue();
            assertThat(builder.build().evaluate(CorsRequest.simple(EVIL, "DELETE")).allowed()).isTrue();
        }

        @Test
        @DisplayName("returns the same decision for repeated equal requests, and never throws to deny")
        void stableDecisions() {
            CorsPolicy policy = forApp().build();

            assertThat(policy.evaluate(CorsRequest.simple(APP, "GET")))
                    .isSameAs(policy.evaluate(CorsRequest.simple(APP, "GET")));
            assertThatCode(() -> policy.evaluate(CorsRequest.simple(EVIL, "TRACE"))).doesNotThrowAnyException();
        }
    }
}
