# Contributing to exeris-caps-cors-policy

Thank you for contributing. This repository is Apache-2.0 (`community` under ADR-023) and open to
outside contributions.

Two things are worth knowing before you start. It is a **security decision function**, so a change
that widens what is allowed needs an argument, not just a green build. And it is the **first
capability in the Exeris cap tier** — 53 more are specified and unwritten — so conventions settled
here get copied, and a shortcut taken once gets taken 54 times.

## Build & test

```bash
mvn compile -Dexeris.codegen.skip=true   # pass 1 — seed the capability metadata
mvn verify                               # pass 2 — generate, validate, scan the Wall
```

Both passes are required after a `clean`; `README.md` explains why, and covers building the two
upstreams from source (`eu.exeris` artefacts are not on Maven Central yet). `CLAUDE.md` carries the
full set of invariants and the reasoning behind them — it is written for agent sessions but it is the
most complete account, and worth reading before a non-trivial change.

Requires **JDK 25** and Maven 3.9+. CI builds JDK 25 and 26; both rows must be green.

## What a change has to preserve

- **The `api` / `internal` split.** `..corspolicy.api` is the entire cross-cap surface. No `internal`
  type may appear in an `api` signature, and the package root `eu.exeris.caps.corspolicy` must not be
  renamed or given an extra segment — the Wall guard derives this cap's identity from it (ADR-055).
- **Zero runtime dependencies.** No new `compile`-scope dependency, and no kernel, Spring, servlet or
  transport type anywhere. The SDK annotations stay `provided`; the processor stays in
  `annotationProcessorPaths`.
- **Fail-closed behaviour.** Unmatched origin, method or header denies; a denial is a return value,
  never an exception; a denied decision emits no headers at all.
- **The committed manifest.** `src/main/generated/java/cap-manifest.json` is tracked, and
  `exeris:generate` writes it into `src/`, not `target/`. If your change touches `CorsPolicyModule`,
  commit the regenerated manifest in the same commit. A clean `git status` after `mvn verify` is the
  check — regeneration is byte-stable, so a dirty one means declaration and manifest disagree.
- **The JDK baseline.** `maven.compiler.release` stays 25 and no preview flag appears anywhere
  (kernel ADR-066, SDK ADR-069).
- **A credential-free build.** The tooling reactor in `.github/workflows/build.yml` is an allowlist
  that deliberately keeps the kernel — and therefore a GitHub Packages token — out of the build.
  Nothing in a pull-request workflow may require a secret.

## Tests

JUnit 5 + AssertJ only. **No mocking framework**: this is the ADR-058 dependency contract for the
generated-test channel, and hand-written tests are held to it so the two stay indistinguishable to
the build. Doubles are written, never mocked.

New behaviour in `DefaultCorsPolicy` belongs in the matching `@Nested` group of
`DefaultCorsPolicyTest`, not a parallel test class. A bug fix lands with the test that fails without
it.

If you are fixing a spec-conformance issue, say which part of the Fetch standard you are conforming
to. `ROADMAP.md` 0.3.0 plans a tabular conformance corpus; a case you contribute now is a case that
corpus will inherit.

## Conventions

- Code, identifiers, comments, commit messages, PR titles and bodies, and documentation files are
  **English**.
- Conventional-commit prefixes, matching the existing history (`feat:`, `fix:`, `build:`, `ci:`,
  `docs:`).
- Comments explain *why*, not *what*. Several non-obvious decisions in this repo are load-bearing and
  are documented where they can be found — the wildcard/credentials rejection, `Locale.ROOT`
  matching, the empty header map on denial. Keep that standard.

## Architecture and ADR discipline

The Exeris ecosystem has **one ADR number space** across ~20 repositories, indexed in
`exeris-docs/adr-index.md`. If your change needs a decision record:

1. **Reserve the number in that index first**, in its own pull request, then write the content. A
   number taken from a stale local checkout is the standard way to collide.
2. A cross-repo decision leaves a `docs/adr/*.link.md` stub in every affected repository; see the
   three already in `docs/adr/`.
3. Refactor-only rationale belongs in the pull-request description, not in an ADR.

Most substantial changes do not need a new ADR — they need to be consistent with ADR-024 (composition
model), ADR-053 (manifest format) and ADR-055 (the Wall). The stubs in `docs/adr/` say when each one
applies here.

## Pull-request checklist

- [ ] `mvn verify` green, and `git status` clean afterwards
- [ ] The `api` / `internal` split and the package root are intact
- [ ] No new `compile`-scope dependency; no kernel / Spring / transport type
- [ ] Tests added or updated in the right `@Nested` group; JUnit 5 + AssertJ only
- [ ] Regenerated `cap-manifest.json` committed, if the declaration changed
- [ ] Anything that widens what the policy allows is justified in the description
- [ ] `README.md` / `CLAUDE.md` / `ROADMAP.md` updated if a documented claim changed

## Security

Do not open a public issue for a suspected vulnerability. `SECURITY.md` describes what is in scope
and how to report privately.
