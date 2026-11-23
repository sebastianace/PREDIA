# Guía para probar Predia sección por sección

Esta guía sirve para dos cosas: comprobar que todo funciona antes de presentar, y tener a mano **qué mostrar y qué explicar** en cada parte. Todos los datos son ficticios (Altos Inmobiliaria, Bucaramanga).

Cada sección tiene: **para qué sirve**, **cómo probarla**, **qué deberías ver** y **qué explicarle al profesor**. La idea que conecta todo es una sola frase:

> **«La IA sugiere, las reglas deciden y la persona confirma.»** Ningún número lo calcula el modelo de lenguaje, nada pasa a «recibido» sin el asesor y nada se le envía a un propietario sin su aprobación.

---

## 0. Antes de empezar

1. Abre Docker Desktop y, en la terminal de VS Code, dentro de la carpeta del proyecto:
   ```bash
   ./arrancar.sh
   ```
2. Abre <http://localhost:5173>. Mailpit (los correos de prueba) está en <http://localhost:8026>.
3. Si tienes una clave de OpenAI en `OPENAI_API_KEY` (archivo `.env`), Predia funciona **con IA**. Si no, arriba aparece **«Modo manual»**: todo funciona salvo lo que necesita al modelo (asistente, «Leer con IA», búsqueda por significado, redacción con IA). Conviene mostrar las dos cosas: que con IA ayuda, y que sin ella la app no se cae.

### Quién entra a Predia

Hay tres roles. Nadie se registra solo: **se entra por invitación**.

| Usuario | Rol | Qué puede hacer |
|---|---|---|
| `admin@altos.test` | ADMIN | Todo, y además usuarios, tasas, catálogo de requisitos y regenerar vectores |
| `laura@altos.test` | ASESOR | El trabajo diario, e invitar al propietario a su portal |
| El propietario que vende (Jorge, en la demo) | PROPIETARIO | Solo su portal: su venta, sus documentos y sus recordatorios |

La contraseña del ADMIN y de Laura es el valor de `DEMO_PASSWORD` en tu `.env`. Jorge no tiene cuenta hasta que lo invitas (sección 15).

Los **compradores** (por ejemplo «Paola y Juan») todavía no tienen portal: son registros que el asesor administra. Su portal, con el plan de pagos, sería la siguiente fase.

---

## 1. Ingreso

**Cómo probarlo:** entra con `laura@altos.test`. Luego cierra sesión (abajo a la izquierda) y entra con `admin@altos.test`. Prueba también una contraseña equivocada.

**Qué deberías ver:** las curvas de nivel se trazan al entrar; con la contraseña mala, un mensaje de error claro.

**Qué explicar:** la sesión es un JWT firmado (HS256) que dura 8 horas. **La inmobiliaria del usuario sale del token**, nunca de un parámetro que se pueda cambiar desde el navegador: así una inmobiliaria jamás ve los datos de otra.

---

## 2. Inicio

**Cómo probarlo:** entra como Laura y mira el inicio.

**Qué deberías ver:** el saludo, tres cifras (inmuebles disponibles, negocios sin cerrar, recordatorios por aprobar), los inmuebles «Para ofrecer» del más barato al más caro y el Apto 1102 con un sello rojo «1 vencido».

**Qué explicar:** es el resumen diario del asesor. Los vencimientos los calcula el motor de reglas con la fecha de hoy (hora de Colombia), no la IA.

---

## 3. Inventario y fotos

**Cómo probarlo:**
1. *Inventario* → filtra por «Disponibles», «Vendidos», etc.
2. Pulsa **Editar** en el Apto 1102 → **Elegir foto** → elige una foto de tu celular o de internet (horizontal, vertical, la que sea).
3. Toca en la foto lo que más importa (por ejemplo la fachada) y mira cómo se ve en «Así se verá en el inventario» y «en el inicio». **Guardar cambios**.
4. Crea un inmueble nuevo con **Nuevo inmueble**.

**Qué deberías ver:** la foto en la cabecera de la tarjeta y como miniatura en el inicio. Sin foto, cada inmueble muestra su propio terreno de curvas de nivel.

**Qué explicar:**
- La foto se reduce en el navegador (cualquier formato, respeta la orientación del celular) y otra vez en el servidor, que la **decodifica de verdad**: un PDF renombrado a `.jpg` se rechaza. Se guardan solo dos copias reducidas, sin metadatos (GPS, cámara).
- El «punto de interés» resuelve el problema de que una foto vertical se recorte mal en un espacio horizontal.

