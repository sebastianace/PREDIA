# Progreso de Predia

## Extra — Hoja impresa del estudio de reducción de crédito ✅ (25/11/2026)

**Rama:** `feature/impresion-estudio-reduccion` (sale de `develop`). Pedido por el profesor, que dio como ejemplo la impresión del simulador original del autor. Boceto aprobado antes de programar.

- **Nueva hoja** (una página A4): encabezado con la marca de la inmobiliaria y los datos del cliente, un bloque «Condiciones actuales con el banco» con el crédito actual y hasta tres escenarios lado a lado (saldo, cuotas y tiempo pendientes, tasa, abono, valor de la cuota y del seguro, nueva cuota, total de la deuda, abono a capital e intereses de la primera cuota), un bloque «Dinero y tiempo ahorrado» (intereses, seguros, total, cuotas reducidas, tiempo ahorrado, ingreso requerido y si el ingreso del cliente alcanza) y las firmas. Identidad de Predia (azul de Prusia, Archivo, curvas de nivel suaves); no se copió la marca del ejemplo.
- **Backend:** la simulación devuelve `primeraCuotaCapital` y `primeraCuotaIntereses` (cómo se reparte la primera cuota), calculados por `CalculadoraReduccion`; el frontend no calcula nada.
- **Defecto de la impresión anterior, corregido:** lo que no era el estudio solo se ocultaba (`visibility`), pero su altura seguía ocupando páginas: el PDF salía con **3 páginas, 2 en blanco**. Ahora se quita del diseño al imprimir y sale **1 página**. También se activó `print-color-adjust` para que las franjas de color salgan en papel.
- **Verificado** generando el PDF real con el navegador en modo impresión: plazo/cuota, abono mensual/anual, con y sin cliente y con seguro. Con los abonos del ejemplo (`$104.700`, `$149.571`, `$209.399`) las cuotas y los desembolsos coinciden con el PDF del profesor; los ahorros difieren por unos pesos (p. ej. `$44.766.975` frente a `$44.767.103`) porque Predia redondea cada mes al peso y los totales son la suma de la tabla. El ingreso requerido usa el porcentaje máximo de cuota que configura la inmobiliaria (30 %), no el 33,33 % del ejemplo.
- **Fuera por ahora (sin decidir):** honorarios del asesor (3 % del ahorro + IVA, modelo de corredor), «estudio válido por 20 días» y la fila «# de veces pagada la deuda / cuotas pagas» del ejemplo, que no se entendió.

## Prueba completa antes de la release 1.2.0 ✅ (25/11/2026)

**Rama:** `fix/asistente-completo`. Antes de publicar la 1.2.0 se probó todo junto en una **base nueva** (arranque desde cero: 9 migraciones y demo sembrada) con la rama de la release y Ollama, con un script que recorre 90 comprobaciones por rol (sin Ollama el script no aplica: usa los cuatro roles y el asistente real).

- **Cubre:** permisos de los cuatro roles (sin token 401; asesor sin acceso a usuarios, parámetros ni tasas; propietario y comprador solo a lo suyo, y 404 ante lo ajeno), cálculos (presupuesto, cuota de $2.438.073 a 15 años, reducción de crédito, tasa mal escrita rechazada), búsqueda por significado, indexación y RAG con cita y sin evidencia, escaneo de archivos (imagen disfrazada de PDF, `.txt`, sin archivo), galería de imágenes, cierre (la IA deja SUGERIDO y el asesor confirma o reabre), recordatorio redactado y aprobado (llega a Mailpit), las tres preguntas del asistente y ambos portales completos (invitación por correo, enlace de un solo uso, activación, login, subida del propietario, ficha del comprador sin código ni datos del propietario, simulador, solicitud y atención).
- **Resultado:** 89 de 90 comprobaciones. Las fallas de la primera corrida (84 comprobaciones) eran del script, no de la aplicación: la forma de la respuesta del estado documental y que el simulador del comprador exige `cuotaInicial`; el asistente, en cambio, sí tenía una inconsistencia real (ver más abajo). La comprobación que queda en rojo («tras confirmar el paz y salvo, faltan 1») no es un error: es la regla de vigencia del paz y salvo (ver más abajo); el resto de la cadena SUGERIDO → RECIBIDO → reabrir sí se verificó.
- **Interfaz:** las 10 pantallas del asesor sin desbordar a 375 px de ancho, pantalla de ingreso y cierre del negocio revisados en celular.
- **Asistente más consistente (hallazgo):** «¿Qué les alcanza?» solo usaba `buscarInmuebles` en 3 de 5 corridas y una vez respondió solo con el tope, sin inmuebles. La instrucción ahora manda siempre `buscarInmuebles` con `paraCliente` (ya devuelve el tope): 12 de 12 respuestas completas, sin cifras sin respaldo.
- **Temperatura de Ollama en 0,2** (`OLLAMA_TEMPERATURE`): uso factual, respuestas más estables. En «¿La venta del 1102 incluye parqueadero?» pasó de 3 «sí» de 5 a 5 de 6; la variación restante sale de qué fragmentos recupera.
- **Vigencia del paz y salvo:** es «del mes en curso» y la firma tentativa es en diciembre, así que un paz y salvo de noviembre queda «por vencer» y el negocio sigue en «Faltan 2». Es la regla funcionando; se explica en el guion de demo.
- Todo se hizo en bases nuevas (borradas al terminar) y Mailpit quedó vacío; la base de desarrollo no se tocó.

## Preparación de la presentación ✅ (25/11/2026)

**Rama:** `fix/seccion-en-citas` (sale de `develop`). Ensayo completo del recorrido de la demo en la instancia real (Ollama, PostgreSQL y Mailpit), por la API y por la interfaz.

- **Umbral de similitud en 0,35** (parámetro de la inmobiliaria; no tiene pantalla, se cambia con `PUT /api/parametros` como ADMIN): con `bge-m3` corta lo ajeno y conserva las paráfrasis (ver la sección de Ollama).
- **Citas con la sección correcta (error encontrado al ensayar).** El fragmentador guardaba como sección el *último* encabezado visto; como los PDF de la demo caben en un solo fragmento, toda cita decía «CLÁUSULA QUINTA. DEPÓSITO» aunque el parqueadero estuviera en otra cláusula. Ahora cada bloque recuerda su sección y el fragmento cita lo que contiene: una sola, o el rango «primera — última». También se corrigió que el encabezado del bloque siguiente se le atribuyera al fragmento que se cerraba. 3 pruebas nuevas y 3 ajustadas; los PDF ya indexados deben subirse de nuevo para tener la sección nueva.
- **Recordatorios mejor redactados con Ollama (error encontrado al ensayar).** `qwen3:8b` escribió «le recuerdo que aún pendiente los siguientes documentos». La instrucción ahora fija la estructura del correo y pide frases completas con verbos conjugados; en tres borradores seguidos salieron gramaticales y consistentes. El validador sigue exigiendo todos los documentos pendientes y ningún número nuevo, y si falla se usa la plantilla.
- **Modelos cargados 2 horas** (`OLLAMA_KEEP_ALIVE`): Ollama los descarga a los 5 minutos y la primera pregunta tras una pausa tardaba mucho más.
- **Lista de comprobación antes de presentar** en el README (guion de demo).

