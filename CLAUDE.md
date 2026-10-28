# CLAUDE.md

## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `quarkus-best-practices` | `.claude/skills/quarkus-best-practices/` | Genera el proyecto Quarkus desde code.quarkus.io (Java 25, Maven, Quarkus LTS >= 3.40.1, packaging jar) y crea o modifica sus capas controllers / services / repositories / models con DTO record y Mapper. |

## Skill trigger rules

- Usa `quarkus-best-practices` cuando el usuario pida crear una API básica, un microservicio o un proyecto con Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida crear, agregar o modificar un entity, model, DTO, mapper, repository, service, controller o endpoint REST de Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida agregar persistencia (Hibernate ORM Panache, PostgreSQL), ya sea por comando (`add-persistence`) o en lenguaje natural, por ejemplo "quiero agregar Panache con Postgres". La persistencia solo se agrega bajo demanda, nunca por defecto al crear el proyecto.
- Para ejecutar la aplicación localmente usa siempre `./mvnw quarkus:dev`.
