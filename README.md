# exeris-caps-cors-policy

Cross-origin request policy as an Exeris capability.

**Licence:** Apache-2.0 — one of the three `community` caps under [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md). Free for any use, production included, with no subscription.

---

## What it does

Provides one service, `CorsPolicy`: a pure decision function over an origin, a method, and (for a preflight) the headers a client declared.

```java
CorsPolicy policy = /* provided by the composition */;

CorsDecision d = policy.evaluate(CorsRequest.simple("https://app.example.com", "GET"));
if (d.allowed()) {
    d.headers().forEach(response::setHeader);
}
```

## What it deliberately does not do

**It touches no transport.** It reads no socket and writes no header — a consuming Gateway cap applies the decision to whatever response type it owns.

That is a boundary decision, not minimalism. CORS is a response-header concern and the kernel exposes no response-header seam to bind to: ADR-061's `HttpRoutePolicy` governs route *authorization*, a different axis. Inventing a binding would put transport knowledge inside a cap, which is what the cap-tier Wall exists to prevent.

The payoff is concrete: this cap needs no kernel SPI, and its jar has **zero runtime dependencies** — the SDK annotations are `@Retention(SOURCE)` and taken at `provided` scope, so nothing survives to the classpath.

## Capability declaration

`CorsPolicyModule` carries the whole contract:

```java
@CapabilityModule
@Provides(service = CorsPolicy.class, version = "1.0.0")
public final class CorsPolicyModule { }
```

No `@Requires` — this cap depends on nothing, and a speculative empty edge would change the derived `initOrder` for every SKU including it. No `@CapabilityLifecycle` — it owns no resource, and four no-op hooks would cost a reflective load and imply a contract it does not have.

## Layout

| Package | Visibility |
|---|---|
| `eu.exeris.caps.corspolicy` | the `@CapabilityModule` declaration |
| `eu.exeris.caps.corspolicy.api` | **visible to other caps** — `CorsPolicy`, `CorsRequest`, `CorsDecision` |
| `eu.exeris.caps.corspolicy.internal` | **private** — a sibling cap reaching in fails the build |

The package root is load-bearing, not tidiness: `CapTierWall` derives the set of cap names a build owns from the segment after `eu.exeris.caps.`, and that set is what licenses the build to read its own `internal` packages.

## Building

Requires **JDK 25+** and Maven 3.9+. `eu.exeris` artifacts are not on Maven Central yet, so both upstreams are built from source first:

```bash
git clone -b v0.10.0 https://github.com/exeris-systems/exeris-sdk.git
(cd exeris-sdk && mvn -DskipTests -Djapicmp.skip=true install)

git clone https://github.com/exeris-systems/exeris-tooling.git
(cd exeris-tooling && mvn -DskipTests install -pl exeris-processor,exeris-codegen-maven-plugin -am)
```

Then, **two passes on a fresh checkout**:

```bash
mvn compile -Dexeris.codegen.skip=true   # seed: the processor writes capability metadata
mvn verify                               # generate, validate the graph, scan the Wall
```

The first pass is not redundant. `exeris:generate` runs at `generate-sources`, but the processor only writes metadata during `compile`, so a first pass has nothing to read.

> Scoping the tooling build to `exeris-processor,exeris-codegen-maven-plugin -am` is what keeps this credential-free. The full reactor includes `exeris-e2e-tests`, which declares the kernel at test scope; those artifacts live on GitHub Packages and would demand a token. Nothing in the processor or plugin chain references the kernel. Note `-DskipTests` would not be enough — Maven collects a module's dependency graph whether or not its tests compile, so the module has to be out of the reactor, not merely quiet.

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
