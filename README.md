# Quarkus + Claude Code: skill `quarkus-best-practices`

Proyecto del curso de **Quarkus con Claude Code**. Contiene una *skill* de Claude Code que genera
proyectos Quarkus desde la herramienta oficial [code.quarkus.io](https://code.quarkus.io) y
construye sus capas siguiendo siempre la misma arquitectura:
**controllers → services → repositories → models**, con DTO como `record` y un Mapper explícito.

La idea es simple: le pides a Claude en lenguaje natural *"crea una API de productos con Quarkus"*
y la skill se encarga de que el resultado respete las convenciones del curso, sin tener que
repetirlas en cada prompt.

---

## Contenido

```
.
├── CLAUDE.md                                   # Registro de skills y reglas de activación
├── README.md
└── .claude/skills/quarkus-best-practices/
    ├── SKILL.md                                # Instrucciones de la skill
    ├── references/layer-templates.md           # Plantillas de cada capa
    ├── scripts/generate_project.py             # Genera el proyecto / agrega persistencia
    └── evals/evals.json                        # Casos de prueba de la skill
```

## Requisitos

| Herramienta | Para qué |
|---|---|
| [Claude Code](https://claude.com/claude-code) | Ejecutar la skill |
| Java 25 | Compilar y ejecutar el proyecto |
| Python 3 y `curl` | Script que consulta y descarga desde code.quarkus.io |
| Docker o Podman | Solo si agregas persistencia: Dev Services levanta PostgreSQL en dev mode |

No necesitas Maven instalado: el proyecto generado incluye Maven Wrapper (`./mvnw`).

## Cómo se usa

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

## Convenciones del proyecto generado

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

### Dependencias

- **Base:** `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`.
- **Bajo demanda:** `quarkus-hibernate-orm-panache` y `quarkus-jdbc-postgresql`. Solo se agregan cuando
  las pides, por comando (`add-persistence`) o en lenguaje natural.

### Arquitectura en capas

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

## Ejecutar la aplicación

```bash
./mvnw quarkus:dev
```

Dev Mode activa Live Coding y Hot Reload: los cambios se aplican en el siguiente request, sin
reiniciar.

| URL | Qué es |
|---|---|
| http://localhost:8080/q/dev-ui | Dev UI |
| http://localhost:8080/q/swagger-ui | Swagger UI |
| http://localhost:8080/q/openapi | Especificación OpenAPI |

Para construir el jar y ejecutarlo:

```bash
./mvnw package
```

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

En producción, la conexión a PostgreSQL se configura con las variables de entorno `DB_URL`,
`DB_USERNAME` y `DB_PASSWORD`.

## Evaluación de la skill

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
