# Plantillas de seguridad

Seguridad nativa de Quarkus para una API con usuarios en PostgreSQL: **JWT en cookie HttpOnly**,
autorización por **roles** (`user`, `admin`) y protección **CSRF** (double submit cookie).
Antes de escribir este código ejecuta `generate_project.py add-security`: agrega las extensiones,
`quarkus-test-security` y la configuración de `application.properties`.
Reemplaza `{base}` por el package base del proyecto.

Contenido:
1. Role y User (entities)
2. UserRepository
3. UserDto (password de solo escritura) y LoginDto
4. UserMapper
5. security/: PasswordHasher, TokenService y AdminSeeder
6. UserService + UserServiceImpl
7. AuthController y UserController
8. Proteger los controllers de otros recursos
9. Errores 401, 403 y rechazo CSRF
10. Variables de entorno
11. Tests

Decisiones del patrón:
- Un solo rol por usuario (enum `Role`), guardado como texto. En el JWT viaja como grupo (`user`/`admin`).
- El registro público (`POST /api/auth/register`) siempre crea el rol `user`. El primer admin lo crea
  `AdminSeeder` al arrancar con `ADMIN_EMAIL`/`ADMIN_PASSWORD`, si no existe ningún admin.
- `UserDto` es el único DTO del recurso: el password se recibe pero nunca se devuelve
  (`@JsonProperty(access = WRITE_ONLY)`). El login usa `LoginDto` porque no es un recurso.
- Reglas: email único, un admin no se elimina a sí mismo, siempre queda al menos un admin.
- En dev y test Quarkus genera en memoria el par de claves RSA del JWT (si no hay ninguna clave
  configurada para ese perfil). En prod se leen de `JWT_PRIVATE_KEY_LOCATION`/`JWT_PUBLIC_KEY_LOCATION`.

---

## 1. Entities — `entities/Role.java` y `entities/User.java`

`@RolesAllowed` solo acepta constantes, por eso `Role.Names`. La tabla es `users` porque `user` es
palabra reservada en PostgreSQL.

```java
package {base}.entities;

// Rol del usuario; group es el nombre que viaja en el claim groups del JWT y usa @RolesAllowed
public enum Role {

    USER(Names.USER),
    ADMIN(Names.ADMIN);

    private final String group;

    Role(String group) {
        this.group = group;
    }

    public String group() {
        return group;
    }

    // "admin" -> ADMIN; null si el valor no corresponde a ningún rol
    public static Role fromGroup(String group) {
        for (Role role : values()) {
            if (role.group.equals(group)) {
                return role;
            }
        }
        return null;
    }

    // Constantes para @RolesAllowed, que solo acepta valores constantes
    public static final class Names {

        public static final String USER = "user";
        public static final String ADMIN = "admin";

        private Names() {
        }
    }
}
```

```java
package {base}.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// "user" es palabra reservada en PostgreSQL, por eso la tabla se llama users
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // Hash bcrypt, nunca el password en texto plano
    @Column(nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Auditoría: se completan solos al insertar y al actualizar
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
```

## 2. Repository — `repositories/UserRepository.java`

```java
package {base}.repositories;

import {base}.entities.Role;
import {base}.entities.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public boolean existsByEmail(String email) {
        return count("email", email) > 0;
    }

    public long countByRole(Role role) {
        return count("role", role);
    }
}
```

## 3. DTO — `models/dto/UserDto.java` y `models/dto/LoginDto.java`

El grupo `OnCreate extends Default` exige el password al registrar y al crear (con
`@ConvertGroup(from = Default.class, to = UserDto.OnCreate.class)` en el controller). Al editar se
valida solo `Default`: sin password se conserva el actual. El rol es un `String` con `@Pattern`, para
que un valor inválido salga como error de validación del campo y no como JSON mal formado.