---

## 4. Clientes

**Cómo probarlo:**
1. *Más → Clientes* → **Nuevo cliente**.
2. **Con IA:** en «Lo que dijo el cliente» escribe algo como «Ganan 6 millones entre los dos, tienen 70 millones ahorrados y buscan apartamento en Cañaveral con parqueadero» → **Leer con IA**. Revisa lo que propone y corrige lo que quieras.
3. **Sin IA:** llena el formulario a mano.
4. Marca la autorización de tratamiento de datos y **Guardar cliente**.

**Qué deberías ver:** sin marcar la autorización no deja guardar. Con IA, el formulario se llena solo pero **no se guarda hasta que pulsas Guardar**.

**Qué explicar:** la IA solo *propone* el perfil; la persona lo confirma (Ley 1581 de 2012: sin autorización no se guardan datos personales).

---

## 5. Presupuesto del cliente

**Cómo probarlo:** *Clientes → Paola y Juan*. Cambia el plazo (15, 20 años) y la tasa.

**Qué deberías ver:** «Puede comprar hasta **$300.000.000**», con la razón: lo limita el ahorro ($90.000.000 es el 30 % de la cuota inicial). Debajo: cuota máxima ($2.250.000 = 30 % del ingreso), tope por ahorro y tope por capacidad de pago.

**Qué explicar:** el tope es el **menor** de dos cálculos hechos en Java con fórmulas explícitas (`CalculadoraPresupuesto`), con pruebas. La tasa es «de ejemplo» y lo dice en pantalla.

---

## 6. Búsqueda de inmuebles para el cliente

**Cómo probarlo:** en el mismo cliente, **Buscar inmuebles dentro del presupuesto**.

**Qué deberías ver:** solo inmuebles disponibles, en venta, dentro del tope y de la ciudad que busca (el Apto 1102 Cañaveral, en Floridablanca). Con IA, además un % de coincidencia con lo que pidió.

**Qué explicar:** búsqueda en dos pasos. **SQL filtra lo no negociable** (precio, estado, ciudad, habitaciones) y **pgvector ordena** por parecido con lo que pidió el cliente. La IA nunca puede meter un inmueble que se pase del presupuesto.

---

## 7. Simulador de crédito

**Cómo probarlo:** en el resultado del 1102, simula el crédito a 15 años.

**Qué deberías ver:** cuota de **$2.438.073**, que equivale al 32,5 % del ingreso y **supera** la cuota máxima ($2.250.000): la pantalla lo advierte.

**Qué explicar:** sistema francés (cuota fija). Tasa mensual = (1 + EA)^(1/12) − 1. Es una simulación; la aprobación es del banco.

---

## 8. Reducción de crédito

**Cómo probarlo:** *Más → Reducción* (o **Reducir su crédito** desde un cliente). Deja 150.000.000, 11 % y 240 cuotas; escribe abonos de 500.000, 700.000 y 1.000.000; cambia entre **Reducir el plazo** y **Reducir la cuota** y entre cada mes / una vez al año / un solo pago. Abre la tabla de amortización e **Imprimir estudio**.

**Qué deberías ver:** con 500.000 al mes se paga en **123 cuotas** en vez de 240 y se ahorran **$113.751.687** en intereses.

**Qué explicar:** viene de un simulador que hice antes en JavaScript; aquí se reescribió en Java con pruebas, y sus resultados se compararon contra el original. La Ley 546 de 1999 permite abonar sin penalidad y elegir si se reduce la cuota o el plazo.

---

## 9. Expedientes (RAG): subir un PDF y preguntarle

Este es el **requerimiento base** del profesor.

**Cómo probarlo:**
1. *Más → Expedientes* → elige el **Apto 1102 Cañaveral** → **Subir PDF** → `demo/pdfs/contrato-arrendamiento.pdf`.
2. Espera a que pase de «En cola» a «Procesando» y a **«Indexado»** (unos segundos).
3. En **«Pregúntale al expediente»** escribe: «¿El inmueble incluye parqueadero?».
4. Pregunta algo que el contrato no diga, por ejemplo «¿Cuál es el color de la fachada?».

**Qué deberías ver:** la respuesta con su **cita** (documento, página y sección; aquí, la cláusula primera). En la pregunta sin respuesta: «No tengo información suficiente…».

