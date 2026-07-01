# ADR-0002: What should manage database credentials?

## Status

Accepted 

## Context

There are two options to consider for database authentication between the Java backend
and RDS. IAM roles, or string credentials stored as secrets. String credentials are the
default option for MySQL, but deploying to RDS offers another option to authenticate
with an IAM role.

## Decision

IAM authentication. It does two big things in this context. It demonstrates
AWS knowledge, and creating a tricky test/local environment setup that doesn't 
have access to IAM.

## Alternatives considered

  - Strings in a secret: Storing secrets is the standard approach, and nothing is 
  particularly wrong with it here. The DB needs a master username/password anyways
  so this would be done for free.

## Consequences

  - I'm adding more overhead to my setup, making the RDS deployment take longer.
  - Project will be more polished in the end, lining up with my goals. The point
  here is the process, not the resulting app.
