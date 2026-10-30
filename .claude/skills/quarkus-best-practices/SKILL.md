---
name: quarkus-best-practices
description: Genera y mantiene proyectos Quarkus (Java 25, Maven, Quarkus LTS >= 3.40.1) con arquitectura en capas controllers / services / repositories / entities, DTO como records y Mapper explícito. Usa esta skill siempre que el usuario pida crear una API básica, un microservicio o un proyecto con Quarkus, y también cuando pida crear, agregar o modificar un entity, model, DTO, mapper, repository, service, controller o endpoint REST en un proyecto Quarkus, o agregar persistencia a uno (por ejemplo "quiero agregar Panache con Postgres", "conecta la API a PostgreSQL"), aunque no mencione la skill por nombre.
---

# Quarkus best practices

Skill para generar un proyecto Quarkus desde la herramienta oficial (code.quarkus.io) y para
crear o modificar sus capas siguiendo siempre la misma arquitectura. El objetivo es que todos
los proyectos del curso queden iguales: mismas coordenadas Maven, mismos packages y la misma
separación de responsabilidades, para que cualquier cambio posterior sea predecible.

## Convenciones del proyecto

| Elemento | Valor |
|---|---|
| Generador | code.quarkus.io (API oficial), vía `scripts/generate_project.py` |
| Quarkus | Stream LTS FINAL más reciente >= 3.40.1 (el script lo consulta en `/api/streams`) |
| Java | 25 |
| Build | Maven + Maven Wrapper (`.mvn/`, `./mvnw`, `./mvnw.cmd`) |
| Packaging | `jar` (fast-jar en `target/quarkus-app/`) |
| Configuración | `src/main/resources/application.properties` (nunca YAML) |
| groupId | `com.andres.course.claude.quarkus` |
| artifactId | nombre del directorio workspace |
| Package base | `com.andres.course.claude.quarkus.{segmento}.app` |
| Extensiones base | `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-hibernate-validator`, `quarkus-smallrye-openapi` |
| Persistencia (bajo demanda) | `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql` |

`{segmento}` es el artifactId convertido en un segmento de package válido: minúsculas, sin
guiones ni símbolos y sin dígitos iniciales (Java no permite que un segmento empiece con
número). Ejemplo: `2-quarkus-arc` → `quarkusarc` → `com.andres.course.claude.quarkus.quarkusarc.app`.

## Flujo 1: crear el proyecto

Úsalo cuando el usuario pide crear una API o proyecto Quarkus y aún no hay `pom.xml`.

1. Ejecuta primero en modo simulación y muestra al usuario las coordenadas y la versión
   de Quarkus que se usarán:
   ```bash
   python3 .claude/skills/quarkus-best-practices/scripts/generate_project.py --dry-run
   ```
2. Genera el proyecto en el workspace actual:
   ```bash
   python3 .claude/skills/quarkus-best-practices/scripts/generate_project.py
   ```
   El script descarga desde code.quarkus.io solo las extensiones base, cambia
   `<packaging>quarkus</packaging>` a `jar` (agregando las executions del `quarkus-maven-plugin`,
   necesarias porque el lifecycle `jar` no las enlaza solo), asegura `quarkus-arc` explícito
   (code.quarkus.io no lo acepta como extensión seleccionable), escribe `application.properties`
   y crea los packages base. No sobrescribe `CLAUDE.md`, `.claude/` ni archivos existentes
   (el registro de la skill en `CLAUDE.md` se hace después, ver "Registro en CLAUDE.md").
3. **No agregues persistencia** salvo que el usuario la haya pedido explícitamente en esa
   misma solicitud (base de datos, PostgreSQL, JPA, Hibernate, Panache, "persistir"...).
   El proyecto base debe quedar liviano; la persistencia se suma después con el Flujo 2.
4. Verifica que compila: `./mvnw -q -B package -DskipTests`.
5. Si el script no puede llegar a code.quarkus.io, informa el error al usuario; no armes el
   proyecto a mano, porque la consigna es usar la herramienta oficial.

Estructura resultante:

```
src/main/java/com/andres/course/claude/quarkus/{segmento}/app/
├── controllers/     # endpoints REST
├── services/        # lógica de negocio
├── repositories/    # acceso a datos (Panache)
├── entities/        # entidades JPA
├── models/
│   └── dto/         # records request/response
└── mappers/         # conversión entity <-> dto
```

## Flujo 2: agregar persistencia (solo bajo demanda)

Se activa cuando el usuario la pide, ya sea por comando o en lenguaje natural. Ejemplos:
"quiero agregar Panache con Postgres", "agrega persistencia", "conecta la API a PostgreSQL",
"necesito guardar los productos en base de datos", "add-persistence". En cualquiera de esos
casos, en ese momento ejecuta:

```bash
python3 .claude/skills/quarkus-best-practices/scripts/generate_project.py add-persistence
```

