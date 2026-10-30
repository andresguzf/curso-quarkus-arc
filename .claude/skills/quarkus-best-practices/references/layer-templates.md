# Plantillas por capa

Ejemplo con la entidad `Product`. Reemplaza `{base}` por el package base
(`com.andres.course.claude.quarkus.{segmento}.app`) y adapta nombres y campos al dominio pedido.

Contenido:
1. Entity (JPA) — requiere persistencia
2. Repository (clase Panache) — requiere persistencia
3. DTO (record)
4. Mapper
5. Service (interface + implementación)
6. Controller
7. Manejo de errores (GlobalExceptionMapper + ErrorDto)
8. Variante sin persistencia

---

## 1. Entity — `entities/Product.java`

Entidad JPA simple con campos privados y getters/setters. No extiende `PanacheEntity`:
el acceso a datos va siempre por el repositorio.

```java
package {base}.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters y setters de todos los campos
}
```

## 2. Repository — `repositories/ProductRepository.java`

El repositorio es **una sola clase** `@ApplicationScoped` que implementa `PanacheRepository`.
No lleva interface aparte ni clase `Impl`: los métodos de Panache (`persist`, `findById`,
`listAll`, `deleteById`, `count`…) ya vienen como `default` y Quarkus los completa en build.
La clase existe solo para declarar el scope CDI (un bean tiene que ser una clase con anotación
de scope). Las consultas personalizadas van como métodos públicos y el service inyecta la clase.

```java
package {base}.repositories;

import {base}.entities.Product;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProductRepository implements PanacheRepository<Product> {

    public Optional<Product> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    public List<Product> searchByName(String term) {
        return list("lower(name) like ?1", "%" + term.toLowerCase() + "%");
    }
}
```

## 3. DTO — `models/dto/ProductDto.java`

Un solo `record` sirve como request y response. Excluye password, `created_at`, `updated_at`
(y variantes `create_at`, `update_at`, `createdAt`, `updatedAt`) y cualquier dato sensible.
`id` es `null` al crear y viene informado en la respuesta. Las validaciones van en el record,
cada una con un `message` simple en español: es lo que verá el usuario en `errors` (sección 7).

```java
package {base}.models.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede tener más de {max} caracteres")
        String name,
        @Size(max = 500, message = "La descripción no puede tener más de {max} caracteres")
        String description,
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
        BigDecimal price) {
}
```

## 4. Mapper — `mappers/ProductMapper.java`

Conversión explícita en ambos sentidos, sin librerías de mapeo. `updateEntity` copia solo
los campos editables (nunca `id` ni fechas de auditoría).

```java
package {base}.mappers;

import {base}.entities.Product;
import {base}.models.dto.ProductDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductMapper {

    // entity -> dto
    public ProductDto toDto(Product entity) {
        if (entity == null) {
            return null;
        }
        return new ProductDto(entity.getId(), entity.getName(), entity.getDescription(), entity.getPrice());
    }

    // dto -> entity
    public Product toEntity(ProductDto dto) {
        if (dto == null) {
            return null;
        }
        Product entity = new Product();
        updateEntity(entity, dto);
        return entity;
    }

    public void updateEntity(Product entity, ProductDto dto) {
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setPrice(dto.price());
    }
}
```

## 5. Service — `services/ProductService.java` + `ProductServiceImpl.java`

El service se divide en una **interface** (el contrato de negocio, solo con DTO en las firmas)
y una clase `@ApplicationScoped` `ProductServiceImpl` con la lógica: orquesta repositorios,
maneja transacciones y convierte con el mapper. Las entidades nunca salen del service.
Controllers y tests inyectan siempre la interface `ProductService`, nunca la `Impl`.

```java
package {base}.services;

import {base}.models.dto.ProductDto;

import java.util.List;

public interface ProductService {

    List<ProductDto> findAll();

    ProductDto findById(Long id);

    ProductDto create(ProductDto dto);

    ProductDto update(Long id, ProductDto dto);

    void delete(Long id);
}
```

