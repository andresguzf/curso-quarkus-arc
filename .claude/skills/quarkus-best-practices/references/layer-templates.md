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
7. Manejo de errores
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

Solo expone endpoints REST: valida con `@Valid`, delega al service (inyectando la
interface `ProductService`) y devuelve DTO.
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
- `entities/Product.java` es un POJO sin anotaciones JPA.
- `repositories/ProductRepository.java` es una sola clase `@ApplicationScoped` en memoria
  (`ConcurrentHashMap<Long, Product>` + `AtomicLong` para ids), sin interface ni `Impl`. Usa
  los mismos nombres de métodos que Panache (`listAll`, `findByIdOptional`, `persist`,
  `deleteById`) para que el service no cambie al pasar a persistencia.
- `ProductServiceImpl` no usa `@Transactional`. La interface del service, DTO, mapper y
  controller quedan igual.

Así, al agregar persistencia más adelante, solo cambian entity y repository.
