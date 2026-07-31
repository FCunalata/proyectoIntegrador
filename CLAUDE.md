# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Base microservices architecture (Java 21 / Spring Boot 3.3.4 / Spring Cloud 2023.0.3) with a static HTML/CSS/JS frontend. Three Maven modules under a parent POM (`com.equipoft4:loggin-platform`), plus a plain `frontend/` directory served independently (no build tooling, no framework).

- **config-server** (port 8888) — Spring Cloud Config Server in `native` profile. Serves YAML config files from `config-server/src/main/resources/config/{service-name}.yml` (e.g. `auth-service.yml`, `api-gateway.yml`). This is where DB credentials, JWT secret, and per-service `server.port` live — not in the service's own `application.yml`.
- **auth-service** (port 8081) — the only service with business logic: user registration/login (JWT), role & permission management, MySQL persistence via Spring Data JPA/Hibernate.
- **api-gateway** (port 8080) — Spring Cloud Gateway (WebFlux/reactive). Routes `/api/auth/**` and `/api/admin/**` to `auth-service` at `http://localhost:8081`. Owns the global CORS config (allowed origins include `http://localhost:5500`/`3000` for the frontend).
- **frontend/** — static HTML5/CSS3/vanilla JS, no bundler. Talks to the backend exclusively through the gateway at `http://localhost:8080/api` (see `API_BASE_URL` in `frontend/js/app.js`).
- **MySQL** — via `docker-compose.yml`, database `app_db`, user `admin`/`adminpassword`, exposed on `3306`.

Both `auth-service` and `api-gateway` declare `spring.cloud.config.fail-fast: false`, so they still start (with only their local minimal config) if `config-server` isn't running — useful during iterative dev, but means a missing config-server can silently leave a service under-configured rather than failing loudly.

## Common commands

Everything must start **in this order** (config-server → auth-service → api-gateway), each in its own terminal, plus MySQL first:

```bash
docker compose up -d                    # start MySQL (must be up before auth-service)
mvn clean install                       # build all modules from repo root

cd config-server && mvn spring-boot:run # port 8888
cd auth-service && mvn spring-boot:run  # port 8081 (needs MySQL)
cd api-gateway && mvn spring-boot:run   # port 8080
```

Frontend (separate terminal, no build step):
```bash
cd frontend && npx serve -l 5500        # http://localhost:5500
```
(`python -m http.server 5500` also works if a real Python install is present — the Microsoft Store Python alias stub does not count.)

Single-module build/compile (from repo root, faster than building everything):
```bash
mvn -pl auth-service -am compile
```

There are currently no automated tests in any module (no `src/test` content beyond Maven's default scaffolding) and no frontend lint/build scripts — verification is manual (curl against the gateway/service, or a browser).

**Port conflicts**: since services are run as long-lived `mvn spring-boot:run` processes across many terminals/sessions, the most common failure is a stale process still holding 8888/8081/8080 from a previous run. Find and kill it before re-running:
```powershell
Get-NetTCPConnection -LocalPort 8081 -State Listen | Select OwningProcess
Stop-Process -Id <pid> -Force
```

## Architecture notes specific to this codebase

**Config resolution chain.** A service's *effective* config is the merge of its own `src/main/resources/application.yml` (bootstrap-only: service name, `spring.cloud.config.import` URL) and whatever `config-server` serves from `config-server/src/main/resources/config/<name>.yml`. When changing DB credentials, JWT settings, CORS origins, or a service's port, edit the file under `config-server/.../config/`, not the service's own `application.yml`.

**Security filter chain (auth-service).** `JwtAuthFilter` (a `OncePerRequestFilter`) reads `Authorization: Bearer <token>`, and on a `JwtException` (expired/malformed token) short-circuits the response itself via `JsonAuthEntryPoint.escribirError(...)` rather than letting the exception propagate — this keeps the error body JSON instead of a container-generated HTML error page (which previously masked errors with a misleading response). `SecurityConfig` wires `JsonAuthEntryPoint` (401, unauthenticated) and `JsonAccessDeniedHandler` (403, authenticated but wrong role) as the `exceptionHandling` handlers so *every* security-layer rejection returns the same JSON shape (`timestamp`, `status`, `error`, `mensaje`) as `GlobalExceptionHandler` (`@RestControllerAdvice`) uses for business errors (`ApiException`, bean-validation errors). `/api/admin/**` requires `hasRole("ADMIN")`; `/api/auth/me` requires authentication; `/api/auth/**` and `/actuator/**` are public.

**Role/permission model.** `Usuario` ⟷ `Rol` is many-to-many (`usuario_rol` join table, EAGER fetch), `Rol` ⟷ `Permiso` is many-to-many (`rol_permiso`). `DataSeeder` (a `CommandLineRunner`) seeds two roles on every startup if missing: `USUARIO` (default, gets `LEER_USUARIOS`) and `ADMIN` (gets all permissions). New registrations get `USUARIO` automatically — there is no unassigned/roleless state by default; roles are only removed by an admin explicitly.

**Input validation is defense-in-depth on purpose.** `RegistroRequestDTO.password` enforces min-8-chars + upper/lower/digit/special via `@Pattern`. `RegistroRequestDTO.nombreUsuario` and `RolRequestDTO.nombre`/`descripcion` are restricted to a safe character set (`^[A-Za-zÀ-ÿ0-9 _.-]+$`, and `^[^<>]*$` for descriptions) specifically to prevent stored XSS, since the frontend renders these values into the DOM. **Frontend rendering must never regress to raw `innerHTML` interpolation of user-controlled fields** (username, email, role name) — always go through `escapeHtml()` in `frontend/js/app.js`. This app has no CSP header configured, so the escaping is the only XSS mitigation; don't rely on backend validation alone since it only covers newly-created data, not whatever might already be in the DB.

**Frontend `apiRequest()` header merging.** In `frontend/js/app.js`, the fetch options object must spread `...options` *before* setting `headers`, not after — reversing that order lets a caller's `options.headers` (e.g. just `{ Authorization: ... }`) silently clobber the default `Content-Type: application/json`, which makes the browser fall back to `Content-Type: text/plain` on POST/PUT bodies. Spring then throws `HttpMediaTypeNotSupportedException`, which triggers a servlet-container `/error` forward that re-runs the security filter chain and returns a misleading 401 instead of the real 415 — a very confusing failure mode if this regresses.

**Role admin UI (`frontend/admin-roles.html`) has "change role" semantics, not "add role".** Selecting a new role in a user's dropdown and submitting removes all of that user's *current* roles (via sequential `DELETE /api/admin/usuarios/{id}/roles/{rol}` calls) before assigning the new one (`POST .../roles`) — it does not additively grant roles. The dropdown's `change` event only updates a preview `<p class="form-hint">` (via `textContent`, safe); nothing is persisted until the form's submit button is clicked.

**Error-message-driven frontend session handling.** `manejarErrorSesion()` (duplicated per page, e.g. in `admin-roles.html`) only force-clears the session and expects re-login on **401** responses — a 403 means "authenticated but insufficient role" and should just surface the error message without logging the user out or navigating away.