### Ensayo (todo con Ollama: `qwen3:8b` + `bge-m3`)
- RAG: parqueadero → sí, con cita; canon, duración y mascotas → respuesta con cita; «mundial de 2014» → «no tengo información» **sin llamar al modelo** (0,04 s).
- Asistente (6–15 s cada una, `cifrasSinRespaldo` vacío): las tres preguntas del profesor, y «redacta un recordatorio» dejó un BORRADOR sin enviar nada.
- Cierre: al subir el paz y salvo al negocio, el modelo lo clasificó (`PAZ_Y_SALVO_ADMINISTRACION`, 0,95) y el requisito quedó **SUGERIDO** por la IA; el semáforo siguió en «Faltan 2 documentos» hasta que el asesor lo confirme. Se reabrió y se borró el documento.
- Interfaz: inicio, expedientes (con la cita), clientes, negocios, recordatorios y asistente sin errores de consola; el asistente muestra «Consultando las herramientas de Predia…» durante la espera.
- Suite: 264 pruebas, todas pasan.
- Todo lo hecho en el ensayo se deshizo: documentos, borradores, requisitos y Mailpit quedaron como al empezar.

## Extra — IA local con Ollama ✅ (25/11/2026)

**Rama:** `feature/proveedor-ollama` (sale de `develop`). Diseño aprobado antes de programar. Predia puede correr con modelos locales: nada de lo que se indexa o se pregunta sale del equipo y no hay costo por uso.

- **Selector de proveedor:** `PREDIA_IA_PROVEEDOR=openai|ollama` (por defecto `openai`). Se usan `spring.ai.model.chat` y `spring.ai.model.embedding` de Spring AI; `EstadoIa` ya no depende de propiedades de OpenAI, expone el `proveedor` y falla al arrancar si chat y embeddings no coinciden o el valor es desconocido. `GET /api/sistema/estado` incluye ahora `proveedor`.
- **Modelos por defecto:** chat `qwen3:8b` y embeddings `bge-m3` (1.024 dimensiones). Predia no descarga modelos: se bajan con `ollama pull`.
- **Sin migración:** `AjusteDimension` rellena con ceros los vectores de menos de 1.536 dimensiones. Con el relleno la similitud coseno no cambia; un vector más largo se rechaza en vez de recortarse. Volver a OpenAI sigue siendo posible regenerando los vectores.
- **Modo de pensar:** `qwen3` «piensa» antes de responder y gasta unas diez veces más tokens (40–78 s por respuesta). `AjustesOllama` lo apaga por defecto (`OLLAMA_PENSAR=false`); Spring no permite enlazar ese valor desde `application.yml`.
- **Renombrado:** `OpenAiProveedorEmbeddings` pasó a `SpringAiProveedorEmbeddings`, porque sirve para cualquier proveedor.

### Pruebas (12 nuevas, 262 en total; no necesitan Ollama ni OpenAI)
- `AjusteDimensionTest` (4): la dimensión exacta no se toca, un vector de 1.024 se rellena con ceros, el coseno no cambia, uno de 3.072 se rechaza.
- `EstadoIaTest` (5): OpenAI sin clave = modo manual, con clave disponible, el marcador no cuenta, Ollama sin clave y con sus modelos, proveedor desconocido o mezclado impide arrancar.
- `AjustesOllamaTest` (3): pensar apagado y encendido, y los demás beans quedan igual.

### Probado en vivo con Ollama real (M5, 16 GB)
- Indexación de la escritura y el reglamento con `bge-m3`: vectores de 1.536 dimensiones en `fragmento` y documentos clasificados bien (`ESCRITURA_PUBLICA`, `REGLAMENTO_PROPIEDAD_HORIZONTAL`).
- **Las tres preguntas del profesor con `qwen3:8b`** (8–17 s cada una con el modo de pensar apagado): el estado del negocio 1102 (certificado vencido el 04/11 y paz y salvo pendiente), lo que alcanza Paola y Juan (herramienta `calcularPresupuesto`: $300.000.000, y el Apto 1102) y el parqueadero (sí, con cita al reglamento). `cifrasSinRespaldo` vacío en las tres.
- **Con el modo de pensar encendido**, la misma pregunta de presupuesto no usó `calcularPresupuesto` y recomendó una casa de $330.000.000, por encima del tope; con él apagado sí la usó. Es variación normal de un modelo de 8.000 millones de parámetros: conviene volver a probar antes de la demo.
- **`llama3.1`** respondió más corto, no usó `calcularPresupuesto` en la pregunta de presupuesto y dudó en la del parqueadero: se queda como alternativa, no como valor por defecto.

### Umbral de similitud (por modelo)
Con `bge-m3` el umbral por defecto de 0,30 deja pasar preguntas ajenas («capital de Francia» devolvió citas aunque el modelo dijo que no sabía). En el conjunto de la demo, **0,35–0,40** corta lo ajeno y conserva las paráfrasis («¿dónde puedo dejar mi carro?» encuentra el parqueadero); 0,45 ya pierde paráfrasis y 0,50 pierde preguntas directas. El umbral es un parámetro de cada inmobiliaria (lo cambia un ADMIN): al cambiar de modelo hay que recalibrarlo. Los valores salen de una demo de dos documentos; con más documentos conviene medir de nuevo.

### Lo que no se hizo
- No hay comprobación de que el servidor de Ollama esté encendido al arrancar: si está apagado, el asistente responde 502 y la indexación reintenta, igual que con un proveedor en la nube caído.
- La prueba se hizo en una copia de la base de desarrollo (borrada al terminar), sin tocar el 8080 ni enviar correos.

## Extra — Escaneo de archivos ✅ (24/11/2026)

**Rama:** `feature/escaneo-archivos` (sale de `develop`). Diseño aprobado antes de programar. Cierra lo que faltaba del punto 4 del flujo («límites de tamaño, validación y escaneo de archivos»).

- **Revisión del PDF** (`InspectorPdf`, siempre activa): firma `%PDF-` en los primeros 1.024 bytes (la extensión y el tipo que declara el navegador no bastan) y rechazo de JavaScript, acciones que lanzan programas y archivos incrustados, recorriendo todos los objetos del documento (también los comprimidos).
- **Antivirus ClamAV opcional** (`EscanerClamAv`): el archivo viaja a `clamd` por el comando `INSTREAM`. Apagado por defecto (`ESCANEO_MODO=DESACTIVADO`) para que Predia siga arrancando con solo PostgreSQL. Con `CLAMAV` y `ESCANEO_OBLIGATORIO=true`, si el antivirus no responde el archivo se rechaza con 503: nunca se da por limpio lo que no se pudo verificar.
- **Efecto de un rechazo:** 422 «rechazado por seguridad» (sin el nombre de la amenaza) y no queda documento, trabajo ni temporal. En el log solo van el hash y el nombre de la amenaza.
- **Docker:** servicio `clamav` bajo el perfil `escaneo` (`docker compose --profile escaneo up -d clamav`). Se usa `clamav/clamav-debian` porque la imagen `clamav/clamav` solo publica amd64 y el Mac de desarrollo es Apple Silicon.

### Pruebas (19 nuevas, 250 en total; no llaman a OpenAI ni necesitan ClamAV)
- `InspectorPdfTest` (8): un PDF normal y un enlace web pasan; JavaScript al abrir, JavaScript dentro de una anotación, `Launch` y archivos incrustados se rechazan; la firma distingue un PDF de una imagen o un ejecutable con extensión `.pdf` y tolera basura corta antes de la firma.
- `EscanerClamAvTest` (6): el protocolo `INSTREAM` contra un `clamd` falso en un socket (el archivo llega completo; `OK`, `FOUND` con el nombre de la amenaza, `ERROR` nunca se da por limpio; sin antivirus, obligatorio o no).
- `CargaSeguraApiTest` (5, PostgreSQL real): archivo limpio → 202; amenaza → 422 sin documento ni temporal y sin nombrar la amenaza; antivirus caído → 503 sin documento ni temporal; PDF con JavaScript → 422 aunque el antivirus no lo vea; un PNG con extensión `.pdf` → 422.

