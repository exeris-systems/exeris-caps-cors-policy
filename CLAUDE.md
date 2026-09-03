# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

`exeris-caps-cors-policy` is a single-module **capability** in the Exeris cap tier. It publishes one
service, `CorsPolicy`: a pure decision function over an origin, a method, and — on a preflight — the
headers a client declared. Apache-2.0, one of the three `community` caps under ADR-023.

Two properties make it unlike its sibling repos, and both are load-bearing:

1. **It is the first cap.** 53 more are specified and unwritten (`exeris-docs/cap-license-registry.md`).
   Whatever shape this repo settles on is the shape a cap author copies, so a convention worth having
   belongs here before it belongs in an archetype.
2. **The jar carries zero runtime dependencies.** Every Exeris annotation is `@Retention(SOURCE)`, so
   `exeris-sdk-annotations` stays at `provided` scope and `exeris-processor` stays inside
   `annotationProcessorPaths` — never on the compile classpath. Nothing survives to a consumer.

Its registry status is `scaffolded`, not `implemented`. **Read `ROADMAP.md` before proposing a design
change**: the obvious gaps (a boolean where a three-state outcome belongs, no public path to an
instance) are already diagnosed and sequenced there, with the reasoning. Re-deriving them costs a
session; contradicting them silently costs more.

## Build & test

```bash
mvn compile -Dexeris.codegen.skip=true   # pass 1 — seed: the processor writes capability metadata
mvn verify                               # pass 2 — generate, validate the graph, scan the Wall
```

**Both passes are required after every `clean`, and the first is not redundant.** `exeris:generate`
runs at `generate-sources`, but `exeris-processor` only writes `target/classes/exeris-metadata/`
during `compile` — so on a cleaned tree the first pass has nothing to read. With a warm `target/`,
`mvn verify` alone is enough, and `mvn -o verify` works offline once the upstreams are installed.

`eu.exeris` artefacts are not on Maven Central, so both upstreams are built from source once:

```bash
git clone -b v0.10.0 https://github.com/exeris-systems/exeris-sdk.git
(cd exeris-sdk && mvn -DskipTests -Djapicmp.skip=true install)   # japicmp.skip is required from v0.10.0

git clone -b v0.7.0 https://github.com/exeris-systems/exeris-tooling.git
(cd exeris-tooling && mvn -DskipTests install -pl exeris-tooling-bom,exeris-processor,exeris-codegen-maven-plugin -am)
```

That tooling module list is an **allowlist, not a convenience**. The full reactor drags in
`exeris-e2e-tests`, which declares the kernel at test scope; those artefacts live on GitHub Packages
behind a credential this repo deliberately does not hold. `-DskipTests` does not help — Maven
collects a module's dependency graph whether or not its tests compile, so the module must leave the
reactor. Naming `exeris-tooling-bom` explicitly is also required: `-am` traverses parent and
dependency edges but not BOM imports, and without it the plugin installs with a POM that cannot be
read back on a cold `~/.m2`.

## Hard constraints

Violating any of these is a blocker, not a nit. Five of them are enforced by the build; the sixth is
enforced by review.

- **The cap-tier Wall (ADR-055), build-enforced.** `..corspolicy.api` is the entire cross-cap
  surface; `..corspolicy.internal` is private, and a sibling cap reaching in fails the build.
  `exeris:verify-capabilities` scans the compiled bytecode at `process-classes` and prints
  `own cap(s): [corspolicy]` — that name is derived from **the segment after `eu.exeris.caps.`**, and
  it is what licenses this build to read its own `internal` packages. Renaming the package root or
  adding a path segment silently revokes that. Never let an `internal` type appear in an `api`
  signature.
- **Zero runtime dependencies, build-enforced by scope.** No new `compile`-scope dependency, no
  kernel / Spring / servlet / transport type, and no attempt to read an Exeris annotation at runtime
  — reflection over a `SOURCE`-retention annotation can never work.
