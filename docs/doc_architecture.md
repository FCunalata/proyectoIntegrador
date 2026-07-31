# Arquitectura del repositorio — guía canónica para agentes AI

> Fuente de esta guía: `CLAUDE.md` (raíz del repo) y lectura directa del código en `config-server/`, `auth-service/`, `api-gateway/` y `frontend/`. No contiene nada que no esté ya en esos dos lugares.

## 1. Propósito

Este documento existe para que un agente AI que edite este repositorio pueda decidir, sin tener que releer todo el código:

- **Dónde va un archivo nuevo** (ver sección 5, "Reglas de ubicación de archivos").
- **Cómo se estructura un módulo nuevo o una funcionalidad nueva** dentro de `auth-service` (única capa con lógica de negocio) siguiendo el mismo patrón `controller → service → repository/entity` con DTOs de por medio (ver secciones 3 y 4).
- **Qué contratos no se deben romper** sin que sea un cambio explícito y deliberado: forma de las respuestas JSON (sección 7), el formato de error unificado (sección 8), y el flujo de configuración centralizada vía `config-server` (sección 9).

Para tareas específicas, remitir a:
- **`CLAUDE.md`** (raíz del repo) — comandos de arranque, orden de los servicios, problemas conocidos (conflictos de puerto, el bug de `apiRequest()` con el orden de merge de headers, por qué existe la validación de caracteres en usuario/rol).
- **`README.md`** (raíz del repo) — guía paso a paso para levantar el entorno completo (Docker, Maven, frontend) y probar el flujo end-to-end.

## 2. Estructura del Proyecto

```
loggin/
├── pom.xml                     # POM padre: agrega los 3 módulos, fija Java 21 / Spring Boot 3.3.4 / Spring Cloud 2023.0.3
├── docker-compose.yml          # Único servicio: mysql-db (MySQL 8.0, db app_db, expuesta en 3306)
├── README.md                   # Guía de arranque paso a paso
├── CLAUDE.md                   # Guía operativa para agentes (comandos, arquitectura, gotchas)
├── docs/
│   └── doc_architecture.md     # Este documento
│
├── config-server/               # Spring Cloud Config Server (modo native), puerto 8888
│   └── src/main/
│       ├── java/.../ConfigServerApplication.java   # única clase: @EnableConfigServer
│       └── resources/
│           ├── application.yml                     # server.port, profile "native", search-locations
│           └── config/                             # *** config real de auth-service y api-gateway vive aquí ***
│               ├── auth-service.yml                # server.port, datasource, jwt secret, management endpoints
│               └── api-gateway.yml                  # server.port, CORS global, rutas del gateway
│
├── auth-service/                 # Único servicio con lógica de negocio. Puerto 8081.
│   └── src/main/java/com/equipoft4/authservice/
│       ├── AuthServiceApplication.java
│       ├── config/               # Beans de arranque/infraestructura (no lógica de negocio)
│       │   ├── DataSeeder.java   # CommandLineRunner: siembra roles/permisos base al arrancar
│       │   └── SecurityConfig.java  # SecurityFilterChain, reglas de autorización por ruta
│       ├── controller/            # Capa de transporte HTTP (@RestController)
│       │   ├── AuthController.java   # /api/auth/**
│       │   └── RolController.java    # /api/admin/**
│       ├── dto/                   # Records de entrada/salida (contratos de API)
│       ├── entity/                 # @Entity JPA (Usuario, Rol, Permiso)
│       ├── exception/              # ApiException + GlobalExceptionHandler (@RestControllerAdvice)
│       ├── repository/             # Interfaces JpaRepository (Usuario, Rol, Permiso)
│       ├── security/                # JWT + filtro de autenticación + handlers de error de seguridad
│       │   ├── JwtService.java             # generar/parsear/validar JWT
│       │   ├── JwtAuthFilter.java           # OncePerRequestFilter: lee Authorization header
│       │   ├── UserDetailsServiceImpl.java  # puente Usuario (entity) → UserDetails (Spring Security)
│       │   ├── JsonAuthEntryPoint.java       # 401 en JSON (no autenticado / token inválido)
│       │   └── JsonAccessDeniedHandler.java  # 403 en JSON (autenticado, rol insuficiente)
│       └── service/                 # Lógica de negocio
│           ├── AuthService.java     # registro, login, perfil
│           └── RolService.java      # CRUD de roles, asignar/quitar rol a usuario, listar usuarios
│
├── api-gateway/                  # Spring Cloud Gateway (WebFlux/reactive). Puerto 8080.
│   └── src/main/java/com/equipoft4/apigateway/
│       └── ApiGatewayApplication.java   # única clase; el enrutamiento y CORS están en config-server/.../api-gateway.yml, no en código Java
│
└── frontend/                      # HTML5/CSS3/JS estático, sin build tool ni framework
    ├── index.html                 # Home pública
    ├── login.html                 # Login
    ├── registro.html              # Alta de usuario (rol USUARIO por defecto)
    ├── perfil.html                 # Perfil del usuario autenticado
    ├── admin-roles.html            # Panel ADMIN: crear roles, asignar/cambiar rol de usuario
    ├── css/styles.css
    └── js/app.js                   # apiRequest(), escapeHtml(), validadores de formulario, toasts — compartido por todas las páginas
```