```java
package {base}.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

// password es de solo escritura: se recibe al registrar, crear o editar, pero nunca se devuelve en el JSON
public record UserDto(
        Long id,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no puede tener más de {max} caracteres")
        String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(groups = OnCreate.class, message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre {min} y {max} caracteres")
        String password,
        @Pattern(regexp = "user|admin", message = "El rol debe ser user o admin")
        String role) {

    // Validaciones de alta (registro y creación): además de las de Default, exige el password.
    // Al editar se valida solo Default: si password viene vacío se conserva el actual
    public interface OnCreate extends Default {
    }
}
```

```java
package {base}.models.dto;

import jakarta.validation.constraints.NotBlank;

// Credenciales del login; no es un recurso, por eso va aparte de UserDto
public record LoginDto(
        @NotBlank(message = "El email es obligatorio")
        String email,
        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
```

## 4. Mapper — `mappers/UserMapper.java`

Nunca copia el password: el hash lo hace el service. Al editar sin rol se conserva el actual (si no,
editar el email de un admin lo bajaría a `user`).

```java
package {base}.mappers;

import {base}.entities.Role;
import {base}.entities.User;
import {base}.models.dto.UserDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserMapper {

    // entity -> dto: nunca copia el password
    public UserDto toDto(User entity) {
        if (entity == null) {
            return null;
        }
        return new UserDto(entity.getId(), entity.getEmail(), null, entity.getRole().group());
    }

    // dto -> entity: el password lo hashea el service, no el mapper
    public User toEntity(UserDto dto) {
        if (dto == null) {
            return null;
        }
        User entity = new User();
        // Un usuario nuevo sin rol es user
        entity.setRole(Role.USER);
        updateEntity(entity, dto);
        return entity;
    }

    // Solo email y rol: nunca id, password ni fechas de auditoría. Sin rol se conserva el actual
    public void updateEntity(User entity, UserDto dto) {
        entity.setEmail(dto.email());
        Role role = Role.fromGroup(dto.role());
        if (role != null) {
            entity.setRole(role);
        }
    }
}
```

## 5. Package `security/`

```java
package {base}.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;

import java.security.GeneralSecurityException;

/**
 * Hash y verificación de passwords con bcrypt (Elytron, la librería de seguridad de Quarkus).
 */
@ApplicationScoped
public class PasswordHasher {

    // Se verifica contra este hash cuando el email no existe, para que el login tarde lo mismo exista o no
    private final String dummyHash = BcryptUtil.bcryptHash("password-ficticio-para-igualar-tiempos");

    public String hash(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    public boolean matches(String password, String hash) {
        try {
            PasswordFactory factory = PasswordFactory.getInstance(BCryptPassword.ALGORITHM_BCRYPT,
                    WildFlyElytronPasswordProvider.getInstance());
            Password stored = factory.translate(ModularCrypt.decode(hash));
            return factory.verify(stored, password.toCharArray());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // Gasta el mismo tiempo que una verificación real; el resultado se descarta
    public void simulateMatch(String password) {
        matches(password, dummyHash);
    }
}
```

```java
package {base}.security;

import {base}.models.dto.UserDto;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

/**
 * Firma el JWT de la sesión. El issuer y la clave privada salen de la configuración de smallrye-jwt
 * (en dev y test Quarkus genera el par de claves en memoria).
 */
@ApplicationScoped
public class TokenService {

    private final Duration duration;

    public TokenService(@ConfigProperty(name = "app.auth.token-duration") Duration duration) {
        this.duration = duration;
    }

    public String generate(UserDto user) {
        return Jwt.upn(user.email())
                .subject(String.valueOf(user.id()))
                .groups(Set.of(user.role()))
                .expiresIn(duration)
                .sign();
    }

    public Duration duration() {
        return duration;
    }
}
```

