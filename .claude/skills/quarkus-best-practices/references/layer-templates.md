# Plantillas por capa

Ejemplo con la entidad `Product`. Reemplaza `{base}` por el package base
(`com.andres.course.claude.quarkus.{segmento}.app`) y adapta nombres y campos al dominio pedido.

Contenido:
1. Model (entity JPA) — requiere persistencia
2. Repository (interface + implementación) — requiere persistencia
3. DTO (record)
4. Mapper
5. Service
6. Controller
7. Manejo de errores
8. Variante sin persistencia

---

## 1. Model — `models/Product.java`

Entidad JPA simple con campos privados y getters/setters. No extiende `PanacheEntity`:
el acceso a datos va siempre por el repositorio.

```java
package {base}.models;

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

## 2. Repository — `repositories/ProductRepository.java` + `ProductRepositoryImpl.java`

El repositorio se declara como **interface** que extiende `PanacheRepository`. Panache clásico
no genera implementaciones de interfaces (inyectar solo la interface falla en build con
`UnsatisfiedResolutionException`), así que se acompaña de una clase `@ApplicationScoped` vacía
que la implementa. Las consultas personalizadas van como métodos `default` en la interface,
y el service inyecta siempre la interface.

```java
package {base}.repositories;

import {base}.models.Product;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends PanacheRepository<Product> {

    default Optional<Product> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    default List<Product> searchByName(String term) {
        return list("lower(name) like ?1", "%" + term.toLowerCase() + "%");
    }
}
```

```java
package {base}.repositories;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductRepositoryImpl implements ProductRepository {
}
```

## 3. DTO — `models/dto/ProductDto.java`

Un solo `record` sirve como request y response. Excluye password, `created_at`, `updated_at`
(y variantes `create_at`, `update_at`, `createdAt`, `updatedAt`) y cualquier dato sensible.
`id` es `null` al crear y viene informado en la respuesta. Las validaciones van en el record.

```java
package {base}.models.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0.0") BigDecimal price) {
}
```

## 4. Mapper — `mappers/ProductMapper.java`

Conversión explícita en ambos sentidos, sin librerías de mapeo. `updateEntity` copia solo
los campos editables (nunca `id` ni fechas de auditoría).

```java
package {base}.mappers;

import {base}.models.Product;
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

## 5. Service — `services/ProductService.java`

Contiene la lógica de negocio, orquesta repositorios, maneja transacciones y convierte con el
mapper. Recibe y devuelve DTO: las entidades nunca salen del service.

```java
package {base}.services;

import {base}.mappers.ProductMapper;
import {base}.models.Product;
import {base}.models.dto.ProductDto;
import {base}.repositories.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductService(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ProductDto> findAll() {
        return repository.listAll().stream().map(mapper::toDto).toList();
    }

    public ProductDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Transactional
    public ProductDto create(ProductDto dto) {
        Product entity = mapper.toEntity(dto);
        repository.persist(entity);
        return mapper.toDto(entity);
    }

    @Transactional
    public ProductDto update(Long id, ProductDto dto) {
        Product entity = getOrThrow(id);
        mapper.updateEntity(entity, dto);
        return mapper.toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.deleteById(id)) {
            throw new NotFoundException("Product " + id + " no existe");
        }
    }

    private Product getOrThrow(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " no existe"));
    }
}
```

## 6. Controller — `controllers/ProductController.java`

Solo expone endpoints REST: valida con `@Valid`, delega al service y devuelve DTO.
Sin lógica de negocio ni acceso a repositorios. Anotaciones OpenAPI para Swagger UI.

```java
package {base}.controllers;

import {base}.models.dto.ProductDto;
import {base}.services.ProductService;
import jakarta.validation.Valid;
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
    public Response create(@Valid ProductDto dto) {
        ProductDto created = service.create(dto);
        return Response.created(URI.create("/api/products/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualiza un producto")
    public ProductDto update(@PathParam("id") Long id, @Valid ProductDto dto) {
        return service.update(id, dto);
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

## 7. Manejo de errores

Quarkus REST ya devuelve 400 para errores de `@Valid` (Hibernate Validator) y 404 para
`NotFoundException`. Si el usuario pide un formato de error uniforme, agrega en `controllers`
un `@ServerExceptionMapper` que devuelva un `record ErrorDto(int status, String message)`.

## 8. Variante sin persistencia

Si el proyecto aún no tiene persistencia y el usuario no la pidió:
- `models/Product.java` es un POJO sin anotaciones JPA.
- `repositories/ProductRepository.java` es una interface propia (`findAll`, `findById`, `save`,
  `deleteById`) con una implementación `@ApplicationScoped` en memoria
  (`ConcurrentHashMap<Long, Product>` + `AtomicLong` para ids).
- El service no usa `@Transactional`. DTO, mapper y controller quedan igual.

Así, al agregar persistencia más adelante, solo cambian model y repository.