**Esta estructura es la fuente de verdad para decidir dónde va cada archivo nuevo.** Si una carpeta no aparece en este listado, no crearla sin primero verificar que ninguna carpeta existente ya cumple ese rol.

## 3. Flujos Principales

### 3.1 Registro de usuario
**Entrada:** `POST /api/auth/register` → `AuthController.registrar()` (`auth-service/.../controller/AuthController.java`)
Flujo: `AuthController.registrar` recibe `RegistroRequestDTO` (ya validado por Bean Validation) → `AuthService.registrar()` (`service/AuthService.java`) verifica unicidad de `nombreUsuario`/`email` contra `UsuarioRepository`, busca o crea el rol `USUARIO` (constante `ROL_POR_DEFECTO`) vía `RolRepository`, hashea el password con `PasswordEncoder`, persiste `Usuario` (entity) con ese rol asignado → responde `UsuarioResponseDTO` con `HttpStatus.CREATED`.

### 3.2 Login
**Entrada:** `POST /api/auth/login` → `AuthController.login()`
Flujo: `AuthService.login()` delega la verificación de credenciales a `AuthenticationManager.authenticate(...)` (que internamente usa `UserDetailsServiceImpl.loadUserByUsername` + el `PasswordEncoder`) → si es válido, reconstruye un `UserDetails` con las authorities `ROLE_<nombreRol>` del usuario → `JwtService.generarToken()` firma el JWT → responde `JwtResponseDTO { token, tipo: "Bearer", nombreUsuario, expiraEnMs }`.

### 3.3 Autenticación de cada request protegida
**Entrada:** cualquier request con header `Authorization: Bearer <token>` contra `auth-service`.
Flujo: `JwtAuthFilter.doFilterInternal()` (`security/JwtAuthFilter.java`) extrae el token, llama `JwtService.extraerNombreUsuario()`/`esTokenValido()`; si el JWT lanza `JwtException` (expirado/inválido/malformado), el filtro corta la cadena ahí mismo y escribe la respuesta 401 vía `JsonAuthEntryPoint.escribirError(...)` — **no** deja propagar la excepción. Si es válido, construye un `UsernamePasswordAuthenticationToken` con las authorities del usuario y lo pone en `SecurityContextHolder`. La decisión de autorización por ruta (`hasRole("ADMIN")`, `authenticated()`, `permitAll()`) la resuelve `SecurityConfig.securityFilterChain()`; si el rol no alcanza, actúa `JsonAccessDeniedHandler` (403).

### 3.4 Gestión de roles (flujo admin)
**Entrada:** `POST /api/admin/roles`, `GET /api/admin/roles`, `GET /api/admin/usuarios`, `POST /api/admin/usuarios/{usuarioId}/roles`, `DELETE /api/admin/usuarios/{usuarioId}/roles/{nombreRol}` → todos en `RolController.java`, todos protegidos por `hasRole("ADMIN")`.
Flujo: `RolController` delega íntegramente en `RolService` (`service/RolService.java`), que opera sobre `RolRepository`/`UsuarioRepository` y mapea las entidades `Rol`/`Usuario` a `RolResponseDTO`/`UsuarioResponseDTO`. Crear un rol duplicado lanza `ApiException("Ya existe un rol con ese nombre", HttpStatus.CONFLICT)`. Asignar o quitar un rol a un usuario inexistente, o un rol inexistente, lanza `ApiException(..., HttpStatus.NOT_FOUND)`.