```java
package {base}.security;

import {base}.entities.Role;
import {base}.entities.User;
import {base}.repositories.UserRepository;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Crea el primer admin al arrancar, con ADMIN_EMAIL y ADMIN_PASSWORD (por ejemplo desde el .env),
 * solo si todavía no existe ningún admin. Sin esas variables no hace nada.
 */
@ApplicationScoped
public class AdminSeeder {

    private final UserRepository repository;
    private final PasswordHasher passwordHasher;
    private final Optional<String> email;
    private final Optional<String> password;

    public AdminSeeder(UserRepository repository, PasswordHasher passwordHasher,
                       @ConfigProperty(name = "app.auth.admin.email") Optional<String> email,
                       @ConfigProperty(name = "app.auth.admin.password") Optional<String> password) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.email = email;
        this.password = password;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (email.isEmpty() || password.isEmpty() || repository.countByRole(Role.ADMIN) > 0) {
            return;
        }
        if (repository.existsByEmail(email.get())) {
            Log.warnf("No se creó el admin inicial: el email %s ya pertenece a otro usuario", email.get());
            return;
        }
        User admin = new User();
        admin.setEmail(email.get());
        admin.setPassword(passwordHasher.hash(password.get()));
        admin.setRole(Role.ADMIN);
        repository.persist(admin);
        Log.infof("Admin inicial creado: %s", email.get());
    }
}
```

## 6. Service — `services/UserService.java` + `UserServiceImpl.java`

El login fallido responde siempre 401 con el mismo mensaje, exista o no el email, y verifica contra un
hash ficticio para que el tiempo de respuesta tampoco lo delate.

```java
package {base}.services;

import {base}.models.dto.LoginDto;
import {base}.models.dto.UserDto;

import java.util.List;

public interface UserService {

    List<UserDto> findAll();

    UserDto findById(Long id);

    UserDto findByEmail(String email);

    // Alta por un admin: respeta el rol enviado (user por defecto)
    UserDto create(UserDto dto);

    // Registro público: siempre crea el rol user
    UserDto register(UserDto dto);

    UserDto update(Long id, UserDto dto);

    // currentEmail es el admin que hace la petición: no puede eliminarse a sí mismo
    void delete(Long id, String currentEmail);

    // Devuelve el usuario si las credenciales son correctas; si no, 401 con un mensaje genérico
    UserDto authenticate(LoginDto credentials);
}
```

```java
package {base}.services;

import {base}.entities.Role;
import {base}.entities.User;
import {base}.mappers.UserMapper;
import {base}.models.dto.LoginDto;
import {base}.models.dto.UserDto;
import {base}.repositories.UserRepository;
import {base}.security.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordHasher passwordHasher;

    public UserServiceImpl(UserRepository repository, UserMapper mapper, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.mapper = mapper;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public List<UserDto> findAll() {
        return repository.listAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public UserDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Override
    public UserDto findByEmail(String email) {
        return repository.findByEmail(email)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("No encontramos un usuario con el email " + email + "."));
    }

    @Override
    @Transactional
    public UserDto create(UserDto dto) {
        if (repository.existsByEmail(dto.email())) {
            throw emailInUse(dto.email());
        }
        User entity = mapper.toEntity(dto);
        entity.setPassword(passwordHasher.hash(dto.password()));
        repository.persist(entity);
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public UserDto register(UserDto dto) {
        // En el registro público el rol lo decide la API, no quien se registra
        return create(new UserDto(null, dto.email(), dto.password(), Role.USER.group()));
    }

    @Override
    @Transactional
    public UserDto update(Long id, UserDto dto) {
        User entity = getOrThrow(id);
        repository.findByEmail(dto.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw emailInUse(dto.email());
                });
        // No se puede dejar el sistema sin admins
        if (entity.getRole() == Role.ADMIN && Role.fromGroup(dto.role()) == Role.USER
                && repository.countByRole(Role.ADMIN) == 1) {
            throw conflict("No puedes quitarle el rol admin al único administrador.");
        }
        mapper.updateEntity(entity, dto);
        // Sin password se conserva el actual
        if (dto.password() != null && !dto.password().isBlank()) {
            entity.setPassword(passwordHasher.hash(dto.password()));
        }
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, String currentEmail) {
        User entity = getOrThrow(id);
        if (entity.getEmail().equals(currentEmail)) {
            throw conflict("No puedes eliminar tu propio usuario.");
        }
        if (entity.getRole() == Role.ADMIN && repository.countByRole(Role.ADMIN) == 1) {
            throw conflict("No puedes eliminar al único administrador.");
        }
        repository.delete(entity);
    }

    @Override
    public UserDto authenticate(LoginDto credentials) {
        Optional<User> user = repository.findByEmail(credentials.email());
        if (user.isEmpty()) {
            // Mismo tiempo de respuesta que con un email existente, para no revelar qué emails están registrados
            passwordHasher.simulateMatch(credentials.password());
            throw invalidCredentials();
        }
        if (!passwordHasher.matches(credentials.password(), user.get().getPassword())) {
            throw invalidCredentials();
        }
        return mapper.toDto(user.get());
    }

    private User getOrThrow(Long id) {
        return repository.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("No encontramos un usuario con el id " + id + "."));
    }

    private ClientErrorException emailInUse(String email) {
        return conflict("Ya existe un usuario registrado con el email " + email + ".");
    }

    private ClientErrorException conflict(String message) {
        return new ClientErrorException(message, Response.Status.CONFLICT);
    }

    // Mismo mensaje si el email no existe o el password es incorrecto
    private NotAuthorizedException invalidCredentials() {
        return new NotAuthorizedException("Email o contraseña incorrectos.", Response.status(Response.Status.UNAUTHORIZED).build());
    }
}
```