### Probado en vivo con ClamAV real (Docker, arm64)
- Un archivo EICAR (la cadena de prueba estándar de los antivirus, que no es un virus) subido como `.pdf` → **422 «rechazado por seguridad»** y una línea en el log con el hash y `Eicar-Test-Signature`.
- Un PDF con JavaScript en `/OpenAction` → **422 «El PDF contiene JavaScript»**.
- Con el contenedor de ClamAV detenido → **503 «No se pudo verificar el archivo…»**; al volver a encenderlo, el mismo archivo limpio se aceptó (202).
- **Límite que conviene saber:** ClamAV reconoce el archivo EICAR cuando es el archivo completo; pegado al final de un PDF real no lo marcó. Es una propiedad de esa firma de prueba, no de Predia; por eso la revisión estructural del PDF va además del antivirus.
- La prueba se hizo en una copia de la base de desarrollo, que se borró al terminar.

### Lo que no se hizo
- No se escanean las fotos y planos de la galería: no se guardan los bytes originales, solo la imagen decodificada y vuelta a codificar como JPEG (`ProcesadorImagen`).
- No hay un aviso en la interfaz de que un archivo se subió «sin verificar» cuando el antivirus está apagado.

## Extra — Pulido visual de Negocios, Clientes y Activar cuenta ✅ (24/11/2026)

**Rama:** `feature/rediseno-pantallas` (sale de `feature/galeria-inmuebles`). Boceto aprobado antes de programar; Sebastián pidió el cambio del panel azul de «Activar cuenta» (un dibujo en vez de curvas sueltas) y se mostraron dos opciones: eligió el edificio.

- **Negocios:** cada negocio es una tarjeta con cabecera azul de Prusia y curvas de nivel, semáforo, avatares de quien vende y quien compra, la cota de avance y la firma tentativa. Arriba, tres resúmenes (negocios abiertos, documentos por resolver, próxima firma) que son sumas de lo que ya entrega el servidor.
- **Clientes:** tarjeta con avatar de iniciales, franja cobalto, etiquetas (intención, zona, habitaciones) y, a la derecha, el ingreso y el ahorro.
- **Activar cuenta:** el panel azul lleva la **fachada de un edificio dibujada con líneas blancas** (pisos, ventanas, entrada, suelo y una cota vertical que marca el «Apto 1102»), que se traza al entrar; la parte del formulario es una tarjeta con pasos, lo que podrá hacer el propietario o el comprador, mostrar u ocultar la contraseña y un medidor orientativo (la única regla que se exige sigue siendo el mínimo de 10 caracteres).
- Verificado en el navegador, en escritorio y en celular (375 px); en celular el chip y el lema del panel se ocultan para no pisar el dibujo. `npm run build` sin errores.

## Extra — Galería de fotos y planos por inmueble ✅ (24/11/2026)

**Rama:** `feature/galeria-inmuebles` (sale de `feature/portal-comprador`; su PR apunta a esa rama hasta que se fusione). Boceto aprobado antes de programar.

### Por qué
Un inmueble tenía una sola foto (V6) y un plano aparte (V8). El comprador necesitaba ver varias fotos «pasándolas una por una», y a veces un inmueble no tiene plano sino solo fotos. Ahora todo es una sola galería ordenada.

### Qué quedó
- **Tabla `imagen_inmueble`** (migración V9): `id, inmueble_id, inmobiliaria_id, tipo (FOTO|PLANO), orden, etiqueta, grande, miniatura, ancho, alto, foco_x, foco_y, hash, subida_por, creada_en`. Reemplaza a `foto_inmueble` y `plano_inmueble`: la migración **copia lo que ya estaba subido** (la foto primero, el plano después, con la etiqueta «Plano») y borra las tablas viejas. Probada contra una copia de la base de desarrollo: las 2 fotos y los 2 planos de la demo pasaron intactos.
- **Reglas:** una foto se guarda a 1600 px (miniatura de 480) y se recorta con su punto de interés; un plano, a 2000 px con más calidad y **nunca se recorta**. Máximo **12 imágenes** por inmueble y 8 MB por archivo. La **portada** del inventario es la primera FOTO por orden (un plano nunca es portada); sin fotos se ve el terreno.
- **API del asesor** `/api/inmuebles/{id}/imagenes`: `GET` (lista en orden), `POST` multipart (`tipo`, `etiqueta`, `focoX`, `focoY`), `GET /{imagenId}?tam=` (con `ETag`), `PATCH /{imagenId}` (tipo, etiqueta, encuadre), `PUT /orden` (debe nombrar cada imagen una sola vez) y `DELETE /{imagenId}` (el orden queda sin huecos). `GET /api/inmuebles/{id}/foto` sigue entregando la portada. Se retiraron `PUT/PATCH/DELETE /foto` y `/plano`.
- **API del comprador:** la ficha trae `imagenes` (en vez de `planoVersion`) y `GET /api/comprador/inmuebles/{id}/imagenes/{imagenId}` solo responde si el inmueble se le compartió.
- **Asesor:** en «Editar inmueble», una sola cuadrícula de «Fotos y planos»: agrega varias a la vez (botones «Agregar fotos» y «Agregar planos»), ordena arrastrando o con las flechas, nombra cada una (sala, cocina…), cambia entre Foto y Plano, elige el encuadre de una foto y la quita. Cada cambio se guarda al instante. Un inmueble nuevo se guarda primero y pasa a su edición para agregar las imágenes.
- **Comprador:** un carrusel con flechas, teclado y deslizar con el dedo, contador («2 / 6»), tira de miniaturas y visor a pantalla completa con ampliación (para leer las medidas de un plano).
- Un valor inválido en un parámetro (por ejemplo, un tipo de imagen inventado) ahora responde `400` en vez de `500`.

### Pruebas (231 en total; no llaman a OpenAI)
- `GaleriaInmuebleApiTest` (11, PostgreSQL real): orden de subida y portada (un plano primero no es portada); tamaños de foto y plano con su miniatura; sin fotos no hay portada; reordenar, cambiar tipo, etiqueta y encuadre (lo no enviado se conserva); el orden debe nombrar cada imagen una vez; quitar no deja huecos; tope de 12; `ETag`/304; archivo falso, vacío, de 9 MB y tipo inventado; **otra inmobiliaria no ve, sube, ordena, edita ni quita (404)** ni alcanza una imagen ajena con un inmueble propio; sin sesión → 401.
- `PortalCompradorApiTest`: la galería (fotos y planos) solo se ve si el inmueble se comparte.

### Probado en vivo (API real, PostgreSQL)
Migración V9 sobre una copia de la base; subida de 3 fotos y un plano desde el navegador, reordenar y nombrar; como comprador, carrusel con flechas, miniaturas y visor (también en 375 px). La copia de la base y los servidores de prueba se borraron.

### Lo que no se hizo
- No se probó con fotos reales de celular ni HEIC (el navegador de pruebas no abre el selector de archivos del sistema).
- Un plano migrado de la tabla anterior usa su imagen completa como miniatura hasta que se vuelva a subir.

## Fase 9 — Portal del comprador ✅ (24/11/2026)

**Rama:** `feature/portal-comprador` (sale de `develop`). Diseño aprobado en `docs/DISENO.md`, sección 11, y boceto aprobado antes de programar las pantallas.

### Por qué
El comprador era solo una ficha que administraba el asesor. Ahora tiene su propia cuenta para ver su presupuesto, los inmuebles que el asesor le comparte y simular su compra.