**En el frontend**, `admin-roles.html` no tiene un botón "agregar rol" aditivo: seleccionar un rol distinto en el `<select>` de un usuario y confirmar dispara primero un `DELETE` por cada rol actual del usuario y luego el `POST` del rol nuevo (semántica de "cambiar rol", no "agregar"). El cambio de `<select>` solo actualiza un texto de previsualización (`textContent`, no persiste nada) hasta que se hace submit.

### 3.5 Petición desde el frontend hacia el backend
**Entrada:** cualquier `apiRequest(path, options)` en `frontend/js/app.js`.
Flujo: `apiRequest` arma un `fetch` contra `API_BASE_URL` (`http://localhost:8080/api`) → la petición llega al **api-gateway** (puerto 8080), cuyas rutas (definidas en `config-server/.../config/api-gateway.yml`, no en código Java) reenvían `Path=/api/auth/**` y `Path=/api/admin/**` a `http://localhost:8081` (auth-service) → la respuesta (JSON con forma `{status, error, mensaje, errores?}` en caso de error) vuelve al frontend, que si `!response.ok` construye un `Error` con `.status` y `.errores` y lo lanza para que el `catch` de cada formulario lo maneje.

## 4. Responsabilidades por Capa

| Capa | SÍ puede contener | NO puede contener |
|---|---|---|
| **`controller/`** (`AuthController`, `RolController`) | Mapeo de rutas HTTP (`@PostMapping`, etc.), extracción de `@RequestBody`/`@PathVariable`/`@AuthenticationPrincipal`, delegar 1:1 a un método de `service/`, fijar el `HttpStatus` de la respuesta exitosa. | Lógica de negocio (validaciones de unicidad, reglas de asignación de roles), acceso a `repository/` directo, construcción manual de JSON de error (eso es de `GlobalExceptionHandler`/los handlers de `security/`). |
| **`service/`** (`AuthService`, `RolService`) | Reglas de negocio, orquestación de `repository/`, mapeo entity↔DTO, lanzar `ApiException` con el `HttpStatus` correspondiente al caso de negocio, `@Transactional` donde haya escritura. | Nada relacionado con HTTP (no debe conocer `HttpServletRequest`, headers, ni construir `ResponseEntity`). No debe escribir HQL/SQL a mano — eso vive en `repository/`. |
| **`repository/`** (interfaces `JpaRepository`) | Métodos derivados por nombre (`findByNombreUsuario`, `existsByEmail`, etc.) o `@Query` si hiciera falta. | Lógica de negocio, validaciones, mapeo a DTO. |
| **`entity/`** (`Usuario`, `Rol`, `Permiso`) | Mapeo JPA puro (`@Entity`, `@Column`, `@ManyToMany`), Lombok (`@Getter/@Setter/@Builder`). | Lógica de negocio, validaciones de formato (eso vive en los DTO vía Bean Validation), lógica de serialización HTTP. |
| **`dto/`** (records) | Forma de entrada/salida de la API, anotaciones de Bean Validation (`@NotBlank`, `@Pattern`, `@Size`). | Lógica (los DTO en este proyecto son `record`s inmutables sin métodos propios). |
| **`security/`** | Todo lo relacionado con JWT (`JwtService`), el filtro de autenticación (`JwtAuthFilter`), el puente a Spring Security (`UserDetailsServiceImpl`) y los handlers de error de seguridad (`JsonAuthEntryPoint`, `JsonAccessDeniedHandler`). | Lógica de negocio de dominio (eso es de `service/`). |
| **`exception/`** | `ApiException` (excepción de negocio con `HttpStatus` asociado) y `GlobalExceptionHandler` (`@RestControllerAdvice`) que la traduce a JSON. | Lógica de negocio; solo traducción de excepción → respuesta. |
| **`frontend/js/app.js`** | Cliente API genérico (`apiRequest`), utilidades compartidas (`escapeHtml`, `showToast`, validadores de formulario, `attachRealtimeValidation`). | Lógica específica de una sola página (eso vive en el `<script>` inline de cada `.html`). |
| **cada `frontend/*.html`** | Markup + su propio `<script>` inline con la lógica específica de esa pantalla. | Reimplementar utilidades ya presentes en `app.js` (p. ej. no duplicar `apiRequest` ni el escapado HTML). |

## 5. Reglas de Ubicación de Archivos