- **The manifest is committed.** `src/main/generated/java/cap-manifest.json` is tracked on purpose
  (the L1 detachment story; see `.gitignore`) and `exeris:generate` writes it **into `src/`, not
  `target/`**. Any diff touching `CorsPolicyModule` must carry the regenerated manifest, with
  `stamp.validated: true`, a `sha256:<64 hex>` `contentBinding`, and an `initOrder` matching the
  declared graph. Regeneration is byte-stable, so a clean `git status` after `mvn verify` is the
  check — a dirty one means the declaration and the manifest disagree.
- **Declaration discipline.** `CorsPolicyModule` carries the whole contract and is never
  instantiated. A speculative or empty `@Requires` is a false edge in the composition DAG and changes
  the derived `initOrder` for every SKU including this cap. A `@CapabilityLifecycle` here would be
  four no-op hooks the boot conductor still loads reflectively, for a cap that acquires no resource.
- **JDK 25 LTS, no preview.** `maven.compiler.release` stays 25 (kernel ADR-066, SDK ADR-069) and no
  `--enable-preview` appears anywhere: a preview-stamped class re-pins this jar to one exact JDK
  major, which is the thing the preview-clean baseline exists to avoid. CI builds 25 and 26; both
  rows must stay green.
- **Test contract (ADR-058).** JUnit 5 + AssertJ only, no mocking framework — doubles are written or
  emitted, never mocked. New behaviour in `DefaultCorsPolicy` belongs in the matching `@Nested` group
  of `DefaultCorsPolicyTest`, not a parallel class. 15 tests across 5 groups today.

## This is a security decision function

The traps below are spec-level, already handled, and easy to undo by accident. Anything that *widens*
what is allowed needs an explicit justification.

- **Fail closed.** An unmatched origin, method or requested header denies, and a denial is a normal
  return value — never an exception.
- **Wildcard + credentials is rejected at construction**, not at request time. Browsers refuse
  `Access-Control-Allow-Origin: *` on a credentialed response, so a config asking for both cannot be
  honoured and failing early beats silently dropping one half.
- **`Vary: Origin` accompanies every origin-dependent response**, or a shared cache serves one
  origin's allow-header to another.
- **Header matching is case-insensitive under `Locale.ROOT`** — the Turkish-I trap: `"I".toLowerCase()`
  under `tr-TR` yields a dotless i, and a configured `If-Match` stops matching itself. Origin matching
  is exact and case-sensitive.
- **A denied decision emits no headers at all.** Never `Access-Control-Allow-Origin: null` — the
  literal string `"null"` is a valid origin that sandboxed documents send, so emitting it grants
  access rather than denying it.
- **Advertised method and header order is sorted**, so the emitted value is byte-stable and does not
  churn caches. Methods are matched against the same normalised (uppercased) set that gets
  advertised, or the policy can advertise a method it then refuses.
- **Preflight-only fields must not leak onto simple requests** — methods, headers and `max-age` are
  emitted only when `preflight` is true.

Report vulnerabilities through `SECURITY.md`, never a public issue.

## Reference-first

The parent `exeris-systems/CLAUDE.md` reference-first rule applies. For this repo specifically:

- **Composition model, `@Provides` / `@Requires` semantics** → `docs/adr/ADR-024.link.md`, then the
  authoritative copy in `exeris-docs`.
- **What the Wall actually forbids** → `docs/adr/ADR-055.link.md`, then `CapTierWall` in
  `exeris-tooling/exeris-codegen-java/`. Read the implementation before arguing about the rule — the
  guard and its ADR have drifted once already, and the guard is what runs.
- **Manifest shape and the stamp** → `docs/adr/ADR-053.link.md` and
  `exeris-sdk/exeris-sdk-composition-spec/`.
- **How a cap is expected to be authored** → `exeris-docs/cap-author-guide.md`.
- **CI / workflow shapes** → `exeris-tooling/.github/workflows/`.

Absence of a pattern here is usually a porting gap, not a deliberate divergence — cite the reference
`path:line` when proposing one.

## Language

Conversation with the user is **Polish**. Code, identifiers, comments, commit messages, PR titles and
bodies, and doc files are **English**.
