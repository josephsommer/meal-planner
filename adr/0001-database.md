# ADR-0001: Which database technology to use

## Status

Accepted 

## Context

I need to pick a database to back the project. I do not have a goto
solution in mind. Postgres and MySQL are common picks, but I do not
have real world experience making a decision between the two.

My chief priorities are simplicity and low cost. From a purely
technical PoV, a RDMS is overkill. But I need one to demonstrate
a proper fullstack environment. So I want something light that 
I can standup quickly and not think too hard about.

## Decision

MySQL. Postgres is a shiny solution but its a swiss army knight
when all I need is a letter opener.

## Alternatives considered

  - SQLite: Perfect for small projects, but as an embedded database
    it won't deploy to RDS or similar. Obscures the stack and defeats
    the point of the exercise.

## Consequences

  - Switching databases is hard, there's no going back from here.