| Tipo de código nuevo | Carpeta destino | Justificación |
|---|---|---|
| Nuevo endpoint REST | `auth-service/.../controller/<Nombre>Controller.java` | Es el único módulo con lógica de negocio; `api-gateway` no define controllers, solo enrutamiento declarativo. |
| Nueva regla de negocio / orquestación | `auth-service/.../service/<Nombre>Service.java` | Los controllers delegan 1:1; la lógica no vive en `controller/` (ver sección 4). |
| Nueva consulta a base de datos | Método en la interfaz `repository/` correspondiente (`UsuarioRepository`, `RolRepository`, `PermisoRepository`) o una interfaz nueva si es una entidad nueva | Acceso a datos centralizado en `repository/`, nunca vía `EntityManager` a mano dentro de un `service`. |
| Nueva tabla / entidad JPA | `auth-service/.../entity/<Nombre>.java` + su repository en `repository/` | Sigue el patrón existente (`Usuario`, `Rol`, `Permiso`), todas con Lombok (`@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`). |
| Nuevo contrato de entrada/salida de un endpoint | `auth-service/.../dto/<Nombre>RequestDTO.java` o `<Nombre>ResponseDTO.java`, como `record` | Ya es el patrón de todos los DTO existentes; nunca exponer una `entity` directamente en un controller. |
| Nueva regla de validación de un campo | Anotación de Bean Validation (`@NotBlank`, `@Size`, `@Pattern`) directamente en el campo del DTO correspondiente | Así están `RegistroRequestDTO.password` (regex de fuerza) y `RolRequestDTO.nombre` (whitelist de caracteres); no crear un validador imperativo aparte para reglas de formato de campo. |
| Nuevo tipo de error de negocio | Lanzar `ApiException(mensaje, HttpStatus)` desde el `service/` correspondiente | Ya cubre el 90% de casos (`CONFLICT`, `NOT_FOUND`, etc.); solo agregar un nuevo `@ExceptionHandler` en `GlobalExceptionHandler` si el tipo de excepción no es `ApiException`/`BadCredentialsException`/`MethodArgumentNotValidException`. |
| Nueva regla de autorización por ruta | `SecurityConfig.securityFilterChain()` (`.authorizeHttpRequests(...)`) | Es el único lugar donde se decide qué rutas son públicas/autenticadas/con rol. |
| Configuración de un servicio (puerto, credenciales, CORS, JWT secret) | `config-server/src/main/resources/config/<nombre-del-servicio>.yml` | El `application.yml` propio de cada servicio es solo bootstrap (nombre de la app + `spring.cloud.config.import`); la config real la sirve `config-server` (ver `CLAUDE.md`, sección "Config resolution chain"). |
| Nueva página del frontend | `frontend/<nombre>.html` con su propio `<script>` inline al final, reutilizando `js/app.js` | Sigue el patrón de `login.html`/`registro.html`/`perfil.html`/`admin-roles.html`: sin build tool, cada página es autocontenida salvo por `app.js`/`styles.css` compartidos. |
| Nueva utilidad de frontend reusable entre páginas | `frontend/js/app.js` | Ahí viven `apiRequest`, `escapeHtml`, `showToast`, los validadores y `attachRealtimeValidation`; no crear un segundo archivo JS compartido. |
| Nueva ruta del gateway | `config-server/.../config/api-gateway.yml` (`spring.cloud.gateway.routes`) | El enrutamiento del gateway es 100% declarativo en YAML; `ApiGatewayApplication.java` no contiene lógica de rutas. |

## 6. Convenciones de Nombres