### Qué quedó
- **Cuarto rol, COMPRADOR** (migración V8: `usuario.cliente_id`, `inmueble_compartido`, `solicitud_comprador`, `plano_inmueble`). `/api/comprador/**` es solo suyo; el personal no entra y él no entra a nada del personal ni del portal del propietario. El JWT lleva `cliente_id`; desactivarlo corta su sesión al instante, igual que al propietario.
- **El asesor decide qué ve:** «Compartir con el cliente» desde los resultados de la búsqueda (con una nota opcional) y «Compartidos con el cliente» en su ficha, con el interés que marcó y «Dejar de compartir». Un inmueble no compartido (o compartido a otro cliente) responde 404.
- **Acceso del cliente:** el mismo componente de invitación del propietario (vista previa del correo, enlace de un solo uso a 72 h, desactivar). Un correo solo puede tener un rol.
- **Inicio del comprador:** su tope de compra como cifra acotada con su razón (ahorro o capacidad de pago), cuota máxima, tasa usada y las tarjetas de inmuebles con «Me interesa» / «No me interesa».
- **Ficha del inmueble:** foto, **plano** (con visor a pantalla completa y ampliación), datos clave, nota del asesor y **«Simule su compra»**: cuota inicial y plazo; las cifras las calcula el servidor (`CalculadoraCompra` + `SimuladorCreditoService`), con una pequeña espera al mover los controles. La cuota inicial mínima es el porcentaje de la inmobiliaria (más bajo en VIS) y la máxima, su ahorro sin pasar del precio.
- **Privacidad:** el comprador no ve el código interno, la dirección exacta ni nada del propietario.
- **«Quiero más información»:** un mensaje al asesor (máximo 5 sin atender). No envía correos: aparece en el inicio del asesor («N mensajes de compradores por atender») y en `/solicitudes`, donde lo marca como atendido.
- **Plano del inmueble:** se guarda reducido a 2.000 px (calidad alta, nunca el original). *(Después pasó a la galería de fotos y planos: ver arriba.)*
- Un método HTTP no permitido ahora responde `405` (antes, `500`).
- **Pulido tras revisar el diseño en vivo:** si un inmueble compartido cuesta más que el tope de compra del cliente, la tarjeta y la ficha muestran «Supera en $X lo que hoy puede comprar» (el valor lo calcula `CalculadoraCompra.excedenteSobreTope`; solo informa, la decisión es del asesor); el simulador muestra un esqueleto mientras llegan las cifras y las deja atenuadas mientras se recalculan; y se quitó el doble punto de «a. m..» en la tarjeta de acceso.

### Pruebas (228 en total, +21; no llaman a OpenAI)
- `CalculadoraCompraTest` (8, unitaria): mínima = porcentaje del precio redondeado hacia arriba; máxima = ahorro sin pasar del precio menos un peso; qué falta si el ahorro no alcanza; sin ahorro o sin precio; validación del rango; el excedente sobre el tope solo existe si el precio lo pasa.
- `PortalCompradorApiTest` (13): solo ve lo compartido y sin código ni dirección; 404 para lo no compartido, lo de otro cliente (misma o distinta inmobiliaria) y su foto y simulación; la simulación respeta el rango y el plazo; un inmueble vendido se ve pero no se simula; interés y mensajes llegan al asesor sin enviar correos; tope de 5 mensajes; dejar de compartir cierra la puerta; plano solo si se comparte; invitar exige correo y no usa uno ya ocupado por otro rol; desactivar corta la sesión; perfil con tope y asesor; el excedente sobre el tope llega calculado en la lista y la ficha; y la **matriz de acceso** de los cuatro roles.

### Probado en vivo (API real, PostgreSQL, Mailpit)
Laura invitó a «Paola y Juan», el enlace llegó a Mailpit, activaron la cuenta y entraron a su portal: tope de $300.000.000 («lo limita su ahorro»), dos inmuebles compartidos, simulación del 1102 con cuota de **$2.223.109 (29,6 % del ingreso)**, la misma cifra de la demo; el deslizador de cuota inicial del Provenza va de $80.400.000 a $90.000.000; plano, visor, «Me interesa» y mensaje al asesor; en celular (375 px) sin desborde. **Error encontrado al probar:** abrir un enlace de invitación con una sesión vencida guardada en el navegador daba «Debes iniciar sesión» (el token vencido se enviaba también a las rutas públicas); ahora no se envía en el ingreso, la activación ni la invitación. La base de desarrollo quedó como estaba (sin la cuenta, lo compartido, los mensajes, la foto y el plano de prueba) y Mailpit, limpio.

### Lo que no se hizo
- El plan de pagos por cuotas durante la construcción (separación, cuota inicial en N cuotas, saldo final): era solo un ejemplo de la captura.
- El comprador no puede cambiar su contraseña por su cuenta (lo resuelve el asesor reenviando el enlace).
- El plano se guarda como JPEG: las líneas muy finas pueden suavizarse.
- Sigue pendiente un límite de intentos en el ingreso y proteger o apagar Swagger en producción.

## Revisión del portal del vendedor y del acceso por rol ✅ (23/11/2026)

**Rama:** `fix/revision-portal` (sale de `develop`)

Revisión de punta a punta con la aplicación corriendo (API real, PostgreSQL, Mailpit): activación con enlace (válido, inválido, contraseñas que no coinciden), ingreso del vendedor por la página normal, un vendedor con dos ventas, uno sin ventas, una venta firmada, subidas con archivos malos, y una matriz de acceso de 15 rutas con asesor, administrador y vendedor (más sin sesión). El acceso salió como se esperaba. Lo que se encontró y se corrigió:

- **Un `500` al enviar sin archivo** (o con una petición que no es multipart): ahora responde `400` («Falta un dato de la petición…») o `415`. Afectaba a cualquier subida, no solo al portal.
- **El correo de invitación elegía una venta al azar:** con dos ventas, nombraba solo la más reciente. Ahora dice «sus ventas» (sin ventas, «su inmueble»; las canceladas no cuentan).
- **Plural en la cota:** decía «4 recibido» y «8 pendiente»; ahora «4 recibidos», «8 pendientes».
- **El portal en celular no decía de qué inmobiliaria era:** el nombre se ocultaba por debajo de 640 px; ahora se ve siempre.
- **La foto del inmueble casi no se veía** en la cabecera del portal por la capa que protege la lectura del texto; se aligeró.
- El mensaje del enlace inválido dejó de tutear: la página no sabe si habla con un propietario o con personal.

### Pruebas (207 en total, +2)
- `PortalPropietarioApiTest` (+1): enviar sin archivo o con un tipo incorrecto es un error del cliente (400/422), no un 500.
- `UsuariosEInvitacionesApiTest` (+1): el correo nombra la venta con una, «sus ventas» con dos y «su inmueble» con ninguna.

### Lo que se encontró y no se corrigió
- **El comprador no tiene portal ni cuenta** (solo ADMIN, ASESOR y PROPIETARIO): es la Fase 9. Hoy el comprador es una ficha que administra el asesor.
- **No hay límite de intentos en el ingreso**: se pueden probar contraseñas sin freno. Conviene agregarlo antes de producción.
- **Swagger sigue público** (decisión pendiente para producción).
- Una venta marcada FIRMADO con documentos pendientes muestra «Su venta ya se firmó» pero la cota sigue en 0 %: es coherente con los datos, pero se ve raro; en uso real una venta firmada tiene todo recibido.


## Fase 8 — Usuarios, invitaciones y portal del propietario ✅ (23/11/2026)

**Rama:** `feature/usuarios-y-portal` (sale de `develop`; incluye la guía de pruebas de `docs/guia-de-pruebas`). Diseño aprobado en `docs/DISENO.md`, sección 10.

### Por qué
No había forma de crear usuarios: los dos de la demo los sembraba `DatosDemo`, así que con la demo apagada nadie podía entrar. Y el propietario que vende no tenía cómo ver qué le faltaba para firmar.