**Qué explicar (lo más importante):**
- El PDF **no se queda en el servidor**: se guarda temporalmente, se extrae el texto (PDFBox; OCR con Tesseract solo en páginas escaneadas), se parte en fragmentos de 500–1.000 tokens con solape, se vectoriza, se verifica y **se borra**. Queda el hash SHA-256, los fragmentos, los vectores y los metadatos.
- La búsqueda es **híbrida** (vectores + texto en español), combinada con RRF y siempre filtrada por inmobiliaria y rol.
- Si no hay evidencia suficiente, responde «no lo sé» **sin llamar al modelo**. Las citas se arman desde los metadatos, no desde lo que escribió el modelo, y las que el modelo invente se descartan.
- Limitación: sin el PDF original no se pueden verificar firmas, sellos o tablas; si cambia el OCR, hay que volver a subirlo.

Para mostrarlo en la base de datos, ver la sección 17.

---

## 10. Cierre del negocio (el Apto 1102)

**Cómo probarlo:**
1. *Negocios → Apto 1102 Cañaveral*.
2. Mira la cota de avance: **«Faltan 2 documentos», 67 %**. El certificado de tradición está **vencido** (sello rojo) y el paz y salvo de administración, pendiente.
3. **Subir un PDF** → `demo/pdfs/paz-y-salvo-administracion.pdf`. Con IA, al indexarse el requisito queda **«Sugerido por la IA»**: entra a **Revisar y confirmar** y confírmalo. Sin IA, márcalo tú con **Marcar recibido**.
4. Haz lo mismo con `demo/pdfs/certificado-tradicion-y-libertad.pdf` (expedido hace pocos días, así que vale 30 días).
5. Cuando estén los dos, el negocio queda **«Listo para firmar»**.

**Qué explicar:**
- En la base solo se guarda **lo que hizo una persona**. El estado que ves (vencido, por vencer, no aplica) lo calcula el **motor de reglas** (`MotorReglasCierre`) con la fecha de hoy: por ejemplo, el certificado de tradición vence a los 30 días de expedido.
- La IA **nunca** pasa un documento a «recibido»: solo lo deja «sugerido».
- La lista de requisitos depende del caso (propiedad horizontal, hipoteca, vendedor casado, apoderado, persona jurídica). Es de referencia: Predia no da asesoría legal.

**Para dejarlo como estaba:** en cada requisito, **Reabrir**.

---

## 11. Recordatorios al propietario