- **Idioma del dominio: español.** Todas las clases, campos, variables y mensajes de negocio están en español (`Usuario`, `Rol`, `Permiso`, `nombreUsuario`, `RegistroRequestDTO`, mensajes de error como `"El nombre de usuario ya está en uso"`). Mantener esta convención en código nuevo del dominio; no mezclar con inglés salvo en nombres técnicos de framework (`Service`, `Controller`, `Repository`, `DTO`).
- **Clases de controlador:** `<Recurso>Controller` (`AuthController`, `RolController`), `@RestController` + `@RequestMapping("/api/<recurso>")`.
- **Clases de servicio:** `<Recurso>Service` (`AuthService`, `RolService`).
- **Clases de repositorio:** `<Entidad>Repository extends JpaRepository<Entidad, Long>` (`UsuarioRepository`, `RolRepository`, `PermisoRepository`).
- **DTOs de entrada:** `<Acción><Recurso>RequestDTO` como `record` (`RegistroRequestDTO`, `LoginRequestDTO`, `RolRequestDTO`, `AsignarRolRequestDTO`).
- **DTOs de salida:** `<Recurso>ResponseDTO` como `record` (`UsuarioResponseDTO`, `RolResponseDTO`, `JwtResponseDTO`).
- **Excepciones:** `ApiException` es la única excepción de negocio; se instancia con `new ApiException(mensaje, HttpStatus)`, nunca subclases nuevas para cada caso de error (ver sección 8).
- **Campos de entidad / DTO en español:** `nombreUsuario`, `email`, `password`, `activo`, `fechaCreacion`, `roles`, `descripcion`, `nombreRol`.
- **Columnas de base de datos en snake_case español:** `nombre_usuario`, `fecha_creacion`, `usuario_rol`, `rol_permiso` (tablas intermedias `<entidadA>_<entidadB>`).
- **Constantes:** `ROL_POR_DEFECTO` (mayúsculas con guion bajo), valor `"USUARIO"` — ese es el nombre real del rol por defecto sembrado por `DataSeeder` y usado por `AuthService.registrar()`.
- **Prefijo de autoridad Spring Security:** `"ROLE_" + rol.getNombre()` (p. ej. `ROLE_ADMIN`, `ROLE_USUARIO`) — usado tanto en `AuthService.login()` como en `UserDetailsServiceImpl`. Cualquier chequeo de autorización nuevo debe usar `hasRole("ADMIN")` (sin el prefijo `ROLE_`, Spring lo agrega solo), igual que en `SecurityConfig`.
- **Funciones de frontend en español, con sufijos consistentes:** `manejarAsignar`, `manejarQuitar`, `manejarErrorSesion`, `cargarRoles`, `cargarUsuarios`, `renderUsuario`, `textoPreview`.

## 7. Contratos de API y Salida

Estos campos son el contrato observable por el frontend y por cualquier consumidor externo; no renombrar ni quitar sin actualizar todos los consumidores (`frontend/js/app.js` y cada `.html`).

- **`POST /api/auth/register` → `UsuarioResponseDTO`** (201): `id`, `nombreUsuario`, `email`, `activo`, `fechaCreacion`, `roles` (array de nombres de rol, siempre incluye `"USUARIO"` para un alta nueva).
- **`POST /api/auth/login` → `JwtResponseDTO`** (200): `token`, `tipo` (siempre `"Bearer"`), `nombreUsuario`, `expiraEnMs`. El frontend guarda `token` y `nombreUsuario` en `sessionStorage`.
- **`GET /api/auth/me` → `UsuarioResponseDTO`** (200, requiere autenticación): mismos campos que en registro. `perfil.html` y `admin-roles.html` dependen de `roles.includes("ADMIN")` para decidir acceso al panel de administración.
- **`GET /api/admin/roles` → `List<RolResponseDTO>`** (200, ADMIN): cada elemento `id`, `nombre`, `descripcion`. `admin-roles.html` usa `nombre` tanto para las `<option>` del selector como para las comparaciones de "rol ya asignado".
- **`POST /api/admin/roles` → `RolResponseDTO`** (201, ADMIN): mismos 3 campos.
- **`GET /api/admin/usuarios` → `List<UsuarioResponseDTO>`** (200, ADMIN): mismos campos que `UsuarioResponseDTO` de arriba.
- **`POST /api/admin/usuarios/{id}/roles`** body `{ nombreRol }` **→ `UsuarioResponseDTO`** (200, ADMIN).
- **`DELETE /api/admin/usuarios/{id}/roles/{nombreRol}` → `UsuarioResponseDTO`** (200, ADMIN).
- **Forma de error unificada** (todas las capas, ver sección 8): `{ timestamp, status, error, mensaje }`, con `errores` (mapa `campo → mensaje`) adicional solo en errores de validación (400). El frontend (`apiRequest` en `app.js`) depende explícitamente del campo `mensaje` para mostrar el toast y de `errores` para pintar los campos de formulario en rojo (`aplicarErroresBackend`) — no renombrar estos dos campos.

## 8. Manejo de Errores

Cada capa mapea a un tipo de error distinto, y **ninguna capa debe dejar propagar una excepción cruda de la capa inferior hacia el cliente HTTP** (ni un stack trace, ni una excepción de JPA/JWT sin traducir):