### Qué quedó
- **Tres roles.** ADMIN y ASESOR (el personal) y el nuevo **PROPIETARIO**, que solo tiene su portal.
- **Seguridad separada por rol:** `/api/**` pasó de «cualquiera con sesión» a **solo ADMIN o ASESOR**; el portal vive en `/api/portal/**` y es **solo del PROPIETARIO**; `/api/usuarios/**` es solo del ADMIN. Dentro del portal todo se filtra por el propietario del token: el negocio de otro propietario (aunque sea de la misma inmobiliaria) responde 404.
- **Desactivar corta la sesión al instante:** en cada petición se comprueba que el usuario siga activo y con el mismo rol (antes, un token valía hasta que vencía, 8 horas).
- **Invitaciones** (`invitacion_acceso`, migración V7): enlace de **un solo uso** que vence a las **72 horas**; en la base solo queda su SHA-256; crear uno nuevo anula el anterior. Sirve para crear la cuenta y, reenviándolo, para elegir una contraseña nueva. Quien invita recibe además el enlace para compartirlo por otro medio si el correo no llega.
- **Usuarios** (pantalla del ADMIN): crear e invitar, cambiar el rol, desactivar y reactivar, reenviar la invitación. No puede quitarse su propio acceso ni dejar la inmobiliaria sin ADMIN.
- **Primer administrador:** si no existe ningún ADMIN y están `PREDIA_ADMIN_*`, se crea la inmobiliaria y su administrador **sin contraseña**, y el enlace para crearla se escribe una vez en el log.
- **Invitar al propietario** desde el negocio: el asesor ve el correo tal como saldrá (al propietario se le trata de «usted») y solo se envía cuando lo confirma. También puede desactivar su acceso.
- **Portal del propietario** (pensado para celular): la foto o el terreno del inmueble, «Le faltan N documentos para firmar», la cota de avance, la firma prevista y el contacto de su asesor; cada documento con su estado, la frase escrita en Java, **cómo conseguirlo** (la descripción del catálogo) y **Enviar documento**; los recordatorios que de verdad se le enviaron. No ve al comprador ni notas internas.
- **Lo que envía el propietario queda en revisión** (`requisito_negocio.sugerido_por = PROPIETARIO`), nunca recibido: el asesor lo ve como «Enviado por el propietario» y lo confirma. La IA no pisa lo que él eligió.
- Sigue entrando **después de firmar**; su acceso solo termina si lo desactivan.

### Pruebas (203 en total; las 14 nuevas no llaman a OpenAI)
- `PortalPropietarioApiTest` (7): ve su venta y sus documentos pero **no al comprador**; otro propietario (misma u otra inmobiliaria) recibe 404; lo enviado queda SUGERIDO por el propietario, nunca RECIBIDO, y **no sale ningún correo**; no puede reemplazar un documento recibido y vigente; solo ve recordatorios enviados (no borradores); desactivarlo corta su sesión; y la **matriz de acceso**: el propietario recibe 403 en negocios, clientes, inmuebles, documentos, consultas, asistente, usuarios, tasas…, y el personal 403 en el portal.
- `UsuariosEInvitacionesApiTest` (4): invitación → enlace → contraseña → login (el token no se guarda en claro y no sirve dos veces); enlace reemplazado o vencido no sirve; reenviar recupera la contraseña; reglas del ADMIN (no quitarse su acceso, no repetir correo, no crear propietarios desde ahí, desactivar o cambiar el rol corta la sesión, un usuario ajeno es 404); el asesor invita al propietario, que entra a su portal.
- `PrimerAdministradorTest` (3, unitaria): crea inmobiliaria y ADMIN sin contraseña; con un ADMIN existente no hace nada; sin variables solo avisa.

### Probado en vivo (API real, PostgreSQL, Mailpit)
Laura invitó a Jorge desde el 1102 (el correo llegó a Mailpit con su nombre y el inmueble), Jorge abrió el enlace, creó su contraseña y entró a su portal; envió el paz y salvo y quedó «En revisión»; Laura lo vio como «Enviado por el propietario» y el último ingreso de Jorge; con su sesión, Jorge recibió 403 en negocios, clientes, documentos y usuarios. Revisado en escritorio y en celular (sin desborde). **Un error encontrado al probar:** el documento enviado por el propietario decía «Sugerido por la IA»; ahora dice «En revisión» en el portal y «Enviado por el propietario» para el asesor. La base de desarrollo quedó como estaba (sin la cuenta de Jorge ni el documento de prueba).

### Lo que no se hizo
- El portal del comprador con el plan de pagos (Fase 9).
- Un «olvidé mi contraseña» autónomo (hoy lo resuelve el asesor o el ADMIN reenviando el enlace).
- No se probó con un servidor SMTP real (solo Mailpit).


## Corrección — Modelo de embeddings y HTTPS ✅ (23/11/2026)

**Rama:** `fix/modelo-embeddings-y-https` (sale de `develop`)

Salió de auditar el proyecto contra `Requerimientos.md` (el punto 11 del flujo de consulta pide embeddings «con el mismo modelo utilizado para el índice consultado», y el 3 pide el envío cifrado por HTTPS).

### Qué quedó
- **La búsqueda vectorial solo compara contra fragmentos indexados con el mismo modelo** que vectoriza la pregunta (`f.embedding_modelo = ?`). Antes el modelo se guardaba por fragmento pero no se comparaba: cambiar `OPENAI_EMBEDDING_MODEL` habría mezclado vectores incompatibles y dado resultados sin sentido, sin avisar.
- **Aviso, no silencio:** la respuesta de la consulta trae `aviso` con cuántos fragmentos quedaron fuera de la búsqueda por significado (siguen encontrándose por palabras); se muestra en Expedientes y lo recibe el asistente.
- **Regeneración sin volver a subir PDF:** `POST /api/documentos/regenerar-vectores` (solo ADMIN, en `ConfiguracionSeguridad`) recalcula, desde el texto guardado, los vectores de otro modelo y los que quedaron sin vector (modo manual). Trabaja por tandas de 300 y devuelve `regenerados`, `pendientes` y el modelo; se repite hasta que `pendientes` sea 0. Solo toca la inmobiliaria del ADMIN, exige IA (503 si no) y, si el proveedor falla a mitad, conserva lo ya regenerado (502).
- **Inmuebles:** un vector de otro modelo cuenta como pendiente y se recalcula solo en la siguiente búsqueda.
- **HTTPS:** [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md) explica cómo ponerlo detrás de un proxy con certificado (Caddy, con su `Caddyfile`), con `SERVER_FORWARD_HEADERS_STRATEGY=framework`, y cómo comprobarlo. **La guía no se probó en un servidor real.** El README apunta a ella.

### Pruebas (189 en total; las 3 nuevas no llaman a OpenAI)
- `ConsultaRagApiTest` (+2): con el mismo modelo la búsqueda por vector encuentra el fragmento; al cambiar de modelo ya no aparece y se avisa («1 fragmento»); el asesor no puede regenerar (403) y el ADMIN sí, tras lo cual se encuentra de nuevo y desaparece el aviso; regenerar solo toca la inmobiliaria propia (la ajena queda con su modelo) y sin IA responde 503.
- `InventarioBusquedaIaTest` (+1): al cambiar el modelo, los vectores de inmuebles se recalculan en vez de mezclarse.
- Las dos pruebas existentes de la consulta que insertaban fragmentos con el modelo `pruebas` ahora fijan ese modelo en el simulador de IA.

### Lo que no se hizo
- ~~El escaneo de archivos (antivirus)~~: implementado después (ver «Extra — Escaneo de archivos», arriba).
- No hay botón en la interfaz para regenerar los vectores: se llama al endpoint (Swagger) como ADMIN.


