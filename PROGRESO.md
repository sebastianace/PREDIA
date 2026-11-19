# Progreso de Predia

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
