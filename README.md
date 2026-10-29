# Quarkus ArC: inyección de dependencias, scopes y ciclo de vida

Proyecto del curso de **Quarkus con Claude Code**. El tema principal es **CDI con ArC**, el contenedor
de inyección de dependencias de Quarkus. El ejemplo es una factura (`Invoice`) con su cliente
(`Customer`) y sus ítems (`Item`), expuesta por una API REST.

El repositorio incluye además la *skill* de Claude Code `quarkus-best-practices`, con la que se generó
el proyecto (ver [más abajo](#skill-quarkus-best-practices)).

---

## Qué muestra

| Concepto | Dónde verlo |
|---|---|
| Inyección por constructor y por campo | `InvoiceController`, `Invoice`, `Customer` |
| Configuración con `@ConfigProperty` y `@ConfigMapping` | `Customer`, `Invoice`, `InvoiceItemsConfig` |
| Productores `@Produces` de `List<Item>` desde `application.properties`, `items.json` e `items.csv` | `ItemsProducer` |
| Qualifiers propios para elegir entre beans del mismo tipo | `@ItemsJson`, `@ItemsFile` |
| Scopes y client proxies (`@ApplicationScoped`, `@SessionScoped`, `@Singleton`) | `Invoice`, `Customer`, `InvoiceController` |
| Ciclo de vida con `@PostConstruct` y `@PreDestroy` | `Invoice`, `InvoiceController` |
| Invalidar la sesión HTTP para destruir un bean `@SessionScoped` | `LogoutController` |
| Filtro HTTP global de Quarkus REST (pre y post) que mide el tiempo de cada petición y lo agrega al JSON como `elapsedTime` | `RequestTimeFilter` |

Dos detalles para observar en el log:

- `Invoice.init()` (`@PostConstruct`) concatena ` (PostConstruct)` al nombre del `Customer`. Como
  `Customer` es `@ApplicationScoped` y compartido, el nombre crece con cada nueva instancia de factura.
  Con `Invoice` en `@SessionScoped`, crece con cada sesión nueva.
- `InvoiceController` inyecta `Bean<InvoiceController>` y escribe su propio scope en el log. Un
  controller de Quarkus REST sin scope explícito es `@Singleton`.

## Rutas

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/invoices` | Devuelve la factura con su cliente, ítems y monto total |
| POST | `/api/logout` | Invalida la sesión HTTP; con `Invoice` en `@SessionScoped` dispara su `@PreDestroy` |
| GET | `/api/health` | Estado de la aplicación |
| * | cualquier otra ruta | 404 con JSON (`NotFoundController`) |
| GET | `/q/swagger-ui` | Swagger UI |
| GET | `/q/openapi` | Especificación OpenAPI |
| GET | `/q/dev-ui` | Dev UI (solo en dev mode) |

## Stack y dependencias

- Java 25, Maven Wrapper (`./mvnw`), Quarkus 3.40.1.1 (LTS), packaging `jar`.
- `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-hibernate-validator` y `quarkus-smallrye-openapi`.
- `quarkus-undertow` y `quarkus-rest-servlet`, necesarias para `@SessionScoped`:
  - Undertow aporta el contexto de sesión.
  - `quarkus-rest-servlet` hace que Quarkus REST atienda por servlet, para que ese contexto se active.
  - Sin ellas, un bean `@SessionScoped` lanza `ContextNotActiveException`.
- Tests: `quarkus-junit` y `rest-assured`.

## Estructura

```
src/main/java/com/andres/course/claude/quarkus/quarkusarc/app/
├── controllers/   # InvoiceController, LogoutController, HealthController, NotFoundController
├── models/        # Invoice, Customer, Item (beans CDI)
├── config/        # ItemsProducer, InvoiceItemsConfig y los qualifiers ItemsJson / ItemsFile
└── filters/       # RequestTimeFilter (tiempo de cada petición en el log y en el JSON)
src/main/resources/
├── application.properties   # Datos del cliente, la factura y sus ítems
├── items.json
└── items.csv
```

Es un proyecto didáctico, así que no usa services, repositories, DTO ni persistencia: los `models` son
beans CDI que los controllers inyectan directamente.

## Ejecutar y probar

```bash
./mvnw quarkus:dev
```

Dev Mode activa Live Coding y Hot Reload: los cambios se aplican en el siguiente request, sin
reiniciar. La aplicación queda en http://localhost:8080.

Para ver la sesión en acción, guarda la cookie entre peticiones:

```bash
curl -c cookies.txt -b cookies.txt http://localhost:8080/api/invoices
```

```bash
curl -X POST -c cookies.txt -b cookies.txt http://localhost:8080/api/logout
```

Tests:

```bash
./mvnw test
```

Para construir el jar y ejecutarlo:

```bash
./mvnw package
```

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Skill `quarkus-best-practices`

La skill genera proyectos Quarkus desde la herramienta oficial [code.quarkus.io](https://code.quarkus.io)
y construye sus capas siguiendo siempre la misma arquitectura:
**controllers → services → repositories → models**, con DTO como `record` y un Mapper explícito.
Le pides a Claude en lenguaje natural *"crea una API de productos con Quarkus"*, y la skill se encarga
de que el resultado respete las convenciones del curso, sin tener que repetirlas en cada prompt.

### Contenido

```
.
└── .claude/skills/quarkus-best-practices/
    ├── SKILL.md                                # Instrucciones de la skill
    ├── references/layer-templates.md           # Plantillas de cada capa
    ├── scripts/generate_project.py             # Genera el proyecto / agrega persistencia
    └── evals/evals.json                        # Casos de prueba de la skill
```

### Requisitos

| Herramienta | Para qué |
|---|---|
| [Claude Code](https://claude.com/claude-code) | Ejecutar la skill |
| Java 25 | Compilar y ejecutar el proyecto |
| Python 3 y `curl` | Script que consulta y descarga desde code.quarkus.io |
| Docker o Podman | Solo si agregas persistencia: Dev Services levanta PostgreSQL en dev mode |

No necesitas Maven instalado: el proyecto generado incluye Maven Wrapper (`./mvnw`).

### Cómo se usa

Abre Claude Code en este directorio y pídele lo que necesites en lenguaje natural.
La skill se activa sola según las reglas de [CLAUDE.md](CLAUDE.md).

| Lo que pides | Lo que hace la skill |
|---|---|
| *"Crea una API básica con Quarkus para gestionar productos"* | Genera el proyecto desde code.quarkus.io y crea las capas del recurso |
| *"Quiero agregar Panache con Postgres"* | Agrega Hibernate ORM Panache + PostgreSQL y su configuración |
| *"Agrega un entity Pedido con su repository, service y controller"* | Crea o modifica el recurso en todas las capas |
| *"Agrega el campo stock a Producto"* | Propaga el cambio por entity, DTO, mapper y validaciones |

También puedes usar el script directamente:

```bash
python3 .claude/skills/quarkus-best-practices/scripts/generate_project.py --dry-run
```

```bash
python3 .claude/skills/quarkus-best-practices/scripts/generate_project.py
```

```bash
python3 .claude/skills/quarkus-best-practices/scripts/generate_project.py add-persistence
```

### Convenciones del proyecto generado

| Elemento | Valor |
|---|---|
| Quarkus | Stream LTS más reciente >= 3.40.1 (consultado en code.quarkus.io) |
| Java | 25 |
| Build | Maven + Maven Wrapper (`.mvn/`, `./mvnw`, `./mvnw.cmd`) |
| Packaging | `jar` |
| Configuración | `application.properties` |
| groupId | `com.andres.course.claude.quarkus` |
| artifactId | Nombre del directorio del proyecto |
| Package base | `com.andres.course.claude.quarkus.{artifactId}.app` |

El artifactId se normaliza para que sea un package Java válido (sin guiones ni dígitos
iniciales). Por ejemplo, `2-quarkus-arc` queda como `com.andres.course.claude.quarkus.quarkusarc.app`.

#### Dependencias

- **Base:** `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`.
- **Bajo demanda:** `quarkus-hibernate-orm-panache` y `quarkus-jdbc-postgresql`. Solo se agregan cuando
  las pides, por comando (`add-persistence`) o en lenguaje natural.

#### Arquitectura en capas

```
src/main/java/com/andres/course/claude/quarkus/{artifactId}/app/
├── controllers/     # Endpoints REST: validan con @Valid, delegan al service y devuelven DTO
├── services/        # Lógica de negocio, transacciones y conversión entity <-> DTO
├── repositories/    # Interfaces que extienden PanacheRepository + implementación @ApplicationScoped
├── models/          # Entidades JPA
│   └── dto/         # Records: un único DTO para request y response, sin campos sensibles
└── mappers/         # Conversión explícita: toDto(entity) y toEntity(dto)
```

Algunas decisiones importantes:

- **Repositorios como interface + implementación.** Panache clásico no implementa interfaces por sí
  solo; si inyectas solo la interface, el build falla. Por eso cada `XxxRepository` va acompañado de
  una clase `XxxRepositoryImpl` vacía con `@ApplicationScoped`.
- **DTO sin datos sensibles.** Los records excluyen `password`, `created_at`, `updated_at` y similares.
- **Mapper explícito**, sin MapStruct ni reflexión, para que se vea qué campos viajan entre capas.

### Evaluación de la skill

La skill se probó con 3 casos (ver [evals.json](.claude/skills/quarkus-best-practices/evals/evals.json)),
comparando a Claude con la skill y sin ella:

1. Crear una API básica de productos (sin persistencia).
2. Crear una API de clientes con Panache y Postgres.
3. Agregar un entity `Pedido` con todas sus capas a un proyecto existente.

| Configuración | Comprobaciones cumplidas |
|---|---|
| Con skill | 100 % |
| Sin skill | 49 % |

Sin la skill, Claude genera proyectos que funcionan, pero con su propio groupId y package
(`org.acme`, `com.example`), con estructuras distintas en cada caso (a veces sin capa de service ni
repository), DTO de request y response separados, sin Mapper y agregando persistencia aunque no se
haya solicitado.