## Extra — Foto de portada de cada inmueble ✅ (23/11/2026)

**Rama:** `feature/fotos-inmuebles` (sale de `feature/rediseno`; su PR apunta a la rama previa hasta que se fusione)

El asesor puede subir una foto por inmueble; aparece en la cabecera de cada tarjeta del inventario y como miniatura en «Para ofrecer» del inicio. Sin foto, se sigue viendo el terreno generado.

### Qué quedó
- **Procesamiento en el servidor** (`ProcesadorImagen`): se decodifica lo que llega (así se rechaza cualquier archivo que no sea una imagen real, aunque se llame `.jpg`), se aplana sobre blanco, se reduce (por mitades, sin ampliar nunca) y se vuelve a codificar como JPEG: una copia **grande de 1600 px** y una **miniatura de 480 px**. Eso además elimina los metadatos (GPS, cámara). Tope de 50 megapíxeles decodificados y de 8 MB subidos. **No se conserva el archivo original.**
- **Almacenamiento aparte** (`V6__fotos_inmueble.sql`): tabla `foto_inmueble` con las dos copias en `BYTEA`, el hash, las dimensiones y el encuadre. Se consulta con SQL explícito (`FotoInmuebleRepositorio`), como los vectores, para que listar inmuebles nunca cargue bytes. Siempre filtrada por inmobiliaria: la foto de otra responde 404.
- **Endpoints** `/api/inmuebles/{id}/foto`: `GET` (`tam=miniatura` o grande; con `ETag` y caché privada, porque el hash cambia al subir otra), `PUT` multipart, `PATCH /foco` y `DELETE`. El inmueble (`InmuebleDto`) trae `foto: {version, focoX, focoY, ancho, alto}` o `null`.
- **Cualquier formato y tamaño, en la interfaz:** el navegador lee la imagen (JPG, PNG, WebP, AVIF, HEIC en Safari…), respeta la orientación de las fotos de celular y la reduce a 1600 px antes de subirla, así que se sube un archivo liviano y funciona con lo que el navegador sepa abrir. El servidor acepta además JPEG, PNG, GIF y BMP directos.
- **Se ve bien en cualquier proporción:** `object-fit: cover` con un **punto de interés** que elige el asesor (toca la imagen completa o usa los controles Horizontal y Vertical). Al lado se ve cómo quedará recortada en el inventario (5:2) y en el inicio (cuadrada), así una foto vertical, una panorámica o una cuadrada se ven bien en los dos sitios.
- La foto va en el formulario del inmueble (alta y edición). Si el inmueble se guarda pero la foto falla, no se pierde nada: se abre la edición con el motivo.

### Decisiones
- **Una foto de portada por inmueble**, no una galería. *(Resuelto después con la galería de fotos y planos.)*
- **En la base de datos, no en disco:** una copia de seguridad de la base incluye las fotos, no hay volúmenes que administrar y el filtro por inmobiliaria es el mismo que en todo lo demás. Con muchas fotos conviene pasar a un almacenamiento de objetos.
- Las fotos **sí se guardan**, a diferencia de los PDF de los clientes: son material comercial de la propia inmobiliaria.
- El servidor no lee la orientación EXIF; lo hace el navegador al reducir. Un cliente de la API que suba fotos de celular directas puede verlas giradas.

### Pruebas (186 en total; las 18 nuevas no llaman a OpenAI)
- `ProcesadorImagenTest` (9): horizontal 4000×3000 → 1600×1200 y miniatura 480; vertical, cuadrada y panorámica; una imagen pequeña no se amplía; lo transparente queda sobre blanco; el resultado siempre es JPEG; rechaza texto, PDF y JPEG truncado aunque se llamen foto; rechaza una imagen de 64 megapíxeles; el hash cambia con otra imagen.
- `FotosInmuebleApiTest` (9, PostgreSQL real): subir, reducir y listar; sin foto → `null`/404; PNG vertical; reemplazar cambia la versión y quitar la borra; cambiar solo el encuadre; `ETag`/304; archivo falso, vacío y de 9 MB → 422; **otra inmobiliaria no ve, sube, quita ni cambia la foto (404)**; sin sesión → 401.

### Probado en vivo
Subida con el flujo real del navegador (imágenes generadas en la página: una horizontal de 3600×2400 y una vertical de 2400×3600): se reducen, se elige el punto de interés, se guarda, y se ven en la cabecera del inventario y en la miniatura del inicio, también tras recargar. Las capturas del README usan **ilustraciones de ejemplo**, no fotos reales; las fotos de prueba se borraron de la base de datos. **No se probó con una foto real de celular ni con HEIC** (el navegador de pruebas no abre el selector de archivos del sistema).


## Extra — Rediseño visual: curvas de nivel y cifras acotadas ✅ (23/11/2026)

**Rama:** `feature/rediseno` (sale de `feature/reduccion-credito`; su PR apunta a `develop` cuando se fusione la reducción)

El diseño anterior (barra lateral, tarjetas blancas y acento cobalto) se veía genérico. Se cambió la identidad por una idea propia del oficio: los mapas topográficos y sus **cotas**.

### Qué quedó
- **Curvas de nivel generadas** (`curvasDeNivel.ts` + `Terreno.tsx`): un campo de alturas suave al que se le trazan las líneas de igual altura (marching squares). La misma semilla da siempre el mismo terreno, así que **cada inmueble tiene el suyo** (miniaturas en el inicio y cabecera de cada tarjeta del inventario). Las curvas «maestras» (cada quinta) van más gruesas, como en un mapa. Se trazan una sola vez al entrar al login, al inicio y al detalle de un negocio, y respetan «reducir movimiento».
- **Cota** (`Cota.tsx`): una línea de medida con topes y la medida al centro. `CifraAcotada` muestra los números importantes con su nombre y la razón que los decidió (tope de compra, cuota actual, avisos del día). `CotaAvance` dibuja el cierre del negocio con **un tramo por documento obligatorio**: macizo si está recibido, punteado si falta y rojo si venció. `CotaPorcentaje` es la versión pequeña de las listas.
- **Sello** para lo vencido (en vez de una etiqueta de color) y estados del resto de documentos como texto con un punto.
- **Pantallas rediseñadas:** ingreso, barra lateral, inicio, lista y detalle de negocios (la ficha de vende/compra/firma/asesor sale con líneas finas, no en tarjetas), presupuesto del cliente, reducción de crédito e inventario. Las demás heredan los colores nuevos, la tipografía y los títulos grandes.
- **Colores y tipografía:** azul de plano `#0C2F73`, azul vivo `#2358D6` solo para lo que se toca, papel `#F0F3F9`, tinta `#070A12` y sello `#C8102E` solo para alertas. Archivo en sus extremos: muy ancha para titulares y cifras, estrecha (`.rotulo`) para las anotaciones.
- Se retiró el plano de apartamento del ingreso y los estilos que solo él usaba.

### Detalles que salieron al revisar
- En celular, una columna de rejilla sin `minmax(0, …)` ensanchaba el inicio más allá de la pantalla; y una fila de recordatorios con texto largo hacía lo mismo en el detalle del negocio. Ambos se corrigieron y se midió el ancho de página (sin desborde) en inicio, negocios, inventario y cliente.
- Un archivo `terreno.ts` junto a `Terreno.tsx` chocaba en macOS (no distingue mayúsculas): el auxiliar se llama `curvasDeNivel.ts`.
- La cifra grande de la cuota de reducción se salía del panel con tres columnas; ahora ocupa su propia fila.