```java
package {base}.services;

import {base}.mappers.ProductMapper;
import {base}.entities.Product;
import {base}.models.dto.ProductDto;
import {base}.repositories.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductServiceImpl(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<ProductDto> findAll() {
        return repository.listAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public ProductDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Override
    @Transactional
    public ProductDto create(ProductDto dto) {
        Product entity = mapper.toEntity(dto);
        repository.persist(entity);
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public ProductDto update(Long id, ProductDto dto) {
        Product entity = getOrThrow(id);
        mapper.updateEntity(entity, dto);
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.deleteById(id)) {
            throw notFound(id);
        }
    }

    private Product getOrThrow(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> notFound(id));
    }

    // Mensaje simple para el usuario final; GlobalExceptionMapper lo devuelve en el JSON de error
    private NotFoundException notFound(Long id) {
        return new NotFoundException("No encontramos un producto con el id " + id + ".");
    }
}
```

## 6. Controller — `controllers/ProductController.java`

Solo expone endpoints REST: valida con `@Valid`, delega al service (inyectando la
interface `ProductService`) y devuelve DTO.
Sin lógica de negocio ni acceso a repositorios. Anotaciones OpenAPI para Swagger UI.

```java
package {base}.controllers;

import {base}.models.dto.ProductDto;
import {base}.services.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GET
    @Operation(summary = "Lista todos los productos")
    public List<ProductDto> findAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Busca un producto por id")
    public ProductDto findById(@PathParam("id") Long id) {
        return service.findById(id);
    }

    @POST
    @Operation(summary = "Crea un producto")
    public Response create(@Valid @NotNull(message = "Envía los datos del producto") ProductDto product) {
        ProductDto created = service.create(product);
        return Response.created(URI.create("/api/products/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualiza un producto")
    public ProductDto update(@PathParam("id") Long id,
                             @Valid @NotNull(message = "Envía los datos del producto") ProductDto product) {
        return service.update(id, product);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Elimina un producto")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
```

## 7. Manejo de errores — `exceptions/GlobalExceptionMapper.java` + `models/dto/ErrorDto.java`

Se crea una sola vez por proyecto, junto con el primer recurso. Todo error de la API responde
con el mismo `ErrorDto`, con mensajes simples para el usuario final. Quarkus elige el
`@ServerExceptionMapper` cuyo tipo de excepción sea el más específico.

```java
package {base}.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Respuesta de error común a toda la API. errors solo aparece en los errores de validación:
// cada campo que falló con la lista de sus mensajes, por ejemplo {"email": ["El email es obligatorio"]}
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorDto(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, List<String>> errors) {
}
```

