# Progreso de Predia

## Fase 2 — Indexación asíncrona de documentos ✅ (19/11/2026)

**Rama:** `feature/indexacion-asincrona` (sale de `develop`, que ya integró la Fase 1)

### Qué quedó
- Migración `V2__expedientes.sql`: `documento`, `trabajo_indexacion` y `fragmento` (con `vector(1536)` + índice HNSW, y `tsvector` en español + índice GIN para la Fase 3).
- **Subida:** `POST /api/documentos` (multipart). Valida tipo, tamaño, que el PDF se pueda abrir y que no esté cifrado; calcula el SHA-256; si el hash ya existe en la inmobiliaria, no duplica el trabajo (devuelve 200 en vez de 202). El archivo se guarda solo en un directorio temporal.
- **Procesador asíncrono** (`ProcesadorIndexacion`, `@Scheduled` cada 5 s, sin brokers externos): toma trabajos `PENDIENTE` con un bloqueo `FOR UPDATE SKIP LOCKED` sobre la propia tabla.
  - **Extracción:** Apache PDFBox, página por página.
  - **OCR:** cuando una página no tiene texto útil (menos de 30 caracteres), se renderiza a 300 DPI y se le aplica Tesseract (tess4j) en español. Nunca se aplica OCR a páginas que ya tienen texto.
  - **Fragmentación:** `Fragmentador` divide el texto en fragmentos de 500 a 1.000 tokens (jtokkit, `cl100k_base`), con solape de ~100 tokens entre fragmentos consecutivos. Detecta encabezados (`CLÁUSULA…`, `ARTÍCULO…`, títulos en mayúsculas) para guardar la sección. Es lógica pura, sin Spring: fácil de probar.
  - **Embeddings:** si hay IA disponible (`EstadoIa`), se generan por lotes de 64 a través de `ProveedorEmbeddings` (interfaz; implementación `OpenAiProveedorEmbeddings`) y se escriben con SQL explícito (`JdbcTemplate` + `::vector`), nunca con el `PgVectorStore` de Spring AI. Sin IA, los fragmentos quedan guardados sin vector (modo manual) y `documento.vectorizado = false`.
  - **Clasificación:** solo con los primeros 3 fragmentos (nunca el PDF completo), con salida estructurada (`ChatClient.entity(...)`). Si falla o no hay IA, el documento igual queda `INDEXADO`; la clasificación se queda en `SIN_CLASIFICAR`.
  - **Reintentos:** hasta 3, con backoff. Al fallo definitivo se marca `ERROR` y se borra el temporal; en los reintentos no (así puede reprocesarse).
  - **Verificación y borrado:** se cuenta que los fragmentos guardados coincidan con los generados antes de marcar `INDEXADO`; el PDF temporal se borra siempre al terminar (éxito o fallo definitivo).
- **Al arrancar** (`LimpiadorArranque`): los trabajos que quedaron `EN_CURSO` por un reinicio vuelven a `PENDIENTE`, y se borran los archivos temporales huérfanos.
- `PUT /api/documentos/{id}/clasificacion`: el asesor confirma o corrige el tipo y la fecha (exige que el documento ya esté `INDEXADO`).
- `GET /api/documentos`, `GET /api/documentos/{id}`, `DELETE /api/documentos/{id}` (borra también sus fragmentos, por `ON DELETE CASCADE`).

### Pruebas (23 en total; las 15 nuevas de esta fase no llaman a OpenAI)
- `FragmentadorTest` (6): tamaño de los fragmentos, solape entre consecutivos, sección por encabezado, página que cruza un fragmento, marca de OCR, división de un párrafo gigante sin pasar el máximo.
- `ServicioOcrTest` (1): reconoce una palabra clara generada en una imagen, con el Tesseract instalado en la máquina.
- `IndexacionAsincronaTest` (5, sin IA): sube y procesa un PDF real (texto extraíble directamente), borra el temporal, no duplica un PDF ya subido, rechaza un archivo que no es PDF, aísla por inmobiliaria, y procesa correctamente una página **escaneada** (imagen sin texto) marcándola `es_ocr = true`.
- `IndexacionConIaDisponibleTest` (1): con `EstadoIa`, `EmbeddingModel` y `ChatModel` **mockeados** (nunca llama a OpenAI) — los fragmentos quedan vectorizados, y una clasificación que falla no tumba la indexación.
- `DocumentoApiTest` (2): una inmobiliaria no ve ni borra los documentos de otra; no se puede confirmar la clasificación de un documento que no está `INDEXADO`.

### Cómo probarlo
1. Con el entorno levantado (`docker compose up -d`, `./mvnw spring-boot:run`), inicia sesión y sube un PDF:
   ```bash
   curl -X POST http://localhost:8080/api/documentos \
     -H "Authorization: Bearer $TOKEN" \
     -F "archivo=@contrato.pdf" -F "inmuebleId=1"
   ```
