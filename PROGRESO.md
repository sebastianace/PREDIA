# Progreso de Predia

## Fase 5 — Cierre del negocio: documentos del vendedor ✅ (19/11/2026)

**Rama:** `feature/cierre-negocio` (sale de `feature/inventario-presupuesto-credito`)

### Qué quedó
- Migración `V5__cierre_del_negocio.sql`: `propietario`, `negocio`, `requisito_catalogo`, `requisito_negocio` y `recordatorio`; el `negocio_id` de los documentos (Fase 2) ahora apunta a un negocio real.
- **Catálogo de requisitos** por inmobiliaria, sembrado con la lista base de 12 (cédula, certificado de tradición y libertad, escritura de adquisición, predial, valorización, administración, promesa de compraventa, levantamiento de hipoteca, cónyuge, poder especial, existencia y representación, servicios públicos). Cada uno trae su regla de cuándo aplica y su regla de vigencia; el ADMIN puede activarlos, desactivarlos, hacerlos recomendados (no bloquean) o cambiar los días de vigencia. La interfaz y la API repiten: *«Lista de referencia; confirma los requisitos con la notaría»*. Predia no da asesoría legal.
- **Motor de reglas** (`MotorReglasCierre`, Java puro, con "hoy" inyectable): en la base solo queda lo que hizo una persona (o la IA sugirió); el estado que se muestra —PENDIENTE, SUGERIDO, RECIBIDO, POR_VENCER, VENCIDO, NO_APLICA— se calcula siempre.
  - Vencido si `vence_el < hoy`; por vencer si vence antes de la firma o en menos de N días (parámetro de la inmobiliaria).
  - Vigencias: N días desde la expedición, año en curso (predial), mes en curso (administración).
  - Aplicación: propiedad horizontal, valorización (ciudades configuradas), hipoteca (solo si el asesor confirma que está vigente), casado o en unión marital, apoderado, persona jurídica.
  - **Semáforo:** «Listo para firmar» o «Faltan X documentos», con el porcentaje de avance. Los requisitos recomendados no bloquean.
- **Negocios y propietarios:** el propietario solo se guarda con su autorización de datos. Un negocio solo se abre sobre un inmueble en venta; al abrirlo se instancian los requisitos. `GET /api/negocios/{id}/estado-documental` devuelve semáforo, avance, advertencias y el detalle de cada requisito, con frases escritas en Java (las fechas y plazos nunca los redacta el modelo).
- **La IA solo sugiere:** cuando se indexa un documento de un negocio y la IA reconoce su tipo y su fecha, el requisito pasa a **SUGERIDO** (con el documento y la fecha propuesta). **Solo el asesor lo pasa a RECIBIDO**; si ya lo había decidido, la sugerencia no lo pisa; y si corrige el tipo del documento, la sugerencia se mueve al requisito correcto.
- **Recordatorios al propietario:**
  - La IA recibe **solo** la lista de pendientes que entregó el motor de reglas. Su texto se valida en Java: menciona todos los pendientes, **no menciona ningún documento que no esté pendiente** y no trae números que no salgan de la propia lista. Si falla la validación, o no hay IA, se usa una **plantilla determinista**.
  - Quedan SUGERIDOS en la bandeja: el asesor los edita (si menciona algo de más, se le avisa sin bloquearlo) y los aprueba. **`POST /aprobar` es la única forma de enviar un correo** (Spring Mail → Mailpit en desarrollo); el trabajo diario nunca envía.
  - Historial por negocio: qué se pidió, cuándo y quién lo aprobó. Si el envío falla queda `ERROR_ENVIO` y se puede reintentar.
- **Trabajo diario** (`@Scheduled`, 7:00 hora de Colombia, configurable con `CRON_RECORDATORIOS`): deja un borrador por cada negocio abierto con pendientes, sin duplicar y sin insistir si ya se envió uno hace menos de 3 días.
- **Resumen diario** `GET /api/resumen-diario`: negocios bloqueados y documentos que vencen esta semana (el asesor ve los suyos; el ADMIN, todos).
- **Datos semilla:** propietario Jorge Ramírez Pardo y el negocio del Apto 1102 Cañaveral con el certificado de tradición **vencido** (expedido hace 45 días) y el paz y salvo de administración **pendiente**: «Faltan 2 documentos», 67 %, y un borrador de recordatorio en la bandeja.
- **PDF de ejemplo** (`demo/pdfs/`, todos ficticios): contrato de arrendamiento, escritura, reglamento de propiedad horizontal, certificado de tradición y paz y salvo de administración. Se regeneran con:
  ```bash
  ./mvnw -q compile exec:java -Dexec.mainClass=co.predia.comun.demo.GeneradorPdfsDemo -Dexec.args="demo/pdfs"
  ```
