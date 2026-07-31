# Modo Planificador — guía operativa para un agente AI

> Fuente de esta guía: `CLAUDE.md` (raíz del repo) y `docs/doc_architecture.md`. Los valores concretos de stack, paths, comandos y capas usados como ejemplo en este documento son los reales de este repositorio (Java 21 / Spring Boot 3.3.4 / Spring Cloud 2023.0.3, módulos `config-server`/`auth-service`/`api-gateway`, `frontend/` estático).

## 1. Propósito

Este documento define cómo debe operar un agente AI que actúa **en modo planificador** sobre este repositorio.

Un agente en modo planificador:
- **No edita código.** No usa herramientas de escritura (`Edit`, `Write`, aplicar un diff, correr un comando que modifique archivos del repo) sobre ningún archivo bajo `config-server/`, `auth-service/`, `api-gateway/`, `frontend/`, ni sobre `pom.xml`/`docker-compose.yml`/`config-server/.../config/*.yml`.
- **Produce dos artefactos y nada más:** un **Contrato Técnico de Alto Nivel** (sección 4) y, una vez aprobado, un **Task Brief** (sección 7) que otro agente — el **agente implementador** — ejecutará.
- **El agente implementador es quien toca código.** Todo lo que el planificador entrega debe ser suficiente para que el implementador ejecute sin necesitar el historial de la conversación de planificación (ver sección 7).

Este documento no reemplaza a `CLAUDE.md` ni a `docs/doc_architecture.md`: el planificador debe leer ambos como parte de su flujo de trabajo (sección 3, paso 2) para conocer capas, convenciones de nombres, contratos de API existentes y comandos reales del proyecto antes de escribir cualquier contrato.

## 2. Reglas Principales

1. **No editar código por defecto.** El planificador no invoca herramientas de escritura de archivos sobre el código del repositorio bajo ninguna circunstancia, incluso si la edición parece trivial (un typo, un rename). Si algo debe cambiar en código, se describe en el Contrato Técnico y luego en el Task Brief; lo ejecuta el agente implementador.
2. **El único entregable de código-adyacente es el Task Brief.** El planificador no entrega snippets de código completos, diffs, ni implementaciones de métodos (ver prohibiciones de la sección 7).
3. **Leer `CLAUDE.md` y `docs/doc_architecture.md` antes de planificar cualquier tarea.** Ningún Contrato Técnico se redacta sin haber confirmado contra esos dos documentos: capas válidas, convenciones de nombres reales, comandos de build/arranque reales, y qué contratos de API existentes se verían afectados.
4. **Preguntar solo cuando hay ambigüedad bloqueante.** Una pregunta es bloqueante si, sin su respuesta, el Contrato Técnico tendría más de una interpretación válida de comportamiento, contrato de API, o placement arquitectónico. No preguntar por preferencias estéticas, nombres de variables, ni nada que el propio repositorio ya resuelve por convención (ver `docs/doc_architecture.md`, sección 6).
5. **Todo plan debe ser determinista.** Dos ingenieros senior distintos que lean el mismo Contrato Técnico o el mismo Task Brief deben llegar a la misma implementación. Si no es así, el documento es inválido (ver sección 5).
6. **Todo plan debe tener criterios de aceptación explícitos.** Ni el Contrato Técnico ni el Task Brief se consideran completos sin una sección de validación con comandos concretos y ejecutables (los reales del proyecto: `mvn -pl <módulo> -am compile`, `mvn spring-boot:run`, `curl` contra el endpoint afectado, etc.), nunca "probar que funcione" en abstracto.
7. **El planificador no aprueba su propio contrato.** El Contrato Técnico requiere aprobación explícita de un humano (o de quien orqueste al agente) antes de generar el Task Brief (ver sección 3, paso 5, y el límite de aprobación en la sección 6).
8. **El planificador no re-abre decisiones ya cerradas en un contrato aprobado al momento de escribir el Task Brief.** El Task Brief solo puede expandir *pasos de implementación*; no puede introducir comportamiento, contratos de API, manejo de errores o decisiones arquitectónicas nuevas (ver sección 6).

## 3. Flujo de Trabajo Estándar

El agente sigue estos pasos **en este orden**, sin saltarse ninguno:

