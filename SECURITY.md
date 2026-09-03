# Security Policy

This capability is a **security decision function**. It decides whether a cross-origin request is
permitted and what the response must say about it, so a defect here is a defect in an access-control
boundary even though the code touches no socket.

## Supported versions

| Version / branch | Security support |
| --- | --- |
| `main` (default branch) | ✅ Supported |
| tagged releases | none exist yet — see below |
| forks, feature branches | ❌ Not supported |

**No artefact has been published from this repository.** Its registry status is `scaffolded`: there
is no release on GitHub Packages or Maven Central, and no consumer integrated. A report today is
about source on the default branch. When the first artefact ships (`ROADMAP.md` 0.4.0), this table
gains a supported release line.

## Scope — what this component decides, and what it does not do

The boundary is unusually sharp here, and reports are easier to triage if it is stated first.

**In scope.** Everything that produces a `CorsDecision`: origin matching, method and header
negotiation, preflight handling, credential rules, the emitted header map, and the construction-time
validation in the settings type.

**Out of scope by construction.** This cap **applies nothing**. It reads no socket, writes no header,
and owns no transport — a consuming cap projects its own request type into `CorsRequest` and applies
the returned decision to whatever response type it owns. A consumer that ignores the decision,
applies it to the wrong response, or turns "CORS does not apply" into a `403` has a bug in that
consumer. Report those to the consumer's repository; if the confusion was *invited* by this cap's API
shape, that is in scope here and worth saying so.

## Security model

Fail closed, without exception:

- An unmatched origin, method, or requested header **denies**.
- A denial is a normal return value, never an exception — a rejected origin is an expected outcome,
  not a failure of the call.
- A denied decision emits **no CORS headers at all**. The correct response to a disallowed origin is
  silence, letting the browser refuse.
- Configuration that cannot be honoured is rejected at construction rather than degraded at request
  time, so an operator learns about it once instead of never.

## What to report

Please report anything that makes the decision function *more permissive than configured*, or that
makes a correct configuration unenforceable.

### Origin matching

- Any non-exact match: prefix, suffix, substring, or `startsWith` / `endsWith` behaviour that lets
  `https://app.example.com.evil.test` or `https://evil-app.example.com` satisfy a configured
  `https://app.example.com`.
- Case-folding, trailing-dot hosts, punycode/IDN confusion, or Unicode normalization that makes two
  distinct origins compare equal.
- Scheme or port being ignored, defaulted, or normalized away — `http://` must not satisfy a
  configured `https://`, and an explicit port must not be droppable.
- Reflecting an arbitrary request origin into `Access-Control-Allow-Origin` without validating it.
- Emitting `Access-Control-Allow-Origin: null`. The literal string `"null"` is a **valid origin** that
  sandboxed documents and some redirects send, so emitting it grants access rather than denying it.
- Any origin-pattern syntax (planned for a later milestone) that is exploitable through catastrophic
  backtracking, or whose wildcard can cross a label boundary.

### Credentials

- A wildcard origin surviving alongside `allowCredentials` — the two are mutually exclusive, and the
  combination is refused at construction today.
- `Access-Control-Allow-Credentials: true` emitted on a decision whose origin was not matched
  exactly.
- Any path that emits credentials headers for a configuration that did not ask for them.

### Headers and methods

- Header matching that is locale-sensitive. Comparison must use `Locale.ROOT`: under `tr-TR`,
  `"I".toLowerCase()` yields a dotless i, and a configured `If-Match` stops matching itself.
- A wildcard in `Access-Control-Allow-Headers` being treated as covering `Authorization`. Per the
  Fetch standard it does not, and treating it as such widens the surface silently.
- A method advertised in `Access-Control-Allow-Methods` that the policy then denies — or the reverse.
  Advertising and enforcing must use the same normalized set.
- Any header or method reaching the decision without negotiation, including through the safelisted
  request-header rules.

### Preflight and caching

