# exeris-caps-cors-policy — Roadmap to `implemented`

This repository is the **first and, at the time of writing, only** `exeris-caps-*` repository. It
carries two obligations that pull in different directions, and this roadmap keeps them apart on
purpose:

1. **Be a complete cap.** Reach `implemented` in [`cap-license-registry.md`](https://github.com/exeris-systems/exeris-docs/blob/main/cap-license-registry.md)
   — "feature-complete against its `@Provides` contract, with tests".
2. **Be the reference cap.** 53 further caps will be authored against whatever conventions land
   here. A gap left open here is a gap copied 53 times.

Milestones are versions, not dates. Dates appear only on what has shipped.

> **`1.0.0` is not on this roadmap.** It is a gate, not a milestone — see the last section. Freezing
> a service contract that no consumer has ever called is how you freeze the wrong shape.

---

## Where this sits

**Not on anyone's critical path, and that is the opportunity.** The API Gateway SKU is Track B
H1 2027 (whitepaper §7), and its blocking caps are `gateway-core` / `policy-chain` /
`route-registry` — all `specified`, none scaffolded. Nothing is waiting on this repo, so every
breaking change costs zero today and costs a major tomorrow.

**No consumer exists.** `policy-chain` and `gateway-core` are `specified`. Nothing validates the
API shape end to end, which is the standing risk this roadmap manages by *not* freezing.

**Upstream constraints this repo does not own** (from the [Capability Author Guide](https://github.com/exeris-systems/exeris-docs/blob/main/cap-author-guide.md) §8):

| Constraint | Effect here | Owner |
|---|---|---|
| `compositionVersion` never wired — every build stamps `0.0.0` | Cosmetic; the content binding is the half that carries weight | `exeris-tooling` U3 |
| Manifest emitted to a *source* root, not the runtime classpath | Delivering it to a running SKU is not this repo's problem | SKU scaffold, Phase 5 |
| No `composition.json` reader | Configuration cannot arrive from a SKU manifest yet — which is why 0.2.0 builds its own construction path | SKU scaffold, Phase 5 |
| ADR-024 open follow-up 5 — cross-repo composition CI gate | Triggers when the **second** cap repository ships. Recorded, not owned | `exeris-docs` |

---

## 0.1.0 — scaffold (shipped 2026-08-18)

- [x] `@CapabilityModule` + `@Provides(CorsPolicy, "1.0.0")`, no `@Requires`, no `@CapabilityLifecycle`
- [x] `api` / `internal` split under the load-bearing `eu.exeris.caps.corspolicy` root
- [x] Zero runtime dependencies — annotations are `@Retention(SOURCE)` and taken at `provided`
- [x] Both codegen goals bound (`generate` + `verify-capabilities`), two-pass build documented
- [x] `cap-manifest.json` committed, with a validated stamp and a SHA-256 content binding
- [x] CI green on JDK 25 and 26, credential-free (allowlisted tooling reactor incl. `exeris-tooling-bom`)
- [x] 15 tests across 5 `@Nested` groups; JUnit 5 + AssertJ only (ADR-058 dependency contract)
- [x] Apache-2.0 per ADR-023 — `LICENSE`, POM `<licenses>`, README claim in step

---

## Ships with this roadmap

> **Goal:** everything that costs nothing to write now, depends on none of the decisions below, and
> is worth more before the work starts than after it.

Pulled forward out of 0.4.0 on purpose. A convention written after the code it was meant to govern
has governed nothing — and every one of these items is doc-only, so they ride in the same PR as this
file.

- [x] **`CLAUDE.md`** — every sibling `exeris-*` repo had one and this one did not. The only encoded
      guardrails lived inside a GitHub Actions review prompt, which a local session never reads: the
      Wall, the zero-runtime-dependency invariant, the two-pass build, the regenerate-the-manifest
      rule and the JDK/preview baseline were enforced on a PR and invisible while the code was being
      written
- [x] **ADR link stubs** — `docs/adr/ADR-024.link.md`, `ADR-053.link.md`, `ADR-055.link.md`, per the
      cross-repo stub convention. Each says when its decision applies *here*, not what the decision
      is. ADR-085's own file cannot ride along: reserve-number-first means content never precedes the
      upstream reservation. The matching rows in the index's **cross-repo stub table** are part of
      the `exeris-docs` PR, not this one
- [x] **`SECURITY.md`, `CONTRIBUTING.md`, `.github/CODEOWNERS`** — a public Apache-2.0 repository
      shipping a security decision function offered no vulnerability-reporting path at all. The
      policy is written around this cap's actual failure modes (non-exact origin matching, wildcard
      with credentials, reflected or `null` origins, a missing `Vary: Origin`) and states the scope
      boundary explicitly: this cap decides, and a consumer that misapplies the decision has its own
      bug. Private vulnerability reporting is enabled on the repository, so the path it names works
- [x] **`.github/dependabot.yml`** — actions on the sibling convention, and **Maven enabled with
      `eu.exeris:*` ignored**, which is a deliberate divergence. `exeris-tooling` and `exeris-kernel`
      disable Maven because a bump there has to travel a milestone path behind gates an individual PR
      would not run; this repo's entire gate is `mvn verify` on two JDK rows plus the manifest
      assertion, and every PR already runs exactly that. The `eu.exeris` pins stay ignored because
      they are built from source at a tag `build.yml` also hardcodes — a bot PR would propose a
      version the build cannot produce

---

## 0.2.0 — the contract

> **Goal:** settle the shape of `CorsPolicy` while breaking it is still free. Every item here is a
> decision, not a feature. Nothing in 0.3.0 should be able to force a change to the surface this
> milestone freezes.

**Entry gate:** ADR number reserved in `adr-index.md` **before** content is drafted. Next free
number is **085** — verified against a freshly fetched `origin/main`, where `075`–`084` are all held.
**Re-verify immediately before opening the reservation PR; do not trust this line.** It has already
been wrong twice while this roadmap was being written: first `079` (the local checkout said `078`,
`origin/main` said `079`), then `080`–`084`, taken by kernel-side reservations in the days between.
A stale index gives no signal at all, which is precisely the failure mode the index's own reservation
note records.

Two misses in one document is no longer an anecdote about carelessness — it is a measurement of how
fast that index moves, and the number is now the least interesting part. `exeris-docs` already ships
`.claude/scripts/adr-filename-check.sh`, which enforces reserve-number-first — but against the
**local** index, so it cannot see a number taken upstream since the last fetch, which is every number
that was ever a problem. The reservation PR therefore carries the fix for the class alongside the
instance; see [Companion work in `exeris-docs`](#companion-work-in-exeris-docs) below.

- [ ] **ADR-085 — the shape of a capability's service contract.** Owned by this repo, scope
      `cross-repo` (it amends HLA §3.2). It is the first cap-contract ADR in the ecosystem, so it
      is precedent for 53 caps, not just documentation of three refactors. It must answer:
  - the three-state outcome (below) and why a boolean was the wrong type
  - where construction lives, and how much of it is frozen surface
  - whether per-route selection belongs in the request, the service, or the consumer
  - **the rule that keeps `@Provides(version)` at `1.0.0` across this reshape.** A service version
    accrues no semver obligation until something can resolve *and* construct it. `CorsPolicy@1.0.0`
    was a scaffold: no published artefact, no consumer, and no public path to an instance — so it
    was never obtainable, and there is nothing for a major bump to protect. The reshape lands
    *inside* `1.0.0`. The ADR states this as a rule the next 53 caps inherit rather than a one-off
    dispensation, and **names its own expiry**: it stops applying the moment 0.4.0 publishes a
    resolvable artefact. Without that clause the rule is a loophole rather than a policy
- [ ] **Three-state outcome.** `CorsOutcome { NOT_APPLICABLE, ALLOWED, DENIED }` becomes a
      `CorsDecision` component; `allowed()` survives as a derived accessor. Today `allowed() == false`
      means both "no `Origin` header, CORS does not apply" and "origin rejected" — and the javadoc,
      the README and a test all warn the consumer not to turn the first into a 403. A warning
      repeated in three places is a missing type.
- [ ] **A public construction path.** `CorsPolicies` in `api` — a builder that returns a
      `CorsPolicy`. `CorsPolicySettings` **stays in `internal`**: the builder is the frozen surface,
      the settings record is an implementation detail. Today nothing outside this jar can obtain a
      `CorsPolicy` at all, which is the single largest reason the cap is unusable rather than merely
      incomplete.
- [ ] **Named profiles.** `CorsPolicyRegistry` in `api`, resolving a profile name to a `CorsPolicy`,
      with a default profile. Added as a **second** `@Provides` (the annotation is repeatable), so a
      single-policy consumer still takes `CorsPolicy` and a gateway takes the registry. Route→profile
      mapping stays with the consumer — the cap never learns what a route is, which is what keeps
      transport out of the request model (ADR-024 predicate 4).
- [ ] **HLA §3.2 amendment** — drop `@Requires policy-chain` from this cap's row. The inventory and
      the repository have disagreed since scaffold; the repository is right. A speculative edge is a
      false edge in the composition DAG and changes the derived `initOrder` for every SKU that
      includes this cap. Regenerate `cap-license-registry.md` from §3.2 in the same PR — the registry
      is derived, never hand-patched.
- [ ] **ADR-055 amendment — caps may read `eu.exeris.kernel.core.*`.** ADR-055 §"open" deferred this
      to "the first real cap in Phase 2" and named the risk of guessing: false-failing the first cap
      that touches `KernelWebClient`. **Ruling: Core is tier-neutral — shared by the Community and
      Enterprise drivers alike — so it is callable; only `**.internal.**` is forbidden.** This is doc-only: `CapTierWall` already implements exactly that (the unambiguous
      intersection). What changes is the *rationale* text, which reads "only the SPI surface is
      callable" in both ADR-024 line 77 and the author guide's Wall table, and would have licensed a
      stricter future implementation. Amendment lands in `exeris-tooling` (which owns ADR-055), with
      the guide corrected in lockstep.
- [ ] **SDK pin `0.10.0` → `0.11.0`** (released 2026-08-26). POM property and the CI checkout ref,
      moved together and to the **tag**, never to a branch. Tooling stays on `0.7.0`: `main` is
      `0.8.0-SNAPSHOT` and no `v0.8.0` tag exists, so there is nothing to pin to yet.
- [ ] **Manifest regenerated** and committed, with the two-pass build. A declaration change carrying
      a stale `cap-manifest.json` is a blocker by this repo's own review contract.

**Exit gate:** `mvn verify` green on both JDK rows; `stamp.validated: true` with a `sha256:` binding;
HLA §3.2 and the registry agree with the repository; ADR-085 accepted.

---

## 0.3.0 — Fetch conformance

> **Goal:** the decision function is complete and provably spec-shaped, not merely consistent with
> its own assumptions.

**The corpus lands first, not last.** A tabular case set derived from the Fetch standard —
origin × method × headers × credentials × preflight, expected outcome and expected header map — as
*data*, not as one `@Test` per case. For a fail-closed security decision this is the only way
coverage means "agrees with the spec" rather than "agrees with the author". Held to the ADR-058
dependency contract: JUnit 5 + AssertJ, no new test dependency, no mocking framework.

- [ ] **Conformance corpus** wired as a parameterized suite, with every currently-passing behaviour
      re-expressed through it — so the corpus is the specification and `DefaultCorsPolicyTest`'s
      `@Nested` groups keep only what is genuinely implementation-specific
- [ ] **`Access-Control-Expose-Headers`** — the one CORS response header the cap cannot express
      today. Without it no consumer can surface a single header of its own to a cross-origin caller
- [ ] **Origin matching** — subdomain patterns (`https://*.example.com`), scheme/host/port
      normalization, an explicit policy for the literal `null` origin (sandboxed documents send it,
      and it must never be reachable by accident), and syntax validation that rejects a malformed
      configured origin at construction rather than silently never matching
- [ ] **Credentials and cookies** — `allowCredentials` is one global boolean today. Per-profile
      credentials falls out of 0.2.0's registry for free; what still needs deciding is whether a
      single profile may credential some origins and not others, and how that interacts with the
      construction-time wildcard refusal that already exists
- [ ] **`OPTIONS` and preflight shape** — `preflight` is a caller-supplied boolean the cap cannot
      check. Derive it from the presence of a requested method, so `OPTIONS` *without*
      `Access-Control-Request-Method` resolves to `NOT_APPLICABLE` (it is an ordinary OPTIONS
      request) instead of being mis-judged as a preflight. Decide whether the decision states that a
      preflight is **complete** — answerable without forwarding upstream. That is a policy fact, not
      a transport fact, and withholding it forces every consumer to re-derive it
- [ ] **Method and header negotiation** — the safelisted request headers (`Accept`,
      `Accept-Language`, `Content-Language`, `Content-Type`) admitted without explicit
      configuration; wildcard `*` in allowed headers **with the `Authorization` carve-out** the Fetch
      standard requires; `Vary` on a preflight extended to `Access-Control-Request-Method` and
      `Access-Control-Request-Headers`; `max-age: 0` distinguished from "advertise nothing"
- [ ] **Private Network Access** — `Access-Control-Request-Private-Network` and its response
      counterpart, in scope so "complete decision function" means complete. Buy it with open eyes:
      the specification was renamed mid-flight (Local Network Access) and ships in one engine, so
      this is the one area of the corpus tracking a moving target. Isolate it behind its own
      settings flag and its own corpus section, so a spec change stays a contained edit
- [ ] **JaCoCo gate** in `verify` — 85% instruction/line, matching the SDK's house threshold
- [ ] **Mutation testing (PIT)** — a mutant that flips a `deny` to an `allow` and survives is
      precisely the defect class this cap exists to not have. A build-time plugin, not a test
      dependency, so the ADR-058 contract is untouched

**Exit gate:** the corpus passes end to end; both gates green; every `@Provides`-visible behaviour
has a corpus case behind it.

---

## 0.4.0 — from example to template, and something to resolve

> **Goal:** the conventions shipped up front proved themselves against three milestones of real
> work; now extract them, and publish an artefact so the second cap and the SKU scaffold have
> something to depend on.

- [ ] **`docs/adr/ADR-085-*.md`** — the authoritative copy, written once the number is held upstream
- [ ] **Archetype seed** — the author guide lists "No archetype" as an open gap and calls itself the
      manual substitute. This repository is the only working example, so the seed is extracted here
      even though the archetype itself belongs in `exeris-tooling`. The deliverable here is the
      parameterized skeleton and the list of what must vary per cap (package root, cap name, licence
      row, `@Provides` service); the plugin wiring is a tooling PR
- [ ] **Release workflow → GitHub Packages.** A tagged release plus release notes, on the SDK/tooling
      pattern. **The credential is confined to the release job** — `build.yml` stays credential-free,
      which is a designed property of this repo and not an accident to trade away
- [ ] **Registry flip `scaffolded` → `implemented`** in `cap-license-registry.md`, via HLA §3.2 as
      the derived-not-retyped rule requires

**Exit gate — the finish line of this roadmap:** a resolvable artefact exists, the registry says
`implemented`, and a second cap author can start from conventions rather than from this repository's
git history.

> **On the version number:** the release option chosen during planning was phrased "0.2.0 to GitHub
> Packages". With three milestones ahead of it, the published version lands at **0.4.0**. The
> decision that mattered — publish a 0.x to GitHub Packages, do not freeze semver — is unchanged.

---

## Companion work in `exeris-docs`

The roadmap lives here; the registry, the HLA and the ADR index live in `exeris-docs`. They are
separate repositories, so this is **not one PR** — it is two, in a fixed order, plus a third that
must wait.

**What `exeris-docs` already has, and must not be rebuilt.** `.claude/scripts/` holds
`adr-filename-check.sh` (filename pattern + reserve-number-first), `check-consistency.sh`
(anti-drift guard over the toolkit itself), and the two *locators* `drift-sweep.sh` /
`taxonomy-check.sh`, with `/adr-reservation-check` driving them. The scripts' own README states they
"run in CI or a pre-commit hook unchanged".

**What is actually missing**, in ascending order of effort:

| Gap | Evidence | Shape |
|---|---|---|
| Nothing runs the existing scripts | `.github/workflows/` holds only `claude.yml` and `claude-code-review.yml`, while the scripts' README already promises CI-readiness | A `docs-guard.yml` invoking them on PR. The two true gates fail the build; the two **locators must stay advisory** — their README is explicit that exit 1 means "review required", not "broken" |
| Reserve-number-first is checked against the *local* index | Two misses in this one document: `078`→`079`, then `079`→`085` as `080`–`084` were taken upstream in between | A check that resolves the index from a fetched `origin/main`, fails on collision, and prints the next free number |
| The registry's "derived, not retyped" rule has no mechanism | `cap-license-registry.md` instructs re-deriving from HLA §3.2 and re-checking totals against ADR-023's 3/50/1 split — by hand | A checker (not a generator): assert cap set, licence and layer agree between §3.2 and the registry, and that totals match ADR-023. It must **not** touch `status` — that column is registry-owned and absent from §3.2 |

The third one is on this roadmap's critical path rather than adjacent to it: milestone 0.2.0 edits
§3.2 (drop `@Requires policy-chain`) and 0.4.0 edits it again (`scaffolded` → `implemented`). Both
are exactly the hand-patching the registry warns against.

**Order, and why it is not negotiable.** `CLAUDE.md` ranks the ADR registry above the HLA, so the
HLA amendment cannot land before the ADR that justifies it:

1. **`exeris-docs`** — reserve ADR-085, register this repo's three stubs in the index's cross-repo
   stub table, and land the guard work above. None of it depends on the decision; the reservation is
   the motivating instance and the guard is the fix for its class.
2. **this repo** — `ROADMAP.md` plus the whole [Ships with this roadmap](#ships-with-this-roadmap)
   batch in one PR; ADR-085's content follows once the number is held.
3. **`exeris-docs`, after ADR-085 is accepted** — HLA §3.2 amendment + registry re-derivation.

The ADR-055 amendment (caps may read `eu.exeris.kernel.core.*`) is a fourth PR against
`exeris-tooling`, which owns that ADR; it is independent of this chain and can land at any point.

---

## 1.0.0 — a gate, not a milestone

**Entry condition: a real consumer has integrated.** `policy-chain` or `gateway-core` resolves this
cap's `@Provides` and runs a composition against it. Until then there is nothing to validate the
surface, and freezing it would only make the first integration expensive.

What 1.0.0 then costs, listed so it is not a surprise:

- **japicmp / revapi semver gate.** Blocked on its own chicken-and-egg: the gate resolves the
  previous release as its baseline, and there is no published artefact until 0.4.0. The gate becomes
  possible at the *release after* the first release, not at the first one — the same sequencing the
  SDK hit and deferred.
- **`@Provides(version)` frozen** — the service version stops moving without a major.
- **Source and javadoc jars**, and whatever Maven Central requires if the community caps' "formal
  Apache 2.0 releases" (whitepaper §7, 2028 horizon) go to Central rather than GitHub Packages.

---

## Deliberately out of scope

Named here so they read as decisions rather than oversights.

- **Transport binding.** Unchanged and non-negotiable: this cap applies no header to any response.
  The kernel exposes no response-header seam (ADR-061's `HttpRoutePolicy` governs route
  *authorization*, a different axis), and inventing one would put transport knowledge inside a cap.
  It is also what buys the zero-dependency jar.
- **Cross-service `@Requires`.** Tooling T12/T17, targeted at tooling 0.8.0. This cap requires
  nothing, so it is unaffected — noted only because a cap author reading this roadmap will meet it.
- **`@CapabilityLifecycle`.** This cap acquires no resource. Four no-op hooks would cost a
  reflective load and imply a contract it does not have.
