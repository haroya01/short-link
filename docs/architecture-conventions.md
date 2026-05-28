# Architecture Conventions

This project keeps HTTP shape, application shape, and persistence shape separate. The naming below
is intentionally boring; the goal is to make a new class's dependency direction obvious from its
name and package.

## Layers

- `presentation`: HTTP controllers, request records, response records, exception advice.
- `application`: use cases, query services, application views, ports to other capabilities.
- `domain`: entities, value objects, domain repository ports.
- `infrastructure`: adapters for JPA, Redis, HTTP clients, S3, Stripe, and other SDKs.

`domain` never imports `application` or `presentation`. `application` never imports
`presentation`. SDK-specific types stay behind infrastructure or common infrastructure wrappers.

## Names

- `*UseCase`: state-changing application operation. Put it in `application.write`.
- `*QueryService`: read operation exposed to controllers. Put it in `application.read`.
- `*Repository`: domain persistence port. Implement it with an `*RepositoryAdapter` in
  `infrastructure.persistence`.
- `Jpa*Repository`: Spring Data interface only. Keep it in `infrastructure.persistence`.
- `*Reader`: read-only port or read-side helper for aggregate/query data. Use this when exposing
  a narrow query capability would make a domain repository too wide.
- `*Lookup`: small capability port for cross-context identity/access lookups.
- `*View`, `*Result`, `*Summary`: application DTOs.
- `*Request`, `*Response`, `*Page`, `*ProblemDetails`: presentation DTOs only.

## Mapping

Application services should return application DTOs (`*View`, `*Result`, `*Summary`) or domain
objects. They should not know the HTTP response record that a controller returns.

Presentation records may have static `from(...)` factories when the mapping is mechanical. If the
mapping needs policy decisions, database reads, security checks, or multiple collaborators, move it
to the application layer and return an application DTO first.

Persistence projections belong behind repositories/readers. Do not pass JPA projection interfaces
directly to controllers.