## 7. Controllers — `controllers/AuthController.java` y `controllers/UserController.java`

`/api/auth/logout` lleva `@Consumes(MediaType.WILDCARD)`: no tiene body y, sin eso, la clase exigiría
`Content-Type: application/json` (415).

```java
package {base}.controllers;

import {base}.models.dto.LoginDto;
import {base}.models.dto.UserDto;
import {base}.security.TokenService;
import {base}.services.UserService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;

/**
 * Registro, login y logout. La sesión es un JWT guardado en la cookie HttpOnly access_token; las peticiones
 * que modifican datos (incluidos login y registro) deben enviar el token CSRF en la cabecera X-CSRF-TOKEN.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth")
public class AuthController {

    private final UserService service;
    private final TokenService tokenService;
    private final SecurityIdentity identity;
    private final String cookieName;
    private final boolean cookieSecure;

    public AuthController(UserService service, TokenService tokenService, SecurityIdentity identity,
                          @ConfigProperty(name = "mp.jwt.token.cookie") String cookieName,
                          @ConfigProperty(name = "app.auth.cookie-secure") boolean cookieSecure) {
        this.service = service;
        this.tokenService = tokenService;
        this.identity = identity;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    @GET
    @Path("/csrf")
    @PermitAll
    @Operation(summary = "Entrega la cookie csrf-token; su valor se envía en la cabecera X-CSRF-TOKEN")
    public Response csrf() {
        return Response.noContent().build();
    }

    @POST
    @Path("/register")
    @PermitAll
    @Operation(summary = "Registra un usuario con rol user")
    public Response register(@Valid @ConvertGroup(from = Default.class, to = UserDto.OnCreate.class)
                             @NotNull(message = "Envía los datos del usuario") UserDto user) {
        UserDto created = service.register(user);
        return Response.created(URI.create("/api/users/" + created.id())).entity(created).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Inicia sesión y deja el JWT en la cookie HttpOnly")
    public Response login(@Valid @NotNull(message = "Envía el email y la contraseña") LoginDto credentials) {
        UserDto user = service.authenticate(credentials);
        String token = tokenService.generate(user);
        return Response.ok(user).cookie(sessionCookie(token, (int) tokenService.duration().toSeconds())).build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    // Sin body: no exige Content-Type JSON
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Cierra la sesión borrando la cookie")
    public Response logout() {
        return Response.noContent().cookie(sessionCookie("", 0)).build();
    }

    @GET
    @Path("/me")
    @Authenticated
    @Operation(summary = "Devuelve el usuario de la sesión")
    public UserDto me() {
        return service.findByEmail(identity.getPrincipal().getName());
    }

    // HttpOnly: el JavaScript del navegador no puede leer el token. SameSite=Strict: el navegador no la envía
    // en peticiones iniciadas desde otros sitios. Secure en prod, para enviarla solo por HTTPS
    private NewCookie sessionCookie(String value, int maxAge) {
        return new NewCookie.Builder(cookieName)
                .value(value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }
}
```