### Lo que no se hizo
- No hay pruebas automáticas de frontend (nunca las hubo): se verificó con `npm run build` y en el navegador, en escritorio y en ancho de celular.
- Recordatorios, expedientes, tasas, requisitos, asistente y los formularios solo heredaron colores y títulos; no tienen terreno ni cotas propias.
- El estudio imprimible mantiene su diseño sobrio en blanco; no se rehízo con terreno.


## Extra — Reducción de crédito (Ley 546 de 1999) ✅ (22/11/2026)

**Rama:** `feature/reduccion-credito` (sale de `develop`, ya en la versión 1.0.0)

Módulo extra a partir del «Simulador profesional de reducción hipotecaria» del autor (HTML + JavaScript). Se reescribió siguiendo las reglas del proyecto: las cifras salen de Java con pruebas, nunca del navegador ni del modelo.

### Qué quedó
- **`CalculadoraReduccion`** (Java puro, BigDecimal; reutiliza `tasaMensual` y `cuota` de `CalculadoraCredito`): abonos extraordinarios mensuales, anuales o únicos, que **reducen el plazo** (la cuota se mantiene) o **la cuota** (el plazo se mantiene; se recalcula después de cada abono). Cada mes se redondea al peso y la última cuota se ajusta, de modo que **los totales siempre son la suma de la tabla**. La Ley 546 permite el prepago sin penalidad; el modo «reducir la cuota» es nuevo respecto al simulador original, que solo reducía el plazo.
- **`POST /api/reducciones/simular`** (`SimuladorReduccionService`): crédito actual + hasta 3 escenarios de abono, con ahorro en intereses y en seguros, cuotas reducidas, desembolso mensual e **ingreso necesario** según el porcentaje máximo de cuota que ya configura cada inmobiliaria (reemplaza el «cuota × 3» fijo del original). Con un cliente, indica si su ingreso alcanza; un cliente de otra inmobiliaria responde 404. La tasa va como fracción y una tasa mal escrita (11 en vez de 0,11) se rechaza en vez de simular un 1100 %.
- **Pantalla «Reducción»** (menú «Más» en celular; enlace «Reducir su crédito» en el cliente): formulario, banner de la cuota actual, comparación de escenarios, tabla de amortización paginada por escenario y **estudio imprimible** con el nombre de la inmobiliaria y del asesor de la sesión. Recalcula al escribir (con una pequeña espera).
- **Asistente:** séptima herramienta, `simularReduccionCredito` (un abono por llamada; la tasa en porcentaje se convierte a fracción en Java). Devuelve las cifras ya escritas, de modo que `GuardiaCifras` las respalda.

### Qué se cambió del simulador original, y por qué
- Los escenarios 2 y 3 salían del abono 1 por 1,417 y 1,929, y «# de veces pagada la deuda» de `meses × 0,63`: factores sin sustento. Ahora el asesor escribe los tres abonos y esa fila no existe.
- El estudio decía «YMG Broker Financiero» fijo: ahora usa la inmobiliaria del usuario.
- Los honorarios de éxito + IVA no se llevaron: son del negocio de un broker, no de una inmobiliaria.
- No se guardan estudios en la base de datos (es una simulación sin estado).

### Pruebas (168 en total; las 19 nuevas no llaman a OpenAI)
- `CalculadoraReduccionTest` (11): **los valores de referencia salen de correr el simulador original con node** (cuota de `$1.495.708`; 123, 137 y 165 cuotas con abono mensual, anual y único; intereses dentro de unos pocos pesos por el redondeo mensual); invariantes en cada caso (capital + abonos = saldo, totales = suma de filas, saldo final cero); reducir cuota mantiene el plazo; reducir plazo ahorra más; abono mayor que el saldo; tasa 0; datos inválidos.
- `ReduccionApiTest` (6, PostgreSQL real): escenarios con cifras de la calculadora, sin abonos, ingreso necesario y si lo cumple el cliente (`$6.852.360` = desembolso / 30 %), modo reducir cuota, cliente de otra inmobiliaria → 404, validaciones y sesión.
- `AsistenteApiTest` (+2): la herramienta usa las cifras de la calculadora e interpreta bien el porcentaje; con una tasa absurda devuelve un error al modelo en vez de fallar.

### Probado en vivo
Backend y frontend de la rama en los puertos 8081 y 5174 (sin tocar la instancia que ya estaba corriendo): el endpoint con los datos de la demo, la pantalla en escritorio y en celular (sin desborde de página, sin errores de consola) y el marcado del estudio, que solo se ve al imprimir. **No se probó la impresión real** del navegador.


## Fase 7 — Pulido, guion de demo y pruebas finales ✅ (22/11/2026)

**Rama:** `feature/pulido-final` (sale de `develop`, con las Fases 1–6 ya fusionadas)

### Qué quedó
- **Revisión de cada pantalla** en escritorio y en celular (inicio, negocios, cierre del 1102, clientes y presupuesto, inventario, recordatorios). La identidad (azul de Prusia, papel blanco, tinta negra, Archivo) ya era coherente, así que no se rediseñó nada: solo se corrigieron detalles.
- **Detalles corregidos:** dos textos terminaban con un punto doble porque la hora local ya trae «p. m.» («Autorización de datos registrada el … 12:30 p. m..» y «Enviado el … . Lo aprobó …»).
- **README:** estado de las siete fases, sección de **capturas** (`docs/capturas/`, incluida una en celular) y **guion de demo** paso a paso, con lo que necesita clave de OpenAI y lo que funciona en modo manual. El guion se contrastó con el contenido real de los PDF de ejemplo: el contrato es un **arrendamiento** ficticio que incluye parqueadero, y la pregunta del asistente se redactó para ese documento.
- **Pruebas finales:** `./mvnw test` → **149 pruebas en verde**; `npm run build` (incluye `tsc -b`) sin errores.

### Lo que no se hizo
- **No se probó con un modelo real de OpenAI** (no hay clave en el equipo de desarrollo). Falta ver cómo redacta el modelo y si respeta la instrucción del sistema del asistente.
- **Sin captura del asistente:** sin clave la sección aparece deshabilitada; se puede agregar cuando haya una clave.
- Los **extras de IA 2** (sentimiento de los comentarios tras la visita y *few-shot* para la intención de un mensaje) siguen pendientes.
- Falta el recorrido final de la demo con la clave real y la rama `release/*` hacia `main` (solo cuando se pida).

## Fase 6 — Asistente con herramientas ✅ (21/11/2026)

**Rama:** `feature/asistente` (sale de `feature/cierre-negocio`, ya fusionada en `develop`)

### Qué quedó
- **Chat con tool calling de Spring AI 1.1.8** (`POST /api/asistente/mensajes`): el modelo conversa con el asesor y decide qué herramienta usar; las herramientas son clases de Predia (`HerramientasPredia`), no texto del modelo. Se crea **una instancia por petición, atada al usuario autenticado**: la inmobiliaria sale del JWT y **nunca de un argumento** que el modelo pueda inventar.
- **Las seis herramientas** (reutilizan los servicios existentes; no duplican ninguna regla):
  | Herramienta | De dónde sale el dato |
  |---|---|
  | `consultarExpedientes(pregunta, inmueble?)` | `MotorRag`: con citas, o «no tengo información» sin llamar al modelo |
  | `buscarInmuebles(operacion, precioMax, habitacionesMin, ciudad, descripcion, paraCliente?)` | `BusquedaInmuebles`; con `paraCliente` usa el tope de compra calculado en Java |
  | `calcularPresupuesto(cliente, plazoAnios?)` | `PresupuestoService` / `CalculadoraPresupuesto` |
  | `simularCredito(inmueble, cliente, plazoAnios?, banco?)` | `SimuladorCreditoService` / `CalculadoraCredito` |
  | `estadoDocumentalNegocio(negocio)` | `ServicioEstadoDocumental` / `MotorReglasCierre` |
  | `redactarRecordatorioPropietario(negocio)` | `ServicioRecordatorios.generar`: **borrador SUGERIDO, nunca se envía** |