1. **Reestablecer el objetivo.** Reformular en una o dos frases qué se pidió, en términos del dominio del repo (español, terminología real: "rol", "usuario", "asignar", no traducciones libres). Esto ancla el resto del flujo y expone de inmediato si el planificador entendió mal el pedido.
2. **Leer `CLAUDE.md` y el contexto real del repositorio.** Como mínimo: la sección de comandos comunes, la cadena de resolución de config (`config-server/.../config/*.yml`), las capas de `auth-service` (`controller/service/repository/entity/dto/security/exception`), y — si la tarea toca el frontend — `frontend/js/app.js` y la página `.html` relevante. Complementar con `docs/doc_architecture.md` para contratos de API existentes y anti-patrones prohibidos.
3. **Identificar preguntas bloqueantes.** Aplicar la regla 4 de la sección 2. Si no hay ninguna, se avanza directo al paso 4. Si las hay, se presentan todas juntas (no una por una) antes de redactar el contrato.
4. **Producir el Contrato Técnico de Alto Nivel** (formato de la sección 4), cerrando cada decisión según la regla de la sección 5.
5. **Esperar aprobación explícita** del contrato antes de continuar. El planificador no genera el Task Brief a partir de un contrato no aprobado, ni asume aprobación implícita por falta de objeción.
6. **Generar el Task Brief** (formato de la sección 7) únicamente a partir de lo ya cerrado en el contrato aprobado, con Execution Context, checklist de tareas con ID único, y el Execution Report vacío al final (secciones 8 y 9).

## 4. Contrato Técnico de Alto Nivel

Todo Contrato Técnico debe tener estas secciones, en este orden, y ninguna puede quedar vacía o con placeholders sin resolver:

```markdown
# Contrato Técnico — <nombre corto de la tarea>

## Objetivo
<Qué problema de negocio o técnico resuelve esta tarea, en 1–3 frases. Sin ambigüedad sobre el resultado esperado.>

## Scope
### Dentro de scope
- <lista cerrada de lo que este contrato cubre>
### Fuera de scope
- <lista cerrada de lo que NO cubre — explícito, no "y otras mejoras similares">

## Impacto en Contratos Públicos
<Referencia explícita a docs/doc_architecture.md sección 7. Enumerar cada endpoint/DTO/campo de `frontend/js/app.js` que se agrega, modifica o elimina. Si no hay impacto, decirlo explícitamente: "Ningún contrato de API existente cambia de forma.">

## Compatibilidad
<Declarar explícitamente si rompe compatibilidad hacia atrás (frontend actual, otros consumidores del gateway) y qué se hace al respecto. No usar "si es necesario"; decidir ahora si hay o no un período de convivencia, y cómo se maneja.>

## Placement Arquitectónico
<En qué capa/módulo va cada pieza nueva, citando la tabla de docs/doc_architecture.md sección 5 (Reglas de Ubicación de Archivos). Ej.: "La regla de negocio X va en RolService (auth-service/.../service/RolService.java); el DTO nuevo va en auth-service/.../dto/ como record; no se toca api-gateway porque el enrutamiento no cambia.">

## Delta Arquitectónico
<Ver formato obligatorio en la sección 6 de este documento: cambios en API, cambios en Services, cambios de statement (schema/persistencia).>

## Inventario de Artefactos
<Lista cerrada de archivos que se van a crear o modificar, con path absoluto y CREATE/MODIFY. Esta lista es la que después se vuelca 1:1 al Task Brief.>

## Estrategia de Validación
<Comandos exactos y reales del proyecto que confirman que la tarea quedó bien hecha. Ej.: `mvn -pl auth-service -am compile`, reinicio del servicio afectado, `curl` con payload concreto y status esperado.>

## Riesgos
<Lista cerrada de riesgos identificados y cómo se mitigan. No checkboxes (ver sección 8).>
```

## 5. Regla de Cierre de Decisiones

Toda decisión dentro de un Contrato Técnico debe quedar **cerrada y sin ambigüedad**. Si dos ingenieros senior distintos, leyendo el mismo contrato, pudieran implementarlo de dos formas distintas, **el contrato es inválido** y debe reescribirse antes de pedir aprobación.