```java
package {base}.exceptions;

import {base}.models.dto.ErrorDto;
import com.fasterxml.jackson.core.JacksonException;
import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class GlobalExceptionMapper {

    // 400: validaciones de Bean Validation; errors agrupa los mensajes por campo
    @ServerExceptionMapper
    public Response handleValidation(ConstraintViolationException exception, UriInfo uriInfo) {
        Map<String, List<String>> errors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(GlobalExceptionMapper::fieldName, TreeMap::new,
                        Collectors.mapping(ConstraintViolation::getMessage,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        messages -> messages.stream().sorted().toList()))));
        return build(400, "Algunos datos no son válidos. Revisa los campos e inténtalo de nuevo.", uriInfo, errors);
    }

    // 400: el body no es un JSON válido o un campo tiene un tipo incorrecto
    @ServerExceptionMapper
    public Response handleInvalidJson(JacksonException exception, UriInfo uriInfo) {
        Log.debugf("JSON inválido en %s: %s", uriInfo.getPath(), exception.getOriginalMessage());
        return build(400, "El cuerpo de la solicitud no es un JSON válido. Revisa el formato e inténtalo de nuevo.",
                uriInfo, Map.of());
    }

    // Errores HTTP de los services (404, 409) y de Quarkus REST (ruta inexistente, 405...)
    @ServerExceptionMapper
    public Response handleHttpError(WebApplicationException exception, UriInfo uriInfo) {
        // Quarkus REST envuelve el error de Jackson en un 400 cuando no puede leer el body
        if (exception.getCause() instanceof JacksonException jacksonException) {
            return handleInvalidJson(jacksonException, uriInfo);
        }
        int status = exception.getResponse().getStatus();
        String message = exception.getMessage();
        // Las excepciones sin mensaje propio traen el texto técnico "HTTP 404 Not Found"
        if (message == null || message.isBlank() || message.startsWith("HTTP ")) {
            message = defaultMessage(status);
        }
        return build(status, message, uriInfo, Map.of());
    }

    // 500: el detalle queda en el log y al usuario solo le llega un mensaje simple
    @ServerExceptionMapper
    public Response handleUnexpected(Exception exception, UriInfo uriInfo) {
        Log.errorf(exception, "Error inesperado en %s", uriInfo.getPath());
        return build(500, "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.", uriInfo, Map.of());
    }

    private static Response build(int status, String message, UriInfo uriInfo, Map<String, List<String>> errors) {
        ErrorDto body = new ErrorDto(status, title(status), message, "/" + uriInfo.getPath().replaceFirst("^/", ""),
                LocalDateTime.now(), errors);
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    // El último nodo del path de la violación es el campo (create.product.name -> name)
    private static String fieldName(ConstraintViolation<?> violation) {
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            field = node.getName();
        }
        return field;
    }

    private static String title(int status) {
        return switch (status) {
            case 400 -> "Solicitud inválida";
            case 401 -> "No autenticado";
            case 403 -> "Acceso denegado";
            case 404 -> "No encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato no disponible";
            case 409 -> "Conflicto";
            case 415 -> "Formato no soportado";
            default -> status >= 500 ? "Error interno" : "Error en la solicitud";
        };
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 400 -> "La solicitud no es válida. Revisa los datos e inténtalo de nuevo.";
            case 404 -> "La ruta o el recurso que buscas no existe. Revisa la URL e inténtalo de nuevo.";
            case 405 -> "Esta operación no está permitida para esta ruta.";
            case 406 -> "No podemos responder en el formato solicitado. Usa application/json.";
            case 415 -> "El formato enviado no es compatible. Envía los datos como application/json.";
            default -> status >= 500
                    ? "Ocurrió un error inesperado. Inténtalo de nuevo más tarde."
                    : "No pudimos procesar la solicitud. Revisa los datos e inténtalo de nuevo.";
        };
    }
}
```

Ejemplo de respuesta de validación:

```json
{
  "status": 400,
  "error": "Solicitud inválida",
  "message": "Algunos datos no son válidos. Revisa los campos e inténtalo de nuevo.",
  "path": "/api/products",
  "timestamp": "2026-10-30T14:07:48.317992",
  "errors": {
    "name": ["El nombre es obligatorio"],
    "price": ["El precio no puede ser negativo"]
  }
}
```

En los tests del controller, verifica el JSON de error de cada caso (400 con `errors`, 404, 409)
y no solo el status. Ejemplo con REST Assured: `.body("errors.name", contains("El nombre es obligatorio"))`.

## 8. Variante sin persistencia

Si el proyecto aún no tiene persistencia y el usuario no la pidió:
- `entities/Product.java` es un POJO sin anotaciones JPA.
- `repositories/ProductRepository.java` es una sola clase `@ApplicationScoped` en memoria
  (`ConcurrentHashMap<Long, Product>` + `AtomicLong` para ids), sin interface ni `Impl`. Usa
  los mismos nombres de métodos que Panache (`listAll`, `findByIdOptional`, `persist`,
  `deleteById`) para que el service no cambie al pasar a persistencia.
- `ProductServiceImpl` no usa `@Transactional`. La interface del service, DTO, mapper y
  controller quedan igual.

Así, al agregar persistencia más adelante, solo cambian entity y repository.