Agrega `quarkus-hibernate-orm-panache` y `quarkus-jdbc-postgresql` con
`./mvnw quarkus:add-extension` y anexa al `application.properties` la configuración de
datasource PostgreSQL y Hibernate ORM (`drop-and-create` en dev/test, `validate` en prod,
credenciales de prod por variables de entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
En dev mode, Dev Services levanta PostgreSQL con Docker/Podman si no hay URL configurada;
avísale al usuario que necesita Docker o Podman corriendo.

Si el usuario pide un entity o repository y el proyecto no tiene persistencia, pregúntale
si quiere agregar Panache + PostgreSQL o trabajar con un repositorio en memoria
(ver la variante sin persistencia en `references/layer-templates.md`). No la agregues por tu cuenta.

## Flujo 3: crear o modificar capas

Lee `references/layer-templates.md` antes de escribir código: tiene la plantilla completa
de cada capa. Para un recurso nuevo crea, en este orden: entity → repository → DTO → mapper
→ service → controller. Al modificar un campo, propaga el cambio por todas las capas
afectadas (entity, DTO, mapper y validaciones).

Responsabilidades de cada capa (respétalas aunque el usuario pida "algo rápido": mezclar
capas es lo que hace que el proyecto deje de ser mantenible):

- **controllers** — solo exponen endpoints REST (`@Path`, JAX-RS de Quarkus REST). Validan la
  entrada con `@Valid`, delegan al service y devuelven DTO. Nunca acceden a repositorios ni
  devuelven entidades.
- **services** — interface `XxxService` con las operaciones (solo DTO en las firmas) más una
  clase `@ApplicationScoped` `XxxServiceImpl implements XxxService` con la lógica de negocio:
  orquesta repositorios, define `@Transactional` en las escrituras y convierte entre entidades
  y DTO usando el mapper. Controllers y tests inyectan siempre la interface, nunca la `Impl`.
  (A diferencia del repositorio, aquí la interface sí aporta: es el contrato de negocio y
  desacopla al controller de la implementación concreta.)
- **repositories** — una sola clase `@ApplicationScoped` `XxxRepository implements
  PanacheRepository<Entidad>`, con las consultas personalizadas como métodos públicos. Sin
  interface aparte ni clase `XxxRepositoryImpl`: los métodos de Panache (`persist`, `findById`,
  `list`, `find`…) ya vienen como `default` y Quarkus los completa en build; la clase solo
  existe para declarar el scope CDI, porque un bean tiene que ser una clase con anotación de
  scope. El service inyecta la clase.
- **entities** — entidades JPA (`@Entity`) con campos privados, getters/setters e `id` `Long`
  autogenerado. No extienden `PanacheEntity`; el acceso a datos va por el repositorio. Las
  fechas de auditoría (`createdAt`, `updatedAt`) se llenan con `@PrePersist`/`@PreUpdate` y
  solo tienen getter.
- **models/dto** — `record`, un único DTO por recurso que sirve como request y response.
  Excluye password, `created_at`, `updated_at`, `create_at`, `update_at` (y sus variantes
  camelCase) y cualquier dato sensible. Las anotaciones de Bean Validation van en el record.
  Mantén esta regla aunque el entity tenga un campo sensible que la API debería recibir
  (por ejemplo `password` en un usuario o cliente): no crees un DTO de entrada aparte. En su
  lugar, deja la columna nullable para que el alta funcione y avisa al usuario de forma
  explícita en el resumen final que ese campo no se puede enviar por la API con el DTO actual,
  para que decida cómo manejarlo.
- **mappers** — clase `@ApplicationScoped` `XxxMapper` con `toDto(entity)` (entity → dto),
  `toEntity(dto)` (dto → entity) y `updateEntity(entity, dto)`. Conversión explícita, sin
  MapStruct ni reflexión, para que se vea qué campos viajan.

Usa inyección por constructor en services y controllers.

## Ejecutar la aplicación

El comando por defecto para levantar la app localmente es siempre:

```bash
./mvnw quarkus:dev
```

Dev Mode habilita Live Coding y Hot Reload (los cambios se aplican en el siguiente request)
y la Dev UI en `http://localhost:8080/q/dev-ui`. Swagger UI queda en
`http://localhost:8080/q/swagger-ui`. Prefiérelo sobre `java -jar` o `mvn` global; usa
`./mvnw package` solo para construir el jar (`java -jar target/quarkus-app/quarkus-run.jar`).

## Registro en CLAUDE.md

Cada vez que uses esta skill en un proyecto, revisa el `CLAUDE.md` de la raíz. Si no tiene las
secciones `## Available skills` y `## Skill trigger rules` con `quarkus-best-practices`,
agrégalas al final con este contenido (crea el `CLAUDE.md` si no existe). Si ya están, no las
dupliques: solo actualízalas si quedaron desalineadas con las convenciones de esta skill. No
toques el resto del archivo.

```markdown
## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `quarkus-best-practices` | `.claude/skills/quarkus-best-practices/` | Genera el proyecto desde code.quarkus.io (Java 25, Maven, Quarkus LTS >= 3.40.1, jar) y crea o modifica sus capas: controllers, services (interface + Impl), repositories Panache, entities, DTO record y mappers. Agrega Panache + PostgreSQL bajo demanda (`add-persistence`). |

## Skill trigger rules

- Usa `quarkus-best-practices` cuando el usuario pida crear una API básica, un microservicio o un proyecto con Quarkus.
- Usa `quarkus-best-practices` cuando el usuario pida crear, agregar o modificar un entity, DTO, mapper, repository, service, controller o endpoint REST.
- Usa `quarkus-best-practices` cuando el usuario pida agregar persistencia (Hibernate ORM Panache, PostgreSQL), por comando (`add-persistence`) o en lenguaje natural ("quiero agregar Panache con Postgres"). La persistencia solo se agrega bajo demanda.
- Antes de escribir una capa, lee `references/layer-templates.md` del skill.
- Para ejecutar la aplicación localmente usa siempre `./mvnw quarkus:dev`.
```

Si `.claude/skills/quarkus-best-practices` es un symlink, agrega bajo la tabla una línea que
indique a dónde apunta y que los cambios al skill se hacen solo en esa fuente original.

## Al terminar

Resume al usuario qué archivos creaste o cambiaste, la versión de Quarkus usada y cómo
probar los endpoints (Swagger UI o `curl`). Si algún campo sensible quedó fuera del DTO y la
API no puede recibirlo, inclúyelo como aviso en el resumen.
