# ADR-NNNN: Short decision title in present tense

<!--
  Filename convention: adr/NNNN-short-title.md
  e.g. adr/0001-jwt-over-sessions.md

  Title rules:
  - Number sequentially, never reuse a number
  - Present tense, as if stating the decision: "Use JWT for auth"
    NOT "We will use JWT" or "JWT authentication"
  - Short enough to fit on one line — 4 to 8 words
-->

## Status

Proposed | Accepted | Deprecated | Superseded by ADR-NNNN

<!--
  Pick one. Most of yours will be "Accepted" because you're writing
  them right after making the decision. Use "Superseded by ADR-NNNN"
  when a later ADR overrides this one — and keep this file in the repo.
  ADRs are append-only history, never delete old ones.

  Date: YYYY-MM-DD  (optional but useful)
-->

## Context

<!--
  What's the situation that forces a decision? 2-4 short paragraphs.

  Cover:
  - What are we building / what's the immediate need?
  - What constraints matter? (time, budget, team skills, existing stack)
  - What forces are in tension? (e.g., simplicity vs scalability,
    speed-to-market vs maintainability)

  Do NOT state the decision here. This section should read like a
  problem statement that a reasonable person could solve multiple ways.
  If after reading the Context the answer feels obvious, the Context
  is incomplete — you're missing the tension.

  Write this in plain English. Pretend you're explaining the situation
  to a new teammate over coffee.
-->

## Decision

<!--
  What did we decide? State it in 1-3 sentences, in the active voice,
  as a clear directive.

  Good: "We will use JWT bearer tokens for API authentication, signed
  with HS256 and stored in the browser's memory (not localStorage)."

  Bad: "We considered several options and JWT seemed best."
  (vague, passive, doesn't tell future-you what was actually decided)

  If the decision has important sub-parts (algorithm, library choice,
  config), list them as bullets under the main statement.
-->

## Alternatives considered

<!--
  Optional section but HIGHLY recommended for a portfolio project —
  this is where you demonstrate engineering judgment to a reader.

  List 2-3 alternatives you seriously considered. For each:
  - One-line description of the option
  - 1-2 reasons it didn't win

  Keep it brief. The goal is to show you didn't just grab the first
  idea — you weighed options. A hiring manager skimming this section
  learns more about how you think than from any other part of the ADR.

  Example:
  - Server-side sessions with Redis: simpler conceptually, but adds
    Redis as a stateful dependency we don't otherwise need.
  - AWS Cognito: managed, but introduces vendor coupling and a
    learning curve disproportionate to v1 scope.
-->

## Consequences

<!--
  What becomes true once this decision is in effect? List both
  positive and negative consequences honestly. 4-8 bullets total.

  Positive consequences are easy ("API is stateless, scales horizontally").
  The valuable ones are the negative consequences you accepted —
  this is where the document proves you thought clearly. Every
  real decision has tradeoffs. Naming them out loud signals maturity.

  Examples of the kinds of consequences to capture:
  - What new capabilities does this unlock?
  - What's now harder or off-limits?
  - What new operational burden does this create?
    (rotating signing keys, monitoring queue depth, etc.)
  - What does this lock us in to? What's the cost to reverse?
  - What follow-up work does this imply?

  If you can only think of positives, you haven't thought hard enough.
  Sit with the decision for another five minutes.
-->

## References

<!--
  Optional. Links to:
  - Issues / PRs this decision relates to
  - External docs, RFCs, blog posts that informed the decision
  - Related ADRs

  Skip this section entirely if you have nothing to cite. Don't
  pad it.
-->