2. En unos segundos (el job corre cada 5 s): `GET /api/documentos/{id}` debe mostrar `"estadoIndexacion": "INDEXADO"`.
3. Verificado en vivo con un contrato real generado para la prueba (`textutil`/`cupsfilter` → PDF): el hash SHA-256 guardado coincide con el del archivo, el fragmento conserva la sección ("CONTRATO DE ARRENDAMIENTO…") y el archivo temporal desaparece del disco al terminar.
4. Pruebas automatizadas (con Docker abierto):
   ```bash
   ./mvnw test
   ```

### Qué falta
- Fase 3: consulta RAG híbrida (vectorial + texto) con citas, y la pantalla de expedientes en el frontend.
- La clasificación todavía no actualiza el estado de un requisito del cierre (`negocio_id`): eso llega con la Fase 5, cuando exista la tabla `negocio`.

---

## Fase 1 — Esqueleto, autenticación y multi-inmobiliaria ✅ (19/11/2026)

**Rama:** `feature/esqueleto-auth`

### Qué quedó
- Proyecto Spring Boot 3.5.16 (Java 21) con Spring AI 1.1.8, Maven Wrapper y springdoc.
- `docker-compose.yml` con PostgreSQL 16 + pgvector (puerto 5434) y Mailpit (SMTP 1026, web 8026). Los puertos se cambiaron porque 5433, 1025 y 8025 ya estaban ocupados en el equipo.
- Migración Flyway `V1__base.sql`: `inmobiliaria`, `usuario`, `parametros_inmobiliaria` e `inmueble`. El inmueble ya trae su columna `vector(1536)` con índice HNSW, porque los expedientes (Fase 2) dependen de él.
- Autenticación JWT (HS256 con Nimbus) con roles ADMIN y ASESOR. La inmobiliaria se toma del token.
- Endpoints:
  - `POST /api/auth/login`
  - `GET /api/auth/yo`
  - `GET /api/sistema/estado`
  - `GET/PUT /api/parametros` (el PUT es solo para ADMIN)
  - `GET /api/inmuebles`
  - `GET /api/inmuebles/{id}`
- Modo manual: con `OPENAI_API_KEY` vacía la aplicación arranca y `/api/sistema/estado` devuelve el aviso.
- Datos semilla: Altos Inmobiliaria, los usuarios admin y Laura, los parámetros y los 6 inmuebles.
- Frontend (React 19 + Vite 8 + Tailwind 4): login, inicio, inventario, aviso de modo manual y navegación inferior en celular.

### Identidad visual
Se rediseñó la interfaz con la idea de un **plano arquitectónico**:
- **Colores:** azul de Prusia `#0B2545` para la marca, cobalto `#1F4FD1` para lo que se puede tocar o está disponible, papel `#F5F7FA` de fondo, superficies blancas y texto en negro.
- **Tipografía:** Archivo (variable), servida desde el propio proyecto. La versión ancha se usa en títulos y precios.
- **Detalle distintivo:** el plano en planta de un apartamento que se dibuja al entrar al login. El resto de la interfaz es sobrio.
- **Inicio:** una barra con un segmento por inmueble, coloreado según su estado, y la lista de inmuebles para ofrecer.
- **Inventario:** filtros por estado y fichas con precio, administración y características.

### Pruebas (8, todas pasan)
- `AutenticacionTest`:
  - login correcto;
  - contraseña incorrecta (401 genérico);
  - sin token o con token falso (401);
  - `/yo`;
  - aviso de modo manual.
- `AislamientoInmobiliariasTest`:
  - un usuario de otra inmobiliaria no ve inmuebles ajenos, y un id ajeno responde 404;
  - el ASESOR no cambia parámetros (403, incluso con un cuerpo inválido);
  - el ADMIN solo cambia los parámetros de su inmobiliaria.

### Cómo probarlo
1. Levanta el entorno:
   ```bash
   cp .env.example .env
   ```
   Pon un `JWT_SECRET` propio y después:
   ```bash
   docker compose up -d
   ```
   ```bash
   ./mvnw spring-boot:run
   ```
   ```bash
   cd frontend && npm install && npm run dev
   ```
2. Entra a <http://localhost:5173> con `laura@altos.test` y la contraseña de `DEMO_PASSWORD`. Verás 4 inmuebles disponibles de 6 y el aviso de modo manual si no hay API key.
3. Abre <http://localhost:8080/swagger-ui.html> para ver la API.
4. Corre las pruebas con Docker abierto:
   ```bash
   ./mvnw test
   ```

### Qué falta (siguientes fases)
- Fase 2: subida de PDF, indexación asíncrona, OCR, fragmentos, embeddings y borrado del temporal.
- Los datos semilla de clientes, tasas, negocio del 1102 y PDF de ejemplo llegan con las tablas de sus fases (4 y 5).