- **Nombres o ids.** Los parámetros aceptan el id o el nombre («Paola y Juan», «apto 1102 de Cañaveral»): `ResolvedorEntidades` ignora tildes, mayúsculas y «apto/apartamento», busca solo dentro de la inmobiliaria del usuario y, si hay varias coincidencias, **no adivina**: devuelve los candidatos para que el asistente pregunte.
- **Las cifras no las escribe el modelo.** Las herramientas las entregan ya escritas por Java (`$300.000.000`, `29,6 %`) y la instrucción del sistema ordena copiarlas. Como red de seguridad, `GuardiaCifras` compara cada número de la respuesta con lo que devolvieron las herramientas (y con lo que escribió el asesor): lo que no tenga respaldo —una suma, un redondeo, un dato inventado— se devuelve en `cifrasSinRespaldo` y la interfaz lo señala. No corrige a escondidas: avisa.
- **Gobernanza:** ninguna herramienta confirma, recibe ni envía nada. El recordatorio queda SUGERIDO en la bandeja; solo `ServicioRecordatorios.aprobar` envía. La respuesta lista los borradores creados y la interfaz enlaza a Recordatorios.
- **Sin IA o con IA caída, el resto sigue funcionando:** sin `OPENAI_API_KEY` el endpoint responde **503** «el asistente está deshabilitado»; si el proveedor falla responde **502** con un mensaje limpio (sin el detalle del proveedor, que podría traer una clave o fragmentos de la consulta).
- **Memoria de conversación** en el servidor, atada al usuario y a su inmobiliaria (otro usuario no la lee aunque conozca el id), con tope de 500 conversaciones y 20 mensajes cada una; se pierde al reiniciar.
- **Frontend:** nueva sección **Asistente** (sube a la barra inferior del celular; Clientes pasa a «Más»). Ejemplos para empezar, indicador de «consultando», etiquetas de qué herramientas se usaron, citas, aviso de borrador pendiente de aprobación, aviso de cifras sin respaldo, «Nueva conversación» y la conversación se conserva al cambiar de sección.

### Tres hallazgos de probar en vivo
1. **Reintentos de Spring AI:** con el proveedor caído, el valor por defecto (10 reintentos con esperas de hasta 3 minutos) dejaría al asesor esperando minutos. Ahora `spring.ai.retry` tiene 3 intentos con esperas de 1–5 s y `spring.http.client` conexión 5 s / lectura 60 s: con el proveedor caído el aviso llega en **~3 s**.
2. **Texto sin espacios** (un correo, una URL, un JSON) ensanchaba la burbuja y toda la página en celular, y la barra inferior quedaba recortada.
3. **El último mensaje quedaba tapado** por el campo de texto fijo tras el autodesplazamiento (se veía cortado un aviso de error).

### Pruebas (149 en total; las 25 nuevas no llaman a OpenAI)
- `GuardiaCifrasTest` (7): cifras respaldadas por las herramientas; una inventada, **una suma hecha por el modelo y un redondeo («294 millones» de `$294.114.000`) se señalan**; «300 millones» de `$300.000.000` sí se acepta; las cifras del asesor cuentan como fuente.
- `ResolvedorEntidadesTest` (5): nombre con tildes y abreviaturas, ambigüedad, referencia vacía, **nunca resuelve entidades de otra inmobiliaria ni por id ni por nombre**, negocio por id/inmueble/propietario.
- `AsistenteApiTest` (13, con `ChatModel` simulado que **ejecuta las herramientas reales** sobre PostgreSQL real): sin IA → 503 sin llamar al modelo; «¿qué les alcanza a Paola y Juan en Floridablanca?» muestra exactamente el tope que calcula `PresupuestoService` y filtra por tope y ciudad; la simulación de crédito coincide con la calculadora; «¿qué le falta al dueño del 1102?» coincide con `ServicioEstadoDocumental`; **el recordatorio queda SUGERIDO y no se envía ningún correo**; consulta de expedientes con cita (documento, sección); sin evidencia → no inventa; **un asesor de otra inmobiliaria no obtiene datos ajenos aunque el modelo pida un id ajeno**; una cifra inventada se señala; la memoria no se comparte entre usuarios; un fallo del proveedor no filtra la clave.

### Probado en vivo (API real + PostgreSQL + Mailpit)
En este equipo no hay API key de OpenAI, así que no se pudo probar contra el servicio real; en su lugar se levantó un **servidor local que imita la API de OpenAI** (`/v1/chat/completions` con `tool_calls` y `/v1/embeddings`) y Predia se apuntó a él con `SPRING_AI_OPENAI_BASE_URL`. Eso ejercitó **el bucle real de Spring AI** (esquemas JSON de los parámetros, argumentos, resultado), que el modelo simulado de las pruebas no cubre:
- «¿Qué le falta al dueño del apto 1102 de Cañaveral para firmar?» → `estadoDocumentalNegocio`: «Faltan 2 documentos», 67 %, certificado de tradición vencido el 4 de noviembre de 2026 y paz y salvo de administración pendiente.
- «¿Qué les alcanza a Paola y Juan en Floridablanca?» → `calcularPresupuesto` + `buscarInmuebles`: tope `$300.000.000` (lo limita el ahorro) y solo el Apto 1102 Cañaveral.
- Simulación a 15 años: cuota `$2.438.073` (32,51 % del ingreso, supera la cuota máxima de `$2.250.000`).
- «Redacta un recordatorio…» → borrador SUGERIDO; **Mailpit: 0 mensajes antes y después**.
- Consulta de expedientes con cita (`reglamento-propiedad-horizontal.pdf`, pág. 1, «ARTÍCULO 6»).
- Sin clave → 503; proveedor caído → 502 en ~3 s; 401 sin token; 400 con mensaje vacío.
- Interfaz revisada en escritorio y en ancho de celular (375 px), con IA, sin IA y con el proveedor caído.
Los datos de prueba se borraron de la base de desarrollo (documento, borrador y vectores falsos) y Mailpit quedó vacío. **Lo que no se pudo probar:** una conversación con un modelo de OpenAI real (no hay clave en este equipo): cómo redacta el modelo, y si respeta siempre las reglas de la instrucción, se verifica con tu clave; la guardia de cifras existe justo para eso.

### Cómo probarlo
1. `OPENAI_API_KEY` en `.env`, `./mvnw spring-boot:run` y `npm run dev`; entra como `laura@altos.test` → **Asistente**.
2. Prueba los tres ejemplos: *«¿Qué le falta al dueño del apto 1102 de Cañaveral para firmar?»*, *«¿Qué les alcanza a Paola y Juan en Floridablanca?»* y *«¿La venta del 1102 incluye parqueadero?»* (esta última requiere haber subido el contrato al inmueble).
3. *«Redáctale un recordatorio al dueño del 1102»* → aparece en **Recordatorios** como borrador; hasta que lo apruebas, Mailpit sigue vacío.
4. Sin clave, el asistente muestra que está deshabilitado y el resto de Predia funciona.
5. `./mvnw test` (con Docker abierto).

### Qué falta
- Fase 7: pulir el diseño, README con guion de demo y capturas, pruebas finales y `release/*`.
- Extras de IA 2 (solo si sobra tiempo): sentimiento de los comentarios tras una visita y few-shot para reconocer la intención de un mensaje.
- Las conversaciones del asistente están en memoria (se pierden al reiniciar).

---

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