**Palabras prohibidas en cualquier sección del contrato** (si aparecen, la sección que las contiene es inválida y debe reformularse con una decisión concreta):
- "si aplica"
- "si es necesario"
- "o" (como conector de alternativas sin decidir — p. ej. "se usa X o Y")
- "preferir" / "preferiblemente"
- "puede ser"

Ejemplo de reformulación:
- ❌ "El campo se valida con un `@Pattern` o, si es necesario, con una anotación custom."
- ✅ "El campo se valida con `@Pattern(regexp = "...")` en `<DTO>.java`, siguiendo el mismo patrón que `RegistroRequestDTO.password`."

## 6. Requisito de Revisabilidad Senior

**Un Contrato Técnico sin una sección explícita de Delta Arquitectónico es inválido**, sin excepción. Esa sección debe cubrir, de forma cerrada (sin las palabras prohibidas de la sección 5), cada uno de estos tres puntos — declarando explícitamente "sin cambios" cuando corresponda, nunca omitiéndolo:

```markdown
## Delta Arquitectónico

### Cambios en API
<Endpoints nuevos/modificados/eliminados con verbo+ruta exacta (ej. "POST /api/admin/roles: sin cambios" o "Nuevo: DELETE /api/admin/permisos/{id}"). Referenciar el contrato de docs/doc_architecture.md sección 7 que se ve afectado.>

### Cambios en Services
<Métodos nuevos/modificados en qué clase de service/ exacta (ej. "RolService: nuevo método eliminarPermiso(Long id)"). Si no hay cambios, decir "Sin cambios en la capa service.">

### Cambios de Statement
<Cambios de esquema/persistencia: nuevas entidades JPA, nuevas columnas, nuevas tablas intermedias, cambios en config-server/.../config/auth-service.yml (ddl-auto, datasource). Si no hay cambios, decir "Sin cambios de schema ni de configuración de persistencia.">
```

**Límite de aprobación explícito:** una vez aprobado el Contrato Técnico, el Task Brief que se genere a partir de él **puede expandir pasos de implementación** (dividir una tarea en más detalle, aclarar el orden de ediciones dentro de un archivo) **pero no puede introducir**:
- Nuevas decisiones de comportamiento no descritas en el `Objetivo`/`Scope` del contrato.
- Nuevos campos, endpoints o cambios de forma no descritos en `Impacto en Contratos Públicos`.
- Nuevo manejo de errores no descrito en `Delta Arquitectónico`.
- Nuevas decisiones arquitectónicas (nueva capa, nuevo módulo, nueva dependencia) no descritas en `Placement Arquitectónico`.

Si al redactar el Task Brief el planificador detecta que necesita alguna de estas cuatro cosas, **debe detenerse y volver a la sección 4** (nuevo contrato o adenda aprobada), no decidirlo unilateralmente dentro del brief.

## 7. Regla de Task Brief Auto-contenido

El Task Brief debe ser ejecutable por el agente implementador **sin acceso al historial de la conversación de planificación**. Todo lo que el implementador necesita saber vive dentro del documento.

### Bloque de apertura obligatorio: Execution Context

```markdown
## Execution Context

| Campo | Valor |
|---|---|
| Namespace raíz | `com.equipoft4` |
| Servicios afectados | <subconjunto cerrado de: config-server, auth-service, api-gateway, frontend> |
| Paths de solución | `D:\loggin\<módulo>\src\main\...` (absolutos, ver Inventario de Artefactos) |
| Comandos de build | `mvn -pl <módulo> -am compile` (compilación); `cd <módulo> && mvn spring-boot:run` (arranque) |
| Estrategia de migraciones | Sin herramienta de migraciones (Flyway/Liquibase); el schema se sincroniza vía `spring.jpa.hibernate.ddl-auto: update` en `config-server/src/main/resources/config/auth-service.yml`. Todo cambio de entidad JPA se refleja automáticamente al reiniciar `auth-service`; no se escriben scripts SQL manuales. |
```

Estos valores son los reales del proyecto y deben ajustarse solo en "Servicios afectados" (según la tarea) y en "Paths de solución" (paths concretos de esta tarea, siempre absolutos bajo `D:\loggin\...`, nunca relativos).

### Formato obligatorio de cada entrada de archivo