| Origen del error | Tipo de excepción | Quién la traduce | HTTP resultante |
|---|---|---|---|
| Regla de negocio en `service/` (nombre de usuario/email duplicado, rol duplicado, usuario/rol no encontrado) | `ApiException(mensaje, HttpStatus)` | `GlobalExceptionHandler.handleApiException` | El `HttpStatus` que trae la excepción (`CONFLICT`, `NOT_FOUND`, etc.) |
| Credenciales inválidas en login | `BadCredentialsException` (de Spring Security, lanzada por `AuthenticationManager.authenticate`) | `GlobalExceptionHandler.handleBadCredentials` | 401, mensaje fijo `"Credenciales inválidas"` |
| Validación de campos del DTO (`@NotBlank`, `@Size`, `@Pattern`, etc.) | `MethodArgumentNotValidException` | `GlobalExceptionHandler.handleValidation` | 400, con el mapa `errores` campo→mensaje |
| Token JWT ausente/expirado/inválido detectado en el filtro | `JwtException` (capturada dentro de `JwtAuthFilter`, no propagada) | El propio `JwtAuthFilter`, escribiendo la respuesta con `JsonAuthEntryPoint.escribirError(...)` | 401 |
| Autenticado pero sin el rol requerido por la ruta | Rechazo de `AuthorizationFilter` de Spring Security | `JsonAccessDeniedHandler` | 403 |
| Content-Type incompatible con el `@RequestBody` esperado | `HttpMediaTypeNotSupportedException` (ver nota de `CLAUDE.md` sobre el bug de `apiRequest()`) | Mecanismo por defecto de Spring MVC (no hay `@ExceptionHandler` propio) | 415 (si el header se arma bien en el cliente); si se rompe el orden del merge de headers en `apiRequest`, termina enmascarado como 401 vía el forward interno a `/error` — ver `CLAUDE.md` |

Reglas explícitas:
- **Todo error de negocio nuevo se expresa como `ApiException` con el `HttpStatus` correcto**, lanzada desde `service/`; no crear una jerarquía de excepciones nueva salvo que el tipo de error no encaje en los cuatro `@ExceptionHandler` ya existentes en `GlobalExceptionHandler`.
- **La forma de la respuesta de error siempre es la que arma `cuerpoError()`** en `GlobalExceptionHandler` (`timestamp`, `status`, `error`, `mensaje`) — cualquier handler nuevo (incluidos los de `security/`) debe producir el mismo shape, como ya hacen `JsonAuthEntryPoint`/`JsonAccessDeniedHandler`.
- **Ningún `controller/` debe tener try/catch propio** para traducir errores a HTTP; esa responsabilidad es exclusiva de `GlobalExceptionHandler` y los handlers de `security/`.

## 9. Configuración y Entorno

- **Lenguaje / runtime:** Java 21 (`java.version`, `maven.compiler.source/target` = 21 en el `pom.xml` padre).
- **Gestor de dependencias/build:** Maven multi-módulo. POM padre `com.equipoft4:loggin-platform:1.0.0-SNAPSHOT` (`packaging=pom`) con `<modules>config-server, auth-service, api-gateway</modules>` y `dependencyManagement` centralizado para `spring-boot-dependencies` (3.3.4) y `spring-cloud-dependencies` (2023.0.3).
- **Frontend:** sin gestor de paquetes ni build (HTML/CSS/JS servidos como estáticos, actualmente con `npx serve` — ver `CLAUDE.md`).
- **Base de datos:** MySQL 8.0 vía `docker-compose.yml` (`mysql-connector-j` como driver runtime en `auth-service`), base `app_db`, `spring.jpa.hibernate.ddl-auto: update` (definido en `config-server/.../config/auth-service.yml`, no en el `auth-service` local).
- **Dónde vive la configuración centralizada:** `config-server/src/main/resources/config/<nombre-del-servicio>.yml` (perfil `native`, `search-locations: classpath:/config`). El `application.yml` de cada servicio (`auth-service/src/main/resources/application.yml`, `api-gateway/.../application.yml`) solo declara `spring.application.name` y `spring.config.import: "optional:configserver:http://localhost:8888"` con `fail-fast: false`.
- **Cómo agregar una variable/config nueva a un servicio:** agregarla en el YAML de `config-server/.../config/<servicio>.yml` correspondiente, no en el `application.yml` del propio servicio (salvo que sea configuración de bootstrap necesaria antes de contactar al config-server).
- **Secretos actuales (JWT secret, credenciales de MySQL) viven en texto plano dentro de `config-server/.../config/auth-service.yml`** — es el único lugar de la config centralizada, coherente con el resto del diseño; cualquier secreto nuevo sigue ese mismo patrón salvo que se decida explícitamente introducir un mecanismo distinto (vault, variables de entorno, etc.), lo cual sería un cambio de arquitectura, no una adición incremental.
- **Puertos fijos:** `config-server` 8888, `auth-service` 8081, `api-gateway` 8080, frontend 5500 (convención, no hardcodeado en backend salvo en la whitelist de CORS de `api-gateway.yml`), MySQL 3306.
- **CORS:** definido una sola vez, de forma global, en `config-server/.../config/api-gateway.yml` (`spring.cloud.gateway.globalcors`). No hay configuración CORS en `auth-service` ni en el código Java del gateway.

