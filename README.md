# Prime Video: de un CRUD a una aplicación distribuida en tiempo real

Examen práctico de Aplicaciones Distribuidas. Se usa la base de datos **primevideodb (PostgreSQL)**, tabla `content`.

**Stack:** Java 21 · Spring Boot 4.1.2 (Web MVC, Data JPA, WebSocket/STOMP) · PostgreSQL 16 (Docker) · HTML + JavaScript.

## Evolución del proyecto (un commit y un tag por etapa)

| Tag | Etapa | Qué agrega |
|---|---|---|
| `etapa-1-crud` | CRUD básico | Listar (búsqueda, filtro y paginación), registrar, editar y eliminar contenidos |
| `etapa-2-websockets` | Tiempo real | Los cambios de un cliente aparecen en los demás sin recargar (STOMP sobre WebSocket) |
| `etapa-3-hilos` | Concurrencia | Estadísticas del catálogo calculadas en un pool de hilos, con progreso por WebSocket |

```bash
git checkout etapa-1-crud   # ver la versión inicial
git checkout main           # versión final
```

## Cómo ejecutar

Requisitos: Docker y Java 21.

```bash
docker compose up -d          # PostgreSQL con primevideodb (carga db/01_PrimeVideoDB_PostgreSQL.sql) + Adminer
./mvnw spring-boot:run        # en Windows: mvnw.cmd spring-boot:run
```

- Aplicación: http://localhost:8080. Ábrela en dos ventanas para ver la sincronización.
- Adminer (cliente web de BD): http://localhost:8081
  - Sistema: PostgreSQL
  - Servidor: `postgres`
  - Usuario y contraseña: `postgres` / `postgres`
  - Base de datos: `primevideodb`

Verificación de la carga:

```sql
SELECT COUNT(*) AS total_content FROM content;     -- 9668
SELECT * FROM content ORDER BY id LIMIT 10;
```

## Etapa 1: CRUD

| Método | Ruta | Operación |
|---|---|---|
| GET | `/api/content?buscar=&tipo=&pagina=&tamanio=` | Listar (más recientes primero) |
| GET | `/api/content/{id}` | Obtener |
| POST | `/api/content` | Registrar |
| PUT | `/api/content/{id}` | Editar |
| DELETE | `/api/content/{id}` | Eliminar. `content_genre`, `content_person` y `content_country` se borran por `ON DELETE CASCADE` |

- La columna `id` del script original no tiene autoincremento. `schema.sql` crea la secuencia `content_id_seq`, que continúa desde el id máximo.
- Si no se indica `source_id`, se genera como `s<id>`, siguiendo el mismo formato del dataset.
- `duration_text` se arma igual que en el dataset: `95 min`, `2 Seasons`.
- Las validaciones devuelven 400 con el error de cada campo; un `source_id` duplicado devuelve 409.

## Etapa 2: WebSockets

- **Endpoint STOMP:** `ws://host:8080/ws`
- **`/topic/content`:** `{accion: CREADO | ACTUALIZADO | ELIMINADO, id, contenido, cliente, timestamp}`
- **`/topic/clientes`:** número de clientes conectados.

El evento se publica con `@TransactionalEventListener`, es decir, **solo después del COMMIT**. Un cambio que termina en rollback nunca se notifica.

Cada navegador envía su identificador en la cabecera `X-Cliente`. Así los demás clientes ven quién hizo el cambio.

## Etapa 3: Hilos

`POST /api/estadisticas?hilos=N` responde **202 de inmediato**. A partir de ahí:

1. Un hilo **coordinador** (`coordinador-estadisticas-1`) carga los datos con JDBC: content, géneros, directores y reparto.
2. Divide los 9668 contenidos en N bloques.
3. Un pool de **hilos trabajadores** (`trabajador-estadisticas-K`) procesa los bloques en paralelo. Cada hilo:
   - cuenta contenidos por tipo, género, año y rating;
   - acumula duraciones;
   - para cada contenido, busca el más parecido de **todo** el catálogo. Usa la similitud de Jaccard sobre géneros, directores y reparto: unos 74 millones de comparaciones. Esto detecta, por ejemplo, la misma película en versión 4K o doblada a otro idioma.
4. Cada hilo trabaja sobre su propio `ResultadoParcial`, sin estado compartido. El coordinador los combina al final.
5. El progreso global y el de cada hilo se envía cada 250 ms por `/topic/estadisticas`, junto con el resultado final.

Para comparar existe `GET /api/estadisticas/sin-hilos`: hace exactamente el mismo cálculo de forma secuencial en el hilo de la petición HTTP, que queda bloqueada hasta terminar.

```bash
./scripts/comparar_tiempos.sh http://localhost:8080
```

| Modo | Respuesta HTTP | Cálculo | Speedup |
|---|---|---|---|
| sin hilos (bloqueante) | 6100 ms | 6025 ms | 1.00x |
| 1 hilo | 4 ms | 6166 ms | 0.98x |
| 2 hilos | 4 ms | 3657 ms | 1.65x |
| 4 hilos | 4 ms | 2051 ms | 2.94x |
| 8 hilos | 6 ms | 1300 ms | 4.63x |
| 12 hilos | 3 ms | 788 ms | 7.65x |

*(Medido en un equipo de 12 núcleos.)*

## Comparación entre versiones

1. **¿Qué cambió al incorporar WebSockets?**
   - En la versión inicial cada cliente solo veía los datos del momento en que cargó la página; para ver los cambios de otro usuario había que recargar.
   - Con WebSockets el servidor **empuja** cada cambio confirmado a todos los clientes conectados. La lista se actualiza sola (en ~0.5 s), con aviso de quién hizo el cambio y un historial de eventos.
   - La comunicación pasa de ser solo petición/respuesta iniciada por el cliente a ser bidireccional y en tiempo real.
2. **¿Qué ventaja dio usar hilos?**
   - **La petición no se bloquea:** el servidor responde en milisegundos (202) en lugar de dejar al cliente esperando 6 s sin información.
   - **El cálculo se reparte entre varios núcleos:** con 12 hilos tarda 0.79 s en vez de 6 s (7.65x).
   - **La aplicación sigue respondiendo:** mientras se procesa, el CRUD responde en ~10–30 ms y el usuario ve el avance de cada hilo en tiempo real.
3. **Diferencia entre la versión inicial y la mejorada:**
   - La versión inicial es un CRUD aislado por cliente, síncrono y secuencial.
   - La mejorada mantiene sincronizados a todos los clientes y ejecuta el trabajo pesado en segundo plano con concurrencia. Así la experiencia es reactiva y el tiempo de respuesta no depende de la duración del cálculo.

## Estructura

```
db/01_PrimeVideoDB_PostgreSQL.sql      script original de primevideodb
docker-compose.yml                     PostgreSQL 16 + Adminer
scripts/comparar_tiempos.sh            comparación sin hilos vs con hilos
src/main/java/com/examen/primevideo/
  content/        entidad, repositorio, servicio y controlador del CRUD     (etapa 1)
  websocket/      configuración STOMP y notificaciones                     (etapa 2)
  estadisticas/   procesamiento concurrente con hilos                      (etapa 3)
src/main/resources/static/              interfaz (index.html, app.js, styles.css, lib/stomp)
```