**Cómo probarlo:**
1. *Más → Recordatorios*: hay un borrador para Jorge Ramírez Pardo con lo que falta.
2. Edita el texto si quieres → **Aprobar y enviar**.
3. Abre Mailpit (<http://localhost:8026>): ahí está el correo.

**Qué explicar:** la IA (o una plantilla sin IA) redacta, pero el texto se valida en Java: debe mencionar todo lo pendiente, nada más, y ningún número nuevo. **Ningún correo sale sin la aprobación del asesor**; ni el trabajo diario ni el asistente envían nada.

**Para dejarlo como estaba:** en el negocio, **Preparar recordatorio** crea otro borrador; y limpia Mailpit:
```bash
curl -X DELETE http://localhost:8026/api/v1/messages
```

---

## 12. Tasas de crédito (solo ADMIN)

**Cómo probarlo:** entra como Laura → *Más → Tasas*: puede verlas pero no cargar. Entra como `admin@altos.test` → **Cargar una tasa**.

**Qué explicar:** el control es del servidor, no solo de la pantalla: si un asesor intenta cargar una tasa por la API, recibe **403** aunque mande datos inválidos (el permiso se decide antes que la validación).

---

## 13. Requisitos del vendedor (solo ADMIN)

**Cómo probarlo:** como ADMIN, *Más → Requisitos*: cambia la vigencia o en qué casos aplica un documento.

**Qué explicar:** cada inmobiliaria ajusta su catálogo; el motor de reglas lo aplica a sus negocios.

---

## 14. Usuarios (solo ADMIN)

**Cómo probarlo:**
1. Entra como `admin@altos.test` → *Más → Usuarios*.
2. Crea un asesor con un correo inventado (por ejemplo `carlos@altos.test`) → **Crear e invitar**.
3. Abre Mailpit (<http://localhost:8026>): llegó «Te invitaron a Predia…». Abre el enlace **en otra ventana privada** (o cierra sesión antes), elige una contraseña y entras como ese asesor.
4. De vuelta como ADMIN: cámbiale el rol, **Desactivar** (su sesión se cierra al instante: recarga la otra ventana) y **Reactivar**.

**Qué explicar:**
- El enlace sirve **una sola vez** y vence a las **72 horas**; en la base solo queda su huella SHA-256, nunca el enlace.
- **Desactivar corta la sesión de inmediato**: en cada petición Predia comprueba que el usuario siga activo y con el mismo rol.
- El ADMIN no puede quitarse su propio acceso ni dejar la inmobiliaria sin administrador.
- Si alguien olvida la contraseña, **Nueva contraseña** le envía otro enlace.

---

## 15. Portal del propietario

**Cómo probarlo:**
1. Como Laura, *Negocios → Apto 1102* → baja hasta **Portal del propietario** → **Invitar al portal**. Lee el correo tal como le llegará a Jorge → **Enviar invitación**.
2. En Mailpit abre el correo «Siga la venta de Apto 1102 Cañaveral…» y abre el enlace en una **ventana privada**. Jorge crea su contraseña y entra directo a su portal.
3. En el portal: la foto (o el terreno) del inmueble, «Le faltan 2 documentos para firmar», la misma cota, y cada documento con qué significa y cómo conseguirlo.
4. En «Paz y salvo de administración» → **Enviar documento** → `demo/pdfs/paz-y-salvo-administracion.pdf` → **Enviar**. Queda **«En revisión»**.
5. Vuelve a la ventana de Laura y recarga el negocio: el documento aparece **«Enviado por el propietario»**, para que ella lo confirme.
6. Pruébalo en el celular (o con la ventana angosta): el portal está pensado para eso.

**Qué explicar:**
- El propietario **no puede salir de su portal**: si intenta abrir cualquier otra parte de la API recibe 403, y si prueba con el número de otro negocio, 404. Hay una prueba automática que recorre esa lista.
- No ve el nombre del comprador ni nada del resto de la inmobiliaria.
- Lo que envía **nunca queda «recibido»**: queda en revisión hasta que el asesor lo confirma. Es el mismo principio que con la IA.
- Después de crear su contraseña entra **cuando quiera** con su correo, incluso después de firmar. Su acceso solo termina si el asesor lo desactiva.

**Para dejarlo como estaba:** en el negocio, **Reabrir** el paz y salvo; **Desactivar acceso** de Jorge (o bórralo en la base: `DELETE FROM usuario WHERE rol = 'PROPIETARIO';`) y limpia Mailpit.

---

## 16. Asistente (necesita IA)

**Cómo probarlo:** *Asistente* → pregunta:
- «¿Qué le falta al dueño del apto 1102 de Cañaveral para firmar?»
- «¿Qué les alcanza a Paola y Juan en Floridablanca?»
- «¿El apto 1102 incluye parqueadero?» (necesita el contrato de la sección 9)
- «¿Cuánto ahorran Paola y Juan si abonan 500 mil al mes a un crédito de 150 millones al 11 % a 20 años?»

**Qué deberías ver:** debajo de cada respuesta, qué herramientas usó y las citas. Si el modelo escribe un número que ninguna herramienta entregó, aparece un aviso ámbar.

**Qué explicar:**
- Tool calling con Spring AI: el modelo **elige** entre siete herramientas, pero cada una llama al mismo servicio Java que usa la pantalla. Las cifras llegan ya escritas por Java.
- La inmobiliaria sale del usuario autenticado, **nunca de un argumento del modelo**.
- `GuardiaCifras` compara cada número de la respuesta con lo que devolvieron las herramientas.
- Sin IA, el asistente avisa que está deshabilitado y el resto de Predia sigue funcionando.

---

## 17. Ver la base de datos

Predia usa PostgreSQL con pgvector en Docker (puerto **5434**).

### Desde la terminal

```bash
docker exec -it predia-postgres psql -U predia -d predia
```

Consultas útiles (escribe `\q` para salir):

```sql
-- Las tablas
\dt

-- Los usuarios que pueden entrar (la contraseña está cifrada con BCrypt; nunca se guarda en claro)
SELECT id, nombre, email, rol, activo, propietario_id, ultimo_ingreso FROM usuario;

-- Las invitaciones: solo la huella del enlace, cuándo vence y si ya se usó
SELECT usuario_id, left(token_hash, 12) AS huella, expira_en, usada_en FROM invitacion_acceso;

-- Los clientes (compradores) y los propietarios: no inician sesión
SELECT id, nombre, ingreso_familiar, ahorro FROM cliente;
SELECT id, nombre, numero_documento FROM propietario;

-- Prueba de que el PDF no se guarda: del documento queda su hash, no el archivo
SELECT id, nombre_archivo, hash_sha256, tamano_bytes, estado_indexacion FROM documento;

-- Sus fragmentos con página, sección y modelo del vector
SELECT documento_id, orden, pagina_inicio, seccion, tokens, embedding_modelo, left(texto, 60)
FROM fragmento ORDER BY documento_id, orden;

-- Lo que solo hizo una persona en el cierre (el estado efectivo lo calcula Java)
SELECT r.id, c.nombre, r.estado_registrado, r.fecha_expedicion
FROM requisito_negocio r JOIN requisito_catalogo c ON c.id = r.requisito_id;
```

> Si alguna tabla o columna tiene otro nombre, `\d nombre_tabla` muestra su estructura.

### Con una aplicación gráfica

Cualquier cliente de PostgreSQL sirve (TablePlus, DBeaver, pgAdmin o la extensión **PostgreSQL** de VS Code). Los datos de conexión son los de tu `.env`:

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Puerto | `5434` |
| Base de datos | `predia` |
| Usuario | `predia` |
| Contraseña | la de `DB_PASSWORD` (en el ejemplo, `predia`) |

**Cuidado:** desde ahí puedes modificar datos. Para la demo, solo consulta.

---

## 18. La carpeta `demo/` y los datos de demostración

- **`demo/pdfs/`** son cinco PDF **ficticios** para subir a mano durante la demo. Predia no los carga solos:

| Archivo | Para qué sirve en la demo |
|---|---|
| `contrato-arrendamiento.pdf` | Expedientes (RAG): canon de $1.800.000, parqueadero, mascotas |
| `escritura-publica-4521.pdf` | Documento del vendedor: escritura con la que adquirió el inmueble |
| `reglamento-propiedad-horizontal.pdf` | Parqueaderos y el paz y salvo de administración para vender |
| `certificado-tradicion-y-libertad.pdf` | Reemplaza el certificado vencido del 1102 |
| `paz-y-salvo-administracion.pdf` | Cubre el documento pendiente del 1102 |

  Se regeneran con `./mvnw -q compile exec:java -Dexec.mainClass=co.predia.comun.demo.GeneradorPdfsDemo -Dexec.args="demo/pdfs"`. Ninguno es escaneado, así que no ponen a prueba el OCR: para mostrarlo, sube un PDF que sea una foto o un escaneo.

- **`DatosDemo`** (código, no carpeta) siembra al arrancar la inmobiliaria, los dos usuarios, Paola y Juan, los inmuebles, la tasa de ejemplo y el negocio del 1102. Es idempotente: no duplica nada si ya existe.

### Para producción

- `DEMO_HABILITADA=false`: no se siembra nada ficticio. La carpeta `demo/` puede quedarse en el repositorio (no se usa sola).
- **El primer administrador** se crea con las variables `PREDIA_ADMIN_*` (ver [DESPLIEGUE.md](DESPLIEGUE.md)); después él invita al resto desde *Usuarios*.
- Un `JWT_SECRET` largo y aleatorio, otra contraseña de base de datos, y HTTPS (ver [DESPLIEGUE.md](DESPLIEGUE.md)).
- Copias de seguridad de PostgreSQL: ahí están los fragmentos, los vectores y las fotos.

---

## 19. Pruebas automáticas

```bash
./mvnw test
```

Corre **203 pruebas** sobre un PostgreSQL con pgvector real (en un contenedor desechable) y **ninguna llama a OpenAI**. Cubren, entre otras cosas, que una inmobiliaria nunca ve datos de otra, que un propietario no puede salir de su portal, que la IA nunca deja un documento «recibido», que no se envía un correo sin aprobación, y que las cifras del crédito y del presupuesto coinciden con las fórmulas.

---

## Orden sugerido para la presentación (≈ 10 minutos)

1. Ingreso y la idea de «la IA sugiere, las reglas deciden, la persona confirma» (1 min).
2. **Expedientes:** subir el contrato, preguntar con cita y la pregunta sin respuesta (3 min).
3. **Cierre del 1102:** la cota, el sello de vencido, subir el paz y salvo y confirmarlo (2 min).
4. **Presupuesto → búsqueda → simulación** de Paola y Juan (2 min).
5. **Recordatorio** aprobado y visto en Mailpit (1 min).
6. **Portal del propietario:** invitar a Jorge y verlo desde el celular (1 min).
7. **Asistente** con una pregunta, si hay IA (1 min).
8. Si preguntan por la base de datos: la consulta de `documento` y `fragmento` de la sección 17.