- **Frontend:** negocios (lista con semáforo, alta con el vendedor y detalle con cada documento: marcar recibido con su fecha, no aplica, reabrir, subir un PDF), bandeja de recordatorios (editar, aprobar, descartar, historial), catálogo de requisitos y el resumen diario en el inicio. La navegación del celular pasa a 4 secciones + «Más».

### Dos mejoras que salieron de probar
- **Fragmentador:** solo reconocía los títulos (`CLÁUSULA…`, `ARTÍCULO…`) si venían separados por una línea en blanco, y casi ningún PDF las trae. Ahora los separa aunque vayan pegados al texto, sin confundir una línea en mayúsculas en mitad de una oración. Mejora la «sección» de las citas de cualquier documento.
- **Fechas en la interfaz:** `new Date("2026-12-01")` mostraba el 30 de noviembre en Colombia (UTC−5); las fechas sin hora ahora se leen como fechas locales.

### Pruebas (124 en total; las 55 nuevas de esta fase no llaman a OpenAI)
- `MotorReglasCierreTest` (13): **certificado expedido hace 31 días → VENCIDO; hace 25 días con firma en 10 → POR_VENCER**; paz y salvo de administración **NO_APLICA si el inmueble no es propiedad horizontal**; el último día de vigencia todavía vale; la IA nunca da algo por RECIBIDO; el semáforo.
- `CierreApiTest` (11): 12 requisitos de partida, vigencias, fecha obligatoria y no futura, llegar a «Listo para firmar», no aplica y reabrir, vendedor persona jurídica, catálogo del ADMIN, y **un usuario de otra inmobiliaria no ve ni toca negocios, ni sube documentos a un negocio ajeno**.
- `SugerenciaRequisitosTest` (3, con `ChatModel` mockeado): SUGERIDO y no RECIBIDO, no pisa lo que ya decidió el asesor, corrección de tipo.
- `RecordatoriosApiTest` (10, con `JavaMailSender` mockeado): **el recordatorio solo menciona documentos faltantes reales; no se envía nada sin aprobar** (`verifyNoInteractions`), ni desde el trabajo diario; aprobación, edición con advertencias, descarte, fallo de envío con reintento, aislamiento entre inmobiliarias.
- `RecordatorioConIaTest` (5): un texto de la IA solo se usa si pasa la validación; menciona un documento de más, olvida uno, inventa un número o falla → plantilla.
- `ValidadorYPlantillaTest` (6), `ResumenDiarioTest` (3), `GeneradorPdfsDemoTest` (2) y 2 pruebas nuevas del fragmentador.