```java
package {base}.controllers;

import {base}.entities.Role;
import {base}.models.dto.UserDto;
import {base}.services.UserService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
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

// Administración de usuarios: solo admin
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Role.Names.ADMIN)
@Tag(name = "Users")
public class UserController {

    private final UserService service;
    private final SecurityIdentity identity;

    public UserController(UserService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @GET
    @Operation(summary = "Lista todos los usuarios")
    public List<UserDto> findAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Busca un usuario por id")
    public UserDto findById(@PathParam("id") Long id) {
        return service.findById(id);
    }

    @POST
    @Operation(summary = "Crea un usuario con el rol indicado (user por defecto)")
    public Response create(@Valid @ConvertGroup(from = Default.class, to = UserDto.OnCreate.class)
                           @NotNull(message = "Envía los datos del usuario") UserDto user) {
        UserDto created = service.create(user);
        return Response.created(URI.create("/api/users/" + created.id())).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualiza un usuario; sin password se conserva el actual")
    public UserDto update(@PathParam("id") Long id,
                          @Valid @NotNull(message = "Envía los datos del usuario") UserDto user) {
        return service.update(id, user);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Elimina un usuario")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id, identity.getPrincipal().getName());
        return Response.noContent().build();
    }
}
```

## 8. Proteger los controllers de otros recursos

Con `deny-unannotated-endpoints=true`, todo endpoint necesita una anotación de seguridad. Patrón:
lectura para ambos roles en la clase y escritura solo para admin en cada método.

```java
@Path("/api/products")
@RolesAllowed({Role.Names.USER, Role.Names.ADMIN})
public class ProductController {

    @GET
    public List<ProductDto> findAll() { ... }

    @POST
    @RolesAllowed(Role.Names.ADMIN)
    public Response create(@Valid @NotNull(message = "Envía los datos del producto") ProductDto product) { ... }

    @PUT
    @Path("/{id}")
    @RolesAllowed(Role.Names.ADMIN)
    public ProductDto update(...) { ... }

    @DELETE
    @Path("/{id}")
    @RolesAllowed(Role.Names.ADMIN)
    public Response delete(...) { ... }
}
```

## 9. Errores — `GlobalExceptionMapper` y `exceptions/EmptyErrorResponseFilter.java`

Agrega estos handlers a `GlobalExceptionMapper` (sección 7 de `layer-templates.md`), con los imports
`io.quarkus.security.AuthenticationFailedException`, `ForbiddenException` y `UnauthorizedException`.
El 401 del login (`NotAuthorizedException` del service) ya lo atiende `handleHttpError`.

```java
    /**
     * 401: falta la sesión o el JWT de la cookie no es válido o expiró.
     */
    @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
    public Response handleUnauthorized(RuntimeException exception, UriInfo uriInfo) {
        return build(401, "Debes iniciar sesión para continuar.", uriInfo, Map.of());
    }

    /**
     * 403: hay sesión, pero el rol no permite la operación (por ejemplo, un user que intenta crear un cliente).
     */
    @ServerExceptionMapper
    public Response handleForbidden(ForbiddenException exception, UriInfo uriInfo) {
        return build(403, "No tienes permisos para realizar esta acción.", uriInfo, Map.of());
    }
```

`errorBody`, `title` y `defaultMessage` del mapper pasan a ser `static` de package (sin `private`)
para reutilizarlos en el filtro. `build` queda así:

