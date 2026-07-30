# Loggin Platform

Base de arquitectura de microservicios en Java 21 con Spring Boot / Spring Cloud, persistencia en MySQL vía Spring Data JPA, y un frontend HTML5/CSS3/JS accesible y responsive.

## Estructura del proyecto

```
loggin/
├── pom.xml                # POM padre (dependencyManagement: Spring Boot 3.3.4 / Spring Cloud 2023.0.3)
├── docker-compose.yml     # MySQL 8.0 (app_db / admin / adminpassword)
├── config-server/         # Spring Cloud Config Server (modo native, puerto 8888)
├── auth-service/          # Autenticación, usuarios/roles/permisos, JWT (puerto 8081)
├── api-gateway/           # Enrutamiento + CORS (Spring Cloud Gateway, puerto 8080)
└── frontend/              # HTML5/CSS3/JS estático (login, registro)
```

## Requisitos previos

- Java 21 (JDK)
- Maven 3.9+
- Docker Desktop (para MySQL)

## 1. Levantar la base de datos (MySQL)

Desde la raíz del proyecto:

```bash
docker compose up -d
```

Esto crea el contenedor `mysql-database` con la base de datos `app_db`, usuario `admin` / contraseña `adminpassword`, expuesto en `localhost:3306`.

## 2. Compilar el proyecto

Desde la raíz del proyecto:

```bash
mvn clean install
```

## 3. Levantar los microservicios

Debes levantarlos **en este orden**, cada uno en una terminal distinta:

```bash
# 1) Config Server (puerto 8888)
cd config-server
mvn spring-boot:run
```

```bash
# 2) Auth Service (puerto 8081)
cd auth-service
mvn spring-boot:run
```

```bash
# 3) API Gateway (puerto 8080)
cd api-gateway
mvn spring-boot:run
```

> Nota: `auth-service` y `api-gateway` están configurados con `spring.cloud.config.fail-fast: false`, por lo que también pueden levantarse aunque el `config-server` no esté disponible (usarán solo su configuración local mínima). Para producción, cambia esta bandera a `true`.

Al iniciar `auth-service`, Hibernate creará automáticamente (gracias a `ddl-auto: update`) las tablas:

- `usuarios`
- `roles`
- `permisos`
- `usuario_rol` (tabla intermedia)
- `rol_permiso` (tabla intermedia)

## 4. Verificar las tablas en MySQL

Con el contenedor corriendo, conéctate al cliente de MySQL:

```bash
docker exec -it mysql-database mysql -u admin -padminpassword app_db
```

Y ejecuta:

```sql
SHOW TABLES;
DESCRIBE usuarios;
DESCRIBE roles;
DESCRIBE permisos;
```

## 5. Levantar el frontend

El frontend es HTML5/CSS3/JS puro, sin build tools. Basta con servirlo como archivos estáticos, por ejemplo:

```bash
cd frontend
npx serve -l 5500
```

> Requiere Node.js instalado (no requiere instalar nada globalmente, `npx` descarga `serve` la primera vez).

Alternativa si tienes Python instalado (no el stub de Microsoft Store):

```bash
cd frontend
python -m http.server 5500
```

Luego abre `http://localhost:5500` en el navegador.

> El origen `http://localhost:5500` ya está permitido en la configuración CORS del `api-gateway` (`config-server/src/main/resources/config/api-gateway.yml`). Si usas otro puerto/herramienta (p. ej. la extensión Live Server de VS Code), ajusta `allowedOrigins` en ese archivo.

## 6. Probar el flujo end-to-end

1. Abre `http://localhost:5500/registro.html` y crea una cuenta.
2. Verifica en MySQL que el usuario se guardó: `SELECT * FROM usuarios;`
3. Inicia sesión en `http://localhost:5500/login.html`. El token JWT recibido se guarda en `sessionStorage`.

## Endpoints disponibles (vía API Gateway, `http://localhost:8080`)

| Método | Endpoint             | Descripción                     |
|--------|-----------------------|----------------------------------|
| POST   | `/api/auth/register` | Registra un nuevo usuario        |
| POST   | `/api/auth/login`    | Autentica y devuelve un JWT      |

## Próximos pasos sugeridos

- Añadir un microservicio de negocio adicional detrás del gateway.
- Persistir el token JWT de forma más robusta (refresh tokens).
- Agregar pruebas automatizadas (unitarias e integración) por microservicio.
- Migrar el frontend a un framework (React/Vue) si el proyecto crece en complejidad.