### Cómo probarlo
1. `./mvnw spring-boot:run` y `npm run dev`; entra como `laura@altos.test`. El inicio ya muestra el negocio del 1102 con «Faltan 2 documentos».
2. **Negocios → Apto 1102 Cañaveral:** ves el semáforo, el 67 % y cada requisito con su estado. Marca el paz y salvo de administración como recibido con la fecha de hoy: queda **POR VENCER** (es «del mes en curso» y la firma es en otro mes).
3. **Recordatorios:** el borrador pide solo lo que falta. Apruébalo y míralo llegar en Mailpit (<http://localhost:8026>), con «responder a» la asesora. Verificado en vivo: Mailpit tenía 0 mensajes antes de aprobar y 1 después.
4. Con una API key de OpenAI: sube `demo/pdfs/certificado-tradicion-y-libertad.pdf` al negocio y la IA sugiere el requisito y la fecha, que tú confirmas.
5. `./mvnw test` (con Docker abierto).

### Qué falta
- Fase 6: el asistente con herramientas (`estadoDocumentalNegocio` y `redactarRecordatorioPropietario` reutilizan lo de esta fase; el recordatorio siempre queda SUGERIDO).
- La hipoteca la confirma el asesor a mano: la IA todavía no la sugiere a partir del certificado de tradición.

---

## Fase 4 — Inventario, clientes, presupuesto y crédito ✅ (19/11/2026)

**Rama:** `feature/inventario-presupuesto-credito` (sale de `feature/consulta-rag`; las Fases 2 y 3 siguen en PR abierto)

### Qué quedó
- Migración `V4__clientes_y_tasas.sql`: `cliente` (con `autorizacion_datos BOOLEAN CHECK (autorizacion_datos)`, fecha y asesor que la registró) y `tasa_credito`.
- **Fórmulas en Java, con pruebas** (nunca las calcula el modelo): `CalculadoraCredito` (tasa mensual `(1+EA)^(1/12)−1`, cuota `P·i/(1−(1+i)^−n)`, y su inverso) y `CalculadoraPresupuesto` (cuota máxima = 30 % del ingreso; tope = el menor entre `ahorro ÷ % cuota inicial` y `ahorro + lo que soporta la cuota máxima`). Todo en `BigDecimal`; los redondeos son conservadores (el tope y lo que soporta la cuota, hacia abajo).
- **Inventario:** `POST/PUT /api/inmuebles` (crear, editar y cambiar de estado) y `POST /api/inmuebles/busqueda` **en dos pasos**: primero SQL filtra lo no negociable (solo `DISPONIBLE`, de la inmobiliaria del usuario, dentro del presupuesto, con las habitaciones pedidas) y después pgvector ordena por similitud con lo que pidió el cliente, con el porcentaje de coincidencia. Los vectores se calculan al crear o editar y, si faltan, al buscar. Sin IA se ordena por precio, sin porcentaje, con un aviso; y una edición sin IA borra el vector viejo para no dejar uno desactualizado.
- **Clientes:** CRUD con la autorización de datos obligatoria (422 sin ella), derecho de supresión (`DELETE`) y `POST /api/clientes/extraer-perfil`: la IA propone el perfil con salida estructurada, Java lo valida (descarta negativos y vacíos) y queda **SUGERIDO**: no guarda nada, el asesor lo revisa en el formulario.
- **Presupuesto:** `GET /api/clientes/{id}/presupuesto?plazoAnios=&tasaId=&vis=` con la tasa usada, el plazo, los porcentajes aplicados y avisos. Las deudas mensuales **no se restan** de la cuota máxima: generan una alerta cuando cuota máxima + deudas superan el 40 % del ingreso (umbral de referencia, no una regla legal).
- **Crédito:** `GET/POST/PUT/DELETE /api/tasas` (escritura solo para el ADMIN, verificada antes de validar el cuerpo) y `POST /api/creditos/simular` con `monto`, o con `inmuebleId` + `clienteId` (financia precio − ahorro). Devuelve cuota, % sobre el ingreso, si cumple la cuota máxima y la cuota inicial mínima, la tasa usada con su banco, fuente y fecha, y el aviso «Es una simulación; la aprobación es del banco». Si no hay una tasa vigente cargada, **no inventa una**: responde 422.
- **Datos semilla** (incrementales: se agregan a una base que ya existía): cliente Paola y Juan (ingreso $7.500.000, ahorro $90.000.000, con autorización) y Banco Ejemplo, 12 % E.A., marcada «de ejemplo».
- **Corrección:** un JSON mal formado devolvía 500; ahora responde 400 (lo descubrí al probar).
- **Frontend:** Clientes (lista, alta/edición con «Leer con IA» como sugerencia y casilla de autorización), detalle del cliente (presupuesto con plazo, tasa y VIS; búsqueda de inmuebles; simulador por inmueble), Tasas (el ADMIN las carga) y alta/edición de inmuebles.

### Pruebas (69 en total; las 39 nuevas no llaman a OpenAI)
- `CalculadoraCreditoTest` y `CalculadoraPresupuestoTest` (11): **crédito de $210.000.000 al 12 % E.A. a 240 meses → cuota ≈ $2.223.000**; **ingreso $7.500.000 y ahorro $90.000.000 → cuota máxima $2.250.000 y tope ≈ $300.000.000**; la tasa mensual se verificó contra un cálculo de 40 dígitos; las deudas no restan; límites e inválidos.
- `CreditoApiTest` (8), `ClienteApiTest` (6), `ExtraccionPerfilTest` (4, con `ChatModel` mockeado), `InventarioApiTest` (7) e `InventarioBusquedaIaTest` (3, con `EmbeddingModel` mockeado): permisos del ADMIN, aislamiento entre inmobiliarias, autorización de datos, **un inmueble no disponible nunca se ofrece (aunque encaje mejor)**, orden por similitud con porcentaje, y que nada se invente sin tasa.

### Cómo probarlo
1. Backend y frontend corriendo; entra como `laura@altos.test` → **Clientes → Paola y Juan**: el presupuesto da cuota máxima $2.250.000 y tope $300.000.000.
2. «Buscar inmuebles dentro del presupuesto»: salen Ciudadela, Provenza y Cañaveral (la Casa Lagos II de $330 M, el Apto Cabecera vendido y el de Real de Minas arrendado quedan fuera).
3. «Simular crédito» en el Apto 1102 Cañaveral: cuota **$2.223.109** (29,6 % del ingreso), cumple la cuota máxima y la cuota inicial. Verificado en vivo contra PostgreSQL real, tras aplicar la migración V4 sobre la base que ya existía.
4. `./mvnw test` (con Docker abierto).

### Cosas a tener en cuenta
- El «% de coincidencia» es la similitud coseno entre el vector de la descripción de cada inmueble y el de lo que pidió el cliente. Sirve para **ordenar**; con `text-embedding-3-small` los valores típicos son moderados (no esperes 90 % en cosas parecidas).
- Con IA, el texto pegado para «Leer con IA» se envía al modelo para extraer el perfil, pero no se almacena.
- La tasa VIS no tiene semilla (solo hay la de 12 % no VIS): para un inmueble VIS hay que cargarla en la pantalla de Tasas.

### Qué falta
- Fase 5: cierre del negocio (catálogo de requisitos, semáforo, recordatorios con aprobación y Mailpit).

---
## Fase 3 — Consulta RAG híbrida con citas ✅ (19/11/2026)

**Rama:** `feature/consulta-rag` (sale de `feature/indexacion-asincrona`, con la Fase 2 ya adentro)

### Qué quedó
- Migración `V3__consulta_rag.sql`: trazabilidad de cada pregunta (quién, qué fragmentos se usaron, si hubo que decir "no lo sé").
- **Búsqueda híbrida** (`BusquedaHibrida`, SQL explícito con `JdbcTemplate`): una consulta vectorial (`pgvector`, `<=>`) y una textual (`tsvector` en español, `websearch_to_tsquery`), **siempre** filtradas por `inmobiliaria_id` y por `rol = ANY(roles_permitidos)`. Las dos listas de hasta 20 resultados se combinan con **Reciprocal Rank Fusion** (`FusionRRF`, lógica pura con sus propias pruebas) y se eligen los 5 mejores, con un máximo de 2 por página.
- **Motor RAG** (`MotorRag`):
  - Si no hay evidencia (ni similitud por encima del umbral configurado por inmobiliaria, ni coincidencia de texto), responde "no tengo información suficiente" **sin llamar al modelo de chat**.
  - Si hay IA, el modelo recibe solo los fragmentos recuperados, numerados `[1]…[5]`, y responde citándolos. Las citas que devuelve la API se arman a partir de los metadatos de los fragmentos (documento, página, sección), no del texto libre del modelo; **los números que el modelo cite pero no correspondan a evidencia real se descartan**.
  - Sin IA (modo manual), se muestra la evidencia encontrada por texto, sin una respuesta redactada.
  - Cada consulta queda registrada en `consulta_rag` (pregunta, respuesta, fragmentos usados, si fue "sin evidencia" o "modo manual").
- `POST /api/consultas` `{pregunta, inmuebleId?}` → `{respuesta, citas[], sinEvidencia, modoManual}`.
- **Frontend:** pantalla "Expedientes" — selector de inmueble, subida de PDF (con su estado de indexación, que se refresca solo mientras hay algo en cola o procesando), y un cuadro para preguntarle al expediente, con las citas mostradas debajo de la respuesta.

### Pruebas (30 en total; las 7 nuevas no llaman a OpenAI)
- `FusionRRFTest` (3): lo que aparece bien ubicado en ambas listas queda primero; una lista vacía no rompe la fusión; el orden dentro de una lista pesa más que aparecer en una sola.
- `ConsultaRagApiTest` (4, con `EstadoIa`, `EmbeddingModel` y `ChatModel` **mockeados**): con evidencia relevante responde y descarta una cita inventada por el modelo (`[9]`, que no existe); sin evidencia suficiente responde "no sé" **sin invocar el chat** (verificado con `verify(chatModel, never())`); sin IA disponible muestra la evidencia sin redactar respuesta; una inmobiliaria nunca ve fragmentos de otra, aunque tengan el mismo vector.

### Cómo probarlo
1. Con el backend y el frontend corriendo, entra a **Expedientes**, elige un inmueble con documentos ya indexados y pregúntale algo.
2. Verificado en vivo (API real, PostgreSQL con pgvector real): con el contrato de la Fase 2 ya indexado, preguntar `contrato arrendamiento Floridablanca` devuelve la cita exacta (documento, página 1, sección "CONTRATO DE ARRENDAMIENTO DE VIVIENDA URBANA"); una pregunta sin ninguna palabra en común con el texto responde "no tengo información suficiente", sin inventar nada.
3. Nota sobre el modo manual: la búsqueda de texto sin IA usa `websearch_to_tsquery`, que exige que las palabras de la pregunta aparezcan en el documento (no entiende sinónimos). Con la IA conectada, la búsqueda vectorial no tiene ese problema.
4. Pruebas automatizadas (con Docker abierto):
   ```bash
   ./mvnw test
   ```

### Qué falta
- Fase 4: inventario completo (crear/editar inmuebles, búsqueda por presupuesto), perfil del cliente, presupuesto y simulador de crédito.
- El asistente con herramientas (Fase 6) reutilizará `MotorRag` como una de sus herramientas (`consultarExpedientes`).

---

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