```java
    private static Response build(int status, String message, UriInfo uriInfo, Map<String, List<String>> errors) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(errorBody(status, message, uriInfo.getPath(), errors)).build();
    }

    static ErrorDto errorBody(int status, String message, String path, Map<String, List<String>> errors) {
        return new ErrorDto(status, title(status), message, "/" + path.replaceFirst("^/", ""), LocalDateTime.now(), errors);
    }
```

El filtro CSRF de Quarkus corta la petición con un 400 sin body, que no pasa por el mapper. Este
filtro lo completa con el `ErrorDto`:

```java
package {base}.exceptions;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.Map;

/**
 * Completa con un ErrorDto las respuestas de error que llegan sin body y que no pasan por
 * GlobalExceptionMapper. El caso principal es el filtro CSRF de Quarkus: cuando falta la cabecera
 * X-CSRF-TOKEN o no coincide con la cookie, corta la petición con un 400 vacío.
 */
public class EmptyErrorResponseFilter {

    private static final String CSRF_MESSAGE =
            "No pudimos verificar la solicitud: falta el token CSRF o no es válido. "
                    + "Pide uno nuevo en /api/auth/csrf y envíalo en la cabecera X-CSRF-TOKEN.";

    @ServerResponseFilter
    public void fillEmptyError(ContainerRequestContext request, ContainerResponseContext response) {
        int status = response.getStatus();
        if (status < 400 || response.hasEntity()) {
            return;
        }
        String message = status == 400 ? CSRF_MESSAGE : GlobalExceptionMapper.defaultMessage(status);
        response.setEntity(GlobalExceptionMapper.errorBody(status, message, request.getUriInfo().getPath(), Map.of()),
                null, MediaType.APPLICATION_JSON_TYPE);
    }
}
```

## 10. Variables de entorno

En `.env` (no versionado) agrega `ADMIN_EMAIL` y un `ADMIN_PASSWORD` generado al azar; en
`.env.example`, solo placeholders. Para prod, `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION`.
Al comprobar si una variable ya existe, busca la línea exacta (`^ADMIN_EMAIL=`): `PGADMIN_EMAIL`
también contiene el texto `ADMIN_EMAIL`.

```properties
# ===== Seguridad =====
# Admin inicial: se crea al arrancar si no existe ningún admin
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=tu-password-admin
# prod: claves PEM para firmar y verificar el JWT (en dev y test Quarkus las genera en memoria)
JWT_PRIVATE_KEY_LOCATION=/ruta/privateKey.pem
JWT_PUBLIC_KEY_LOCATION=/ruta/publicKey.pem
```

## 11. Tests

- Controllers: `@TestSecurity(user = "admin@ejemplo.com", roles = "admin")` en la clase y un
  `@TestSecurity` con `roles = "user"` en los métodos que prueban el 403. Todas las peticiones pasan
  por `Csrf.given()`.
- Service: `@QuarkusTest` + `@TestTransaction`; verifica el hash (`startsWith("$2")`), el login y las
  reglas del último admin.
- Flujo real sin `@TestSecurity`: registro, login (cookie `HttpOnly` y `SameSite=Strict` con
  `extract().detailedCookie("access_token")`), `GET` 200 y `POST` 403 como `user`, 400 sin token
  CSRF, 401 sin sesión y logout con `Max-Age=0`. Limpia `users` en `@BeforeEach`/`@AfterEach`.

Helper — `src/test/java/{base}/controllers/Csrf.java`:

```java
package {base}.controllers;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

// Pide un token CSRF y lo envía como cookie y en la cabecera X-CSRF-TOKEN, como lo haría el frontend
final class Csrf {

    static final String COOKIE = "csrf-token";
    static final String HEADER = "X-CSRF-TOKEN";

    private Csrf() {
    }

    static String token() {
        return RestAssured.given().when().get("/api/auth/csrf")
                .then().statusCode(204)
                .extract().cookie(COOKIE);
    }

    static RequestSpecification given() {
        String token = token();
        return RestAssured.given().cookie(COOKIE, token).header(HEADER, token);
    }
}
```
