---
title: "exeris-caps-cors-policy: agent operating contract"
type: reference
visibility: public
owning-repo: exeris-caps-cors-policy
status: active
last-verified: 2026-10-13
---

# exeris-caps-cors-policy

Operating contract for AI agents working in this repository.

## 1. Mission and scope

`exeris-caps-cors-policy` is a single-module Exeris capability. It provides one service, `CorsPolicy`:
a pure decision function over an origin, a method and, on a preflight, the headers a client declared.
Licence: Apache-2.0, one of the three `community` caps under ADR-023.

- **It is the first capability.** The shape it settles on is the shape a capability author copies,
  so a convention worth having belongs here before it belongs in an archetype.
- **The jar has zero runtime dependencies.** Every Exeris annotation is `@Retention(SOURCE)`:
  `exeris-sdk-annotations` stays at `provided` scope and `exeris-processor` stays in
  `annotationProcessorPaths`, never on the compile classpath.
- **Its registry status is `scaffolded`.** Read [`ROADMAP.md`](ROADMAP.md) before proposing a design
  change: the known gaps are diagnosed and sequenced there, with the reasoning.

## 2. Operating contract and boundaries

- **The cap-tier Wall (ADR-055), build-enforced.** `eu.exeris.caps.corspolicy.api` is the whole
  cross-cap surface; `..corspolicy.internal` is private. The guard derives the capability's own name
  (`corspolicy`) from the segment after `eu.exeris.caps.`, so renaming the package root or adding a
  segment revokes the build's right to read its own `internal`. Never put an `internal` type in an
  `api` signature.
- **No runtime dependency.** No new `compile`-scope dependency, no kernel, Spring, servlet or transport
  type, and no reading an Exeris annotation at runtime: reflection over `SOURCE` retention never works.
- **The committed manifest.** `src/main/generated/java/cap-manifest.json` is tracked, and
  `exeris:generate` writes it into `src/`. A diff touching `CorsPolicyModule` carries the regenerated
  manifest: `stamp.validated: true`, a `sha256:<64 hex>` `contentBinding`, an `initOrder` matching the
  declared graph. Regeneration is byte-stable, so a dirty `git status` after `mvn verify` means the
  declaration and the manifest disagree.
- **Declarations.** `CorsPolicyModule` carries the whole contract and is never instantiated. A
  speculative `@Requires` is a false edge that changes every SKU's `initOrder`; a
  `@CapabilityLifecycle` would be four no-op hooks for a capability that acquires no resource.
  `@Provides(version = …)` is a semver contract: a behavioural change to `CorsPolicy` bumps it.
- **JDK 25, no preview.** `maven.compiler.release` stays 25 and no `--enable-preview` appears
  anywhere (kernel ADR-066, SDK ADR-069): a preview-stamped class re-pins the jar to one JDK major.
  CI builds 25 and 26; both rows stay green.
- **Tests (ADR-058).** JUnit 5 and AssertJ only, no mocking framework. New behaviour in
  `DefaultCorsPolicy` goes in the matching `@Nested` group of `DefaultCorsPolicyTest`.
- **A credential-free build.** The upstream tooling reactor is an allowlist that keeps the kernel, and
  so a GitHub Packages token, out of the build: the full reactor pulls in `exeris-e2e-tests`, which
  declares the kernel at test scope, and `-DskipTests` does not drop it. `exeris-tooling-bom` is named
  because `-am` does not follow BOM imports.
- **Licence.** Apache-2.0 is an ADR-023 classification: `LICENSE`, the POM `<licenses>` block and
  the README claim move together.
- **Language.** Code, identifiers, comments, commit messages, pull-request bodies and documentation
  are English.
- **Safety.** Do not push, tag, publish or change repository settings unless the user asks. The vendored
  bundle policy `bundle:agent-safety-and-autonomy` applies.

### This is a security decision function

These are spec-level and easy to undo by accident. A change that widens what is allowed needs an
explicit justification.

- **Fail closed.** An unmatched origin, method or requested header denies, and a denial is a return
  value, never an exception.
- **Wildcard with credentials is rejected at construction.** Browsers refuse
  `Access-Control-Allow-Origin: *` on a credentialed response.
- **`Vary: Origin` accompanies every origin-dependent response**, or a shared cache serves one origin's
  allow-header to another.
- **Header matching is case-insensitive under `Locale.ROOT`** (under `tr-TR`, `"I".toLowerCase()` is a
  dotless i). Origin matching is exact and case-sensitive.
- **A denied decision emits no headers.** Never `Access-Control-Allow-Origin: null`: `"null"` is a
  valid origin that sandboxed documents send, so emitting it grants access.
- **Advertised methods and headers are sorted**, so the value is byte-stable. Methods are matched
  against the same uppercased set that is advertised.
- **Preflight-only fields stay off simple requests.** Methods, headers and `max-age` are emitted only
  when `preflight` is true.

Report vulnerabilities through [`SECURITY.md`](SECURITY.md), never a public issue.

## 3. Architecture and documentation entry points

- [`README.md`](README.md): what the capability does and does not do, building the upstreams, the
  manifest.
- [`ROADMAP.md`](ROADMAP.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).
- Composition model: [`docs/adr/ADR-024.link.md`](docs/adr/ADR-024.link.md). Manifest and stamp:
  [`docs/adr/ADR-053.link.md`](docs/adr/ADR-053.link.md) and `exeris-sdk-composition-spec` in
  exeris-sdk.
- The Wall: [`docs/adr/ADR-055.link.md`](docs/adr/ADR-055.link.md), then `CapTierWall` in
  exeris-tooling's `exeris-codegen-java`. The guard is what runs; read it before arguing the rule.
- [Capability Author Guide](https://github.com/exeris-systems/exeris-docs/blob/main/cap-author-guide.md);
  ADRs indexed in [`adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md).

## 4. `.agents/` discovery

- `.agents/manifest.yaml` imports the organisation bundle `exeris-agents`, pinned by version, commit
  and digest.
- `.agents/vendor/exeris-agents-2.1.0/` is the vendored bundle: policies, base schemas and the hook
  dispatcher. Never edit it in place; bumping it means re-vendoring.

This repository authors no profiles, skills, workflows or hooks of its own.

## 5. Verification and reporting

exeris-sdk `v0.10.0` and exeris-tooling `v0.10.0` are built from their tags first, as `README.md`
gives it; the tooling module list is an allowlist, `exeris-tooling-bom` included.

```bash
mvn compile -Dexeris.codegen.skip=true   # pass 1 — seed capability metadata
mvn verify                               # pass 2 — generate, validate the graph, scan the Wall
git status --porcelain                   # must be empty: the committed manifest matches the build
```

Both passes are needed after a `clean`: the processor writes metadata only during `compile`. The
build log must show `own cap(s): [corspolicy]`. Report what ran and its result; do not claim a check
that was not run.

## 6. Provider adapters

`CLAUDE.md` is a pointer to this file. No other provider adapter is rendered in this repository.
