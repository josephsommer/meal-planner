# ADR-0004: Readability4J for text extraction

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

Accepted

## Context

The core of this project's functionality rests in whatever pulls a readable
article out of a url. I need to identify what will do that, and where it will
run. The vague architecture I started with had a black box worker that would
do the text extraction. It's time to define what goes in the box. 

The solution does not need to be fancy. This is ultimately a sample project, 
the point is to finish and show off the infrastructure. The problem has almost
certainly been solved before. The component will run outside the API, so
I am free to choose any language or runtime.

## Decision

I will use Readability4J on a Java Lambda. It is a Java implementation
based on Mozilla's Readability library.


## Alternatives considered

- Mozilla's Readability: More recognizable, but uses a JS runtime and would
  require getting a Node stack set up.
- Integrating Readability4J into the API to make it monolithic. There's an
  interesting performance angle to consider, but the point of the project
  is to build different types of infrastructure.

## Consequences

- Stateless worker, demonstrates capability for horizontal scaling.
- It will be hard to get out of the lambda model
- Need to think about what to do with failed runs
- Lambda's are light, can replace it with a new lambda later

## References

https://github.com/dankito/Readability4J
https://github.com/mozilla/readability