## 10. Anti-patrones

Prohibido introducir en este repositorio:

1. **Lógica de negocio dentro de un `controller/`** (validaciones más allá de Bean Validation, condicionales de reglas de negocio, acceso directo a un `repository/`). Debe vivir en `service/`.
2. **Acceso a `repository/` o al `EntityManager` desde un `controller/`.** Los controllers solo llaman a `service/`.
3. **Exponer una `@Entity` (`Usuario`, `Rol`, `Permiso`) directamente como cuerpo de respuesta de un endpoint.** Siempre mapear a un `*ResponseDTO`, como ya hacen `AuthService`/`RolService` con sus métodos `aResponseDTO`/`aUsuarioResponseDTO`.
4. **Try/catch de traducción de errores dentro de un `controller/` o `service/`.** La traducción a HTTP es responsabilidad exclusiva de `GlobalExceptionHandler` (para excepciones de negocio/validación) y de `JsonAuthEntryPoint`/`JsonAccessDeniedHandler` (para errores de la cadena de seguridad).
5. **Dejar propagar una excepción cruda** (`JwtException`, excepción de JPA, `NullPointerException`, etc.) hasta el cliente sin traducir — ni como stack trace ni como página de error HTML del contenedor (ver la nota de `CLAUDE.md` sobre el 415→401 enmascarado).
6. **Insertar en el DOM del frontend (`innerHTML`) un valor proveniente del backend sin pasar por `escapeHtml()`** (username, email, nombre de rol). Es la única mitigación de XSS que tiene el frontend, ya que no hay `Content-Security-Policy` configurada (ver `CLAUDE.md`).
7. **Relajar o quitar las restricciones de caracteres en `nombreUsuario` (`RegistroRequestDTO`) o en `nombre`/`descripcion` de rol (`RolRequestDTO`)** sin reemplazarlas por una mitigación equivalente — existen específicamente para bloquear XSS almacenado, no son validación de formato incidental.
8. **Reordenar `apiRequest()` en `frontend/js/app.js` de forma que `...options` se spreadee después de `headers`.** Ese orden específico existe para que el `Content-Type: application/json` por defecto no sea sobrescrito por un `options.headers` parcial (ver `CLAUDE.md`).
9. **Hardcodear configuración de servicio (puerto, URL de datasource, secretos, CORS) en el `application.yml` propio de `auth-service` o `api-gateway`.** Esa configuración vive exclusivamente en `config-server/.../config/`.
10. **Agregar lógica de enrutamiento o CORS en código Java dentro de `api-gateway`.** El gateway es puramente declarativo (YAML servido por `config-server`); `ApiGatewayApplication.java` no debe crecer más allá del bootstrap de Spring Boot.
11. **Crear un segundo cliente HTTP o una segunda función de escapado HTML en el frontend.** Toda página nueva debe reusar `apiRequest`/`escapeHtml`/`showToast` de `js/app.js`, no reimplementarlos inline.
12. **Cambiar la semántica de "cambiar rol" de `admin-roles.html` a "agregar rol" (aditiva) sin decisión explícita.** El comportamiento actual (quitar todos los roles actuales antes de asignar el nuevo) es intencional, no un descuido a "corregir".
13. **Introducir dependencias de testing, linters o build tools para el frontend sin que el usuario lo pida explícitamente** — actualmente no existen (ni test suite en ningún módulo Java más allá del scaffolding por defecto de Maven, ni build step de frontend); no asumir su existencia ni inventar comandos de test/lint que no están documentados en `CLAUDE.md` ni en `README.md`.
