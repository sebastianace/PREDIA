-- Predia · V2: expedientes (documentos, trabajos de indexación y fragmentos)
-- El PDF original NUNCA se guarda aquí: solo su hash, sus fragmentos de texto y sus vectores.

CREATE TABLE documento (
    id                          BIGSERIAL PRIMARY KEY,
    inmobiliaria_id             BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    inmueble_id                 BIGINT       NOT NULL REFERENCES inmueble (id),
    negocio_id                  BIGINT,
    nombre_archivo              VARCHAR(255) NOT NULL,
    hash_sha256                 VARCHAR(64)  NOT NULL,
    tamano_bytes                BIGINT       NOT NULL CHECK (tamano_bytes > 0),
    paginas                     INT,
    paginas_ocr                 INT          NOT NULL DEFAULT 0,
    estado_indexacion           VARCHAR(12)  NOT NULL DEFAULT 'EN_COLA'
                                CHECK (estado_indexacion IN ('EN_COLA', 'PROCESANDO', 'INDEXADO', 'ERROR')),
    error                       TEXT,
    indexado_en                 TIMESTAMPTZ,
    vectorizado                 BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Clasificación sugerida por la IA; solo el asesor la pasa a CONFIRMADA.
    tipo_sugerido               VARCHAR(60),
    fecha_expedicion_sugerida   DATE,
    confianza                   NUMERIC(4, 3),
    clasificacion_estado        VARCHAR(16)  NOT NULL DEFAULT 'SIN_CLASIFICAR'
                                CHECK (clasificacion_estado IN ('SIN_CLASIFICAR', 'SUGERIDA', 'CONFIRMADA')),
    tipo_confirmado              VARCHAR(60),
    fecha_expedicion_confirmada  DATE,
    confirmado_por               BIGINT REFERENCES usuario (id),
    confirmado_en                TIMESTAMPTZ,
    roles_permitidos            TEXT[]       NOT NULL DEFAULT '{ADMIN,ASESOR}',
    subido_por                  BIGINT       NOT NULL REFERENCES usuario (id),
    creado_en                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_documento_hash UNIQUE (inmobiliaria_id, hash_sha256)
);
CREATE INDEX ix_documento_inmueble ON documento (inmobiliaria_id, inmueble_id);
CREATE INDEX ix_documento_negocio ON documento (inmobiliaria_id, negocio_id) WHERE negocio_id IS NOT NULL;
CREATE INDEX ix_documento_estado ON documento (inmobiliaria_id, estado_indexacion);

-- Un trabajo por documento. El procesador toma trabajos con FOR UPDATE SKIP LOCKED: no hace falta un broker.
CREATE TABLE trabajo_indexacion (
    id               BIGSERIAL PRIMARY KEY,
    inmobiliaria_id  BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    documento_id     BIGINT       NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    ruta_temporal    VARCHAR(500) NOT NULL,
    estado           VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE'
                     CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'TERMINADO', 'FALLIDO')),
    intentos         INT          NOT NULL DEFAULT 0,
    proximo_intento  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    error            TEXT,
    version          BIGINT       NOT NULL DEFAULT 0,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_trabajo_documento UNIQUE (documento_id)
);
CREATE INDEX ix_trabajo_pendientes ON trabajo_indexacion (proximo_intento) WHERE estado = 'PENDIENTE';

CREATE TABLE fragmento (
    id               BIGSERIAL PRIMARY KEY,
    inmobiliaria_id  BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    documento_id     BIGINT       NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    inmueble_id      BIGINT       NOT NULL REFERENCES inmueble (id),
    orden            INT          NOT NULL,
    pagina_inicio    INT          NOT NULL,
    pagina_fin       INT          NOT NULL,
    seccion          VARCHAR(200),
    texto            TEXT         NOT NULL,
    tokens           INT          NOT NULL,
    es_ocr           BOOLEAN      NOT NULL DEFAULT FALSE,
    embedding        vector(1536),
    embedding_modelo VARCHAR(80),
    roles_permitidos TEXT[]       NOT NULL DEFAULT '{ADMIN,ASESOR}',
    -- Búsqueda de texto en español; se combina con la búsqueda vectorial (Fase 3).
    tsv              tsvector GENERATED ALWAYS AS (
                         to_tsvector('spanish', coalesce(seccion, '') || ' ' || texto)
                     ) STORED,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_fragmento_orden UNIQUE (documento_id, orden)
);
CREATE INDEX ix_fragmento_documento ON fragmento (inmobiliaria_id, documento_id, orden);
CREATE INDEX ix_fragmento_tsv ON fragmento USING gin (tsv);
CREATE INDEX ix_fragmento_embedding ON fragmento USING hnsw (embedding vector_cosine_ops);