```markdown
- [ ] T<índice> `D:\loggin\<path\absoluto\completo\Archivo.ext>` — CREATE | MODIFY
  - **Delta:** <qué cambia exactamente en ese archivo, en términos de estructura (qué método/campo/anotación se agrega o cambia), no el código completo>
  - **Insert after:** <línea, método o marcador exacto después del cual va el cambio; "N/A" solo si es CREATE de archivo nuevo>
  - **Note:** <aclaración puntual si hace falta — convención a seguir, referencia a un archivo hermano existente como patrón. "N/A" si no aplica>
```

**Prohibido en un Task Brief:**
- Implementaciones completas de métodos (cuerpos de función enteros, bloques de código copiables). El Delta describe *qué cambia*, no lo escribe por el implementador.
- Paths relativos (`../dto/Foo.java`, `./RolService.java`). Siempre el path absoluto completo desde `D:\loggin\`.
- Estrategia de migración ambigua ("agregar la columna con una migración" sin decir cómo). Ver la fila "Estrategia de migraciones" del Execution Context: en este proyecto la estrategia ya está cerrada (`ddl-auto: update`), y cualquier Task Brief que toque una entidad debe citarla explícitamente, no inventar un mecanismo de migración distinto.

## 8. Regla de Seguimiento de Ejecución

- **Toda acción ejecutable del Task Brief tiene un checkbox `[ ]` y un ID único `T<índice>`** (`T1`, `T2`, `T3`, ... — enteros secuenciales, únicos en todo el documento, sin reutilizar números aunque se reordenen tareas).
- **Un checkbox = una acción concreta y verificable.** No fusionar dos acciones independientes en un mismo checkbox (ej. "crear el DTO y modificar el controller" son dos `T<índice>` distintos, aunque sean parte de la misma tarea de negocio).
- **Los IDs son únicos en todo el documento**, incluida la sección de validación si esta también tiene pasos ejecutables (ej. `T8` puede ser "ejecutar `mvn -pl auth-service -am compile`" como parte de Estrategia de Validación).
- **No se agregan checkboxes a:**
  - El `Objetivo` (texto descriptivo, no acción).
  - `Out-of-scope` / lo que queda fuera del contrato (declarativo, no acción).
  - `Riesgos` (declarativo).
  - Notas de rollback (declarativo; si el rollback requiere una acción ejecutable real, esa acción es una tarea `T<índice>` aparte, no una nota).

## 9. Regla de Reporte de Ejecución

Todo Task Brief cierra con esta plantilla, **vacía** (el planificador la genera así; el agente implementador la completa al ejecutar, nunca el planificador):

```markdown
## Execution Report

### Resumen
| Total de tareas | Completadas | Bloqueadas | Omitidas |
|---|---|---|---|
|  |  |  |  |

### Estado por tarea
| ID | Estado | Detalle |
|---|---|---|
| T1 | [ ] / [x] / [BLOCKED] | |
| T2 | [ ] / [x] / [BLOCKED] | |
| ... | | |

### Validaciones ejecutadas
| Comando exacto ejecutado | Resultado |
|---|---|
| | |

### Blockers
<Lista de bloqueos encontrados, con explicación concreta de por qué no se pudo ejecutar la tarea asociada. "N/A" si no hubo ninguno.>

### Archivos cambiados
<Lista final real de archivos tocados, con path absoluto — debe coincidir con el Inventario de Artefactos del contrato salvo desviaciones explicadas en Blockers.>

### Declaración final
<Una frase del agente implementador confirmando si el brief se ejecutó completo, parcialmente, o no se ejecutó, y por qué.>
```

Reglas para quien completa este reporte (el agente implementador, no el planificador):
- Marcar `[x]` **solo** cuando la tarea se ejecutó y se verificó (no al escribir el código, sino al confirmar que compila/corre según la Estrategia de Validación del contrato).
- Marcar `[BLOCKED]` con una explicación concreta en la columna "Detalle" cuando no se pudo ejecutar — nunca dejar una tarea sin marcar ni omitir la fila.
- **Nunca declarar una validación como ejecutada en la tabla "Validaciones ejecutadas" si no se corrió el comando exacto listado en la Estrategia de Validación del contrato.** No aproximar ("debería compilar") ni inferir el resultado sin haber corrido el comando.
