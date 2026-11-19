-- Predia · V1: base multi-inmobiliaria (tenants, usuarios, parámetros e inventario)
-- Regla: toda tabla de negocio lleva inmobiliaria_id y sus índices empiezan por esa columna.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE inmobiliaria (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    nit         VARCHAR(20)  NOT NULL UNIQUE,
    ciudad      VARCHAR(100) NOT NULL,
    creada_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id               BIGSERIAL PRIMARY KEY,
    inmobiliaria_id  BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    nombre           VARCHAR(150) NOT NULL,
    email            VARCHAR(150) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    rol              VARCHAR(20)  NOT NULL CHECK (rol IN ('ADMIN', 'ASESOR')),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- El email identifica al usuario en el login, por eso es único en todo el sistema.
CREATE UNIQUE INDEX ux_usuario_email ON usuario (lower(email));
CREATE INDEX ix_usuario_inmobiliaria ON usuario (inmobiliaria_id);

-- Parámetros de reglas de negocio, configurables por inmobiliaria (los edita el ADMIN).
CREATE TABLE parametros_inmobiliaria (
    inmobiliaria_id            BIGINT PRIMARY KEY REFERENCES inmobiliaria (id),
    pct_cuota_max_ingreso      NUMERIC(5, 4) NOT NULL DEFAULT 0.30 CHECK (pct_cuota_max_ingreso > 0 AND pct_cuota_max_ingreso <= 1),
    pct_cuota_inicial          NUMERIC(5, 4) NOT NULL DEFAULT 0.30 CHECK (pct_cuota_inicial > 0 AND pct_cuota_inicial <= 1),
    pct_cuota_inicial_vis      NUMERIC(5, 4) NOT NULL DEFAULT 0.20 CHECK (pct_cuota_inicial_vis > 0 AND pct_cuota_inicial_vis <= 1),
    plazo_default_anios        INT           NOT NULL DEFAULT 20 CHECK (plazo_default_anios BETWEEN 1 AND 30),
    dias_alerta_vencimiento    INT           NOT NULL DEFAULT 7 CHECK (dias_alerta_vencimiento >= 0),
    ciudades_con_valorizacion  TEXT[]        NOT NULL DEFAULT '{}',
    umbral_similitud_rag       NUMERIC(4, 3) NOT NULL DEFAULT 0.300 CHECK (umbral_similitud_rag BETWEEN 0 AND 1),
    email_remitente            VARCHAR(150),
    actualizado_en             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE inmueble (
    id                    BIGSERIAL PRIMARY KEY,
    inmobiliaria_id       BIGINT        NOT NULL REFERENCES inmobiliaria (id),
    codigo                VARCHAR(30)   NOT NULL,
    titulo                VARCHAR(150)  NOT NULL,
    tipo                  VARCHAR(20)   NOT NULL CHECK (tipo IN ('APARTAMENTO', 'CASA', 'LOCAL', 'OFICINA', 'LOTE')),
    direccion             VARCHAR(200)  NOT NULL,
    barrio                VARCHAR(100)  NOT NULL,
    ciudad                VARCHAR(100)  NOT NULL,
    estrato               SMALLINT      CHECK (estrato BETWEEN 1 AND 6),
    area_m2               NUMERIC(8, 2) NOT NULL CHECK (area_m2 > 0),
    habitaciones          SMALLINT      NOT NULL DEFAULT 0 CHECK (habitaciones >= 0),
    banos                 SMALLINT      NOT NULL DEFAULT 0 CHECK (banos >= 0),
    parqueaderos          SMALLINT      NOT NULL DEFAULT 0 CHECK (parqueaderos >= 0),
    acepta_mascotas       BOOLEAN       NOT NULL DEFAULT FALSE,
    valor_administracion  NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (valor_administracion >= 0),
    operacion             VARCHAR(10)   NOT NULL CHECK (operacion IN ('VENTA', 'ARRIENDO')),
    precio_venta          NUMERIC(15, 2) CHECK (precio_venta > 0),
    canon_arriendo        NUMERIC(15, 2) CHECK (canon_arriendo > 0),
    es_vis                BOOLEAN       NOT NULL DEFAULT FALSE,
    propiedad_horizontal  BOOLEAN       NOT NULL DEFAULT FALSE,
    descripcion           TEXT          NOT NULL DEFAULT '',
    estado                VARCHAR(12)   NOT NULL DEFAULT 'DISPONIBLE'
                          CHECK (estado IN ('DISPONIBLE', 'SEPARADO', 'ARRENDADO', 'VENDIDO')),
    -- El embedding se escribe con SQL explícito (JdbcTemplate); la entidad JPA no lo mapea.
    embedding             vector(1536),
    embedding_modelo      VARCHAR(80),
    creado_en             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_inmueble_precio CHECK (
        (operacion = 'VENTA' AND precio_venta IS NOT NULL) OR
        (operacion = 'ARRIENDO' AND canon_arriendo IS NOT NULL)
    ),
    CONSTRAINT ux_inmueble_codigo UNIQUE (inmobiliaria_id, codigo)
);
CREATE INDEX ix_inmueble_busqueda ON inmueble (inmobiliaria_id, estado, operacion, habitaciones);
CREATE INDEX ix_inmueble_embedding ON inmueble USING hnsw (embedding vector_cosine_ops);