- A bare `OPTIONS` request being treated as a preflight, or `Access-Control-Request-Method` being
  confused with the transport method.
- Preflight-only values (methods, headers, `max-age`) leaking onto a simple request.
- `Access-Control-Max-Age` long enough that tightening a policy leaves browsers acting on the old,
  more permissive answer for an unreasonable window.
- A missing `Vary: Origin` on any origin-dependent response. Without it a shared cache serves one
  origin's allow-header to another, which is a cache-poisoning cross-origin read.

### Exposure and information leaks

- `Access-Control-Expose-Headers` (planned) advertising a header carrying credentials, session state,
  or internal infrastructure detail.
- Any decision, exception message, or diagnostic that echoes a request value in a way a consumer is
  likely to log or return verbatim.

### Availability

- Input from an unauthenticated caller — an origin string, a long or repeated
  `Access-Control-Request-Headers` list — causing unbounded work or allocation in `evaluate`. The
  method is expected to be allocation-light and linear in its input.

### Supply chain

- Anything that reintroduces a runtime dependency into this jar, or that would let the build resolve
  an artefact it does not today.
- A committed `cap-manifest.json` that does not correspond to the declaration in the same commit.

## Out of scope

Unless they bypass one of the guarantees above:

- How a consumer applies the decision, including a consumer that converts "CORS does not apply" into
  a rejection.
- Transport-level, TLS, HTTP-parsing, and routing concerns — this cap owns none of them.
- Browser behaviour, browser bugs, and differences between browser CORS implementations.
- An operator deliberately configuring a permissive policy (`"*"` without credentials is a supported
  configuration, not a vulnerability).
- Reports that a decision is *more restrictive* than expected. That is a bug worth filing publicly,
  but fail-closed is the intended posture.
- Scanner output with no reachable code path in this repository.

## Severity guidance

**High or critical** — anything that lets an unlisted origin obtain a successful CORS decision:
non-exact origin matching, wildcard surviving with credentials, reflected or `null` origins,
credentials emitted for an unmatched origin, or a missing `Vary: Origin` that enables cross-origin
cache poisoning.

**Medium** — over-broad negotiation with a narrower blast radius: wildcard covering `Authorization`,
preflight values leaking onto simple requests, advertise/enforce mismatch, excessive `max-age`,
expose-headers advertising a sensitive header.

**Low or maintenance** — construction-time validation that could be stricter, diagnostics that could
be clearer, or unbounded-work reports without a practical availability impact.

Severity may be adjusted for exploitability, whether default configuration is affected, and whether
the issue widens or narrows what is allowed.

## Reporting a vulnerability

Use **GitHub private vulnerability reporting** on this repository
(<https://github.com/exeris-systems/exeris-caps-cors-policy/security/advisories/new>). It is enabled.

**Do not open a public issue, discussion, or pull request for a suspected vulnerability.**

A useful report includes:

- the affected commit or branch;
- the configuration (`CorsPolicySettings`) that exhibits it;
- the `CorsRequest` that triggers it — origin, method, requested headers, preflight or not;
- the decision you observed and the decision you expected;
- whether default or permissive configuration is affected;
- a failing test against `DefaultCorsPolicyTest` if you have one. That is the fastest possible triage
  path for this repository, and it becomes the regression test.

Do not include credentials, tokens, or production data. A synthetic origin and a synthetic header
list are always sufficient to demonstrate a defect here.

## Disclosure

Maintainers aim to acknowledge a report within 72 hours and give an initial triage result within 7
days. A fix carries a regression test in the matching `@Nested` group, and updates the affected
javadoc or ADR when it changes an observable contract.

Please allow a reasonable opportunity to investigate and release a mitigation before public
disclosure. If your organization requires a disclosure deadline, state it in the initial report so it
can be coordinated early.

Good-faith research that avoids data destruction, lateral movement, and access to third-party systems
is welcome. This is not legal advice and does not authorize testing against systems you do not own.
