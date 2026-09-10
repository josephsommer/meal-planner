# ADR-0003: Use Sessions for api auth

## Status

Accepted

## Context

I'm implementing Spring Security into the backend. I need to pick
how I want to set it up. Spring Security supports many authentication
methods, and I want to configure authentication that makes sense.
The ideal method works without a lot of manual intervention. The
goal is to associate basic user data. Nothing here is actually
sensitive.

## Decision

I will use session authentication.

## Alternatives considered

  - JWT: Extremely common pattern, but it requires me to
    keep track of the tokens. Adds a lot of overhead for
    a throwaway project.

## Consequences

  - API is capable of differentiating users.
  - JWT can drop in at a later date

## References

https://gist.github.com/samsch/0d1f3d3b4745d778f78b230cf6061452