# exeris-caps-cors-policy

Cross-origin request policy as an Exeris capability.

**Licence:** Apache-2.0 — one of the three `community` caps under [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md). Free for any use, production included, with no subscription.

---

## What it does

Provides one service, `CorsPolicy`: a pure decision function over an origin, a method, and (for a preflight) the headers a client declared.

```java
CorsPolicy policy = CorsPolicies.builder()
        .allowOrigin("https://app.example.com")
        .methods(List.of("GET", "POST"))
        .build();

CorsDecision d = policy.evaluate(CorsRequest.simple("https://app.example.com", "GET"));
if (d.allowed()) {
    d.headers().forEach(response::setHeader);
}
```

## Constructing a policy

`CorsPolicies.builder()` is the only way to obtain a `CorsPolicy` from outside this jar, and it is part of the cross-cap surface. A consumer never names a type from `internal`.

```java
CorsPolicy policy = CorsPolicies.builder()
        .allowOrigins(List.of("https://app.example.com", "https://admin.example.com"))
        .methods(List.of("GET", "POST", "DELETE"))   // replaces the default GET, HEAD, POST
        .headers(List.of("Content-Type", "Authorization"))   // replaces the default: none
        .allowCredentials(true)
        .maxAge(Duration.ofMinutes(10))
        .build();
```

The configuration is validated, and a configuration that cannot be honoured does not build:

- **Nothing is allowed by default.** With no origin, `build()` throws `IllegalStateException`; there is no wildcard default. The wildcard is served only when written: `allowAnyOrigin()`, or `"*"` passed to `allowOrigin` / `allowOrigins`.
- **Wildcard plus credentials is refused** at `build()`, and so is a wildcard next to explicit origins.
- **Origins are `scheme://host[:port]`.** A path, a trailing slash, user information, a `*` inside the origin and the literal `null` origin are refused, because each either never matches or grants more than it says. Matching is exact and case-sensitive.
- **Methods and header names must be HTTP tokens**, and `*` is not accepted for either. Defaults are `GET`, `HEAD`, `POST`, no request headers, no credentials, ten minutes of preflight cache.
- An invalid value throws `IllegalArgumentException` from its setter; an invalid combination throws `IllegalStateException` from `build()`. A built policy never throws to deny.

A built policy is immutable and thread-safe, and `evaluate` allocates nothing per request: every decision it can return is built once, at construction. `CorsPolicyAllocationTest` measures this on virtual threads, with a control that must fail.

## What it deliberately does not do

**It touches no transport.** It reads no socket and writes no header — a consuming Gateway cap applies the decision to whatever response type it owns.

That is a boundary decision, not minimalism. CORS is a response-header concern and the kernel exposes no response-header seam to bind to: ADR-061's `HttpRoutePolicy` governs route *authorization*, a different axis. Inventing a binding would put transport knowledge inside a cap, which is what the cap-tier Wall exists to prevent.

The payoff is concrete: this cap needs no kernel SPI, and its jar has **zero runtime dependencies** — the SDK annotations are `@Retention(SOURCE)` and taken at `provided` scope, so nothing survives to the classpath.

## Capability declaration

`CorsPolicyModule` carries the whole contract:

```java
@CapabilityModule
@Provides(service = CorsPolicy.class, version = "0.1.0")
public final class CorsPolicyModule { }
```

No `@Requires` — this cap depends on nothing, and a speculative empty edge would change the derived `initOrder` for every SKU including it. No `@CapabilityLifecycle` — it owns no resource, and four no-op hooks would cost a reflective load and imply a contract it does not have.

## Layout

| Package | Visibility |
|---|---|
| `eu.exeris.caps.corspolicy` | the `@CapabilityModule` declaration |
| `eu.exeris.caps.corspolicy.api` | **visible to other caps** — `CorsPolicy`, `CorsPolicies`, `CorsRequest`, `CorsDecision` |
| `eu.exeris.caps.corspolicy.internal` | **private** — a sibling cap reaching in fails the build |

The package root is load-bearing, not tidiness: `CapTierWall` derives the set of cap names a build owns from the segment after `eu.exeris.caps.`, and that set is what licenses the build to read its own `internal` packages.

## Building

Requires **JDK 25+** and Maven 3.9+. `exeris-sdk` 0.12.0 and `exeris-tooling` 0.10.0 resolve from Maven Central, so nothing is built from source first.

Then, **two passes on a fresh checkout**:

```bash
mvn compile -Dexeris.codegen.skip=true   # seed: the processor writes capability metadata
mvn verify                               # generate, validate the graph, scan the Wall
```

The first pass is not redundant. `exeris:generate` runs at `generate-sources`, but the processor only writes metadata during `compile`, so a first pass has nothing to read.

## What a successful build produces

`cap-manifest.json`, carrying a validated stamp and a SHA-256 content binding over the resolved cap set:

```json
{
  "schemaVersion": 2,
  "stamp": { "validated": true, "compositionVersion": "0.0.0", "contentBinding": "sha256:…" },
  "modules": [ { "qualifiedName": "eu.exeris.caps.corspolicy.CorsPolicyModule", … } ],
  "initOrder": [ "eu.exeris.caps.corspolicy.CorsPolicyModule" ]
}
```

`compositionVersion` reads `0.0.0` because the codegen plugin does not yet wire it (tracked as tooling **U3**); the SDK's stamp asserter tolerates it by design. The content binding is the half that carries weight — it makes the stamp non-transferable, attesting *this* composition rather than some composition.

## Reference

[Capability Author Guide](https://github.com/exeris-systems/exeris-docs/blob/main/cap-author-guide.md) · [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) (composition model) · [ADR-055](https://github.com/exeris-systems/exeris-tooling/blob/main/docs/adr/ADR-055-cap-tier-wall-guard.md) (Wall guard)
