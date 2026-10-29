# CLAUDE.md

## Proyecto

`2-quarkus-arc` es un proyecto del curso de Quarkus. Su tema principal es **CDI con ArC**, el contenedor de inyección de dependencias de Quarkus. Usa una factura (`Invoice`) con su cliente (`Customer`) y sus ítems (`Item`) para mostrar:

- Inyección por constructor y por campo, con `@ConfigProperty` y `@ConfigMapping` (`InvoiceItemsConfig`).
- Productores (`@Produces` en `ItemsProducer`) de `List<Item>` desde tres fuentes: `application.properties`, `items.json` y `items.csv`.
- Qualifiers propios (`@ItemsJson`, `@ItemsFile`) para elegir entre beans del mismo tipo.
- Scopes y contextos (`@ApplicationScoped`, `@SessionScoped`, `@Singleton`, que es el scope por defecto de los controllers) y client proxies.
- Callbacks de ciclo de vida `@PostConstruct` y `@PreDestroy`.
  - `Invoice.init()` concatena un sufijo al nombre del `Customer` compartido, así se ve cuántas instancias se crean.
  - `InvoiceController` registra en el log su propio scope con `Bean<T>`.

Es un proyecto didáctico, así que no sigue la arquitectura completa de la skill. No hay services, repositories, DTO ni persistencia: los `models` son beans CDI y los controllers los inyectan directo. No agregues capas salvo que el usuario lo pida.

## Stack y dependencias

- Java 25, Maven (`./mvnw`), Quarkus 3.40.1.1 (LTS), packaging `jar`.
- `quarkus-rest-jackson` (Quarkus REST), `quarkus-arc`, `quarkus-hibernate-validator` y `quarkus-smallrye-openapi` (Swagger UI).
- `quarkus-undertow` y `quarkus-rest-servlet` son necesarias para `@SessionScoped`:
  - Undertow aporta el contexto de sesión.
  - `quarkus-rest-servlet` hace que Quarkus REST atienda por servlet, para que ese contexto se active.
  - Sin ellas, un bean `@SessionScoped` lanza `ContextNotActiveException`.
  - También permiten inyectar `HttpServletRequest` en `LogoutController`.
- Tests: `quarkus-junit` y `rest-assured`.

## Estructura

Package base: `com.andres.course.claude.quarkus.quarkusarc.app`.

- `controllers/`: `InvoiceController`, `LogoutController`, `HealthController`.
- `models/`:
  - `Invoice` (`@ApplicationScoped`; se puede cambiar a `@SessionScoped` para el ejemplo de sesión).
  - `Customer` (`@ApplicationScoped`).
  - `Item`.
- `config/`: `ItemsProducer`, `InvoiceItemsConfig` y los qualifiers `ItemsJson` e `ItemsFile`.
- `filters/`: `RequestTimeFilter`, filtro global de Quarkus REST con `@ServerRequestFilter(preMatching = true)` (pre: guarda el inicio) y `@ServerResponseFilter` (post: escribe en el log el método, la ruta, el status y los ms, y agrega `elapsedTime` al JSON de respuesta convirtiendo la entidad a `ObjectNode` con el `ObjectMapper`; solo si la respuesta es un objeto JSON).
- `src/main/resources/`: `application.properties` (datos de cliente, factura e ítems), `items.json` e `items.csv`.

## Rutas

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/invoices` | Devuelve la factura con su cliente e ítems (los inyectados con `@ItemsFile`) |
| POST | `/api/logout` | Invalida la sesión HTTP; con `Invoice` en `@SessionScoped` dispara su `@PreDestroy` |
| GET | `/api/health` | Estado de la aplicación |
| GET | `/q/swagger-ui` | Swagger UI |
| GET | `/q/openapi` | Documento OpenAPI |
| GET | `/q/dev-ui` | Dev UI (solo en dev mode) |

## Ejecutar y probar

- Dev mode: `./mvnw quarkus:dev`, en `http://localhost:8080`.
- Tests: `./mvnw test` (`InvoiceControllerTest`). `InvoiceControllerIT` repite esos tests contra el jar.
- Si cambias el scope de `Invoice`, ajusta `InvoiceControllerTest`: los tests de sesión dependen de la cookie `JSESSIONID`.

## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `quarkus-best-practices` | `.claude/skills/quarkus-best-practices/` | Genera el proyecto Quarkus desde code.quarkus.io (Java 25, Maven, Quarkus LTS >= 3.40.1, packaging jar) y crea o modifica sus capas controllers / services / repositories / models con DTO record y Mapper. |

## Skill trigger rules

- Usa `quarkus-best-practices` cuando el usuario pida crear una API básica, un microservicio o un proyecto con Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida crear, agregar o modificar un entity, model, DTO, mapper, repository, service, controller o endpoint REST de Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida agregar persistencia (Hibernate ORM Panache, PostgreSQL), ya sea por comando (`add-persistence`) o en lenguaje natural, por ejemplo "quiero agregar Panache con Postgres". La persistencia solo se agrega bajo demanda, nunca por defecto al crear el proyecto.
- Para ejecutar la aplicación localmente usa siempre `./mvnw quarkus:dev`.
