-- Predia · V4: clientes compradores (con autorización de datos, Ley 1581 de 2012) y tasas de crédito por banco.

CREATE TABLE cliente (
    id                          BIGSERIAL PRIMARY KEY,
    inmobiliaria_id             BIGINT         NOT NULL REFERENCES inmobiliaria (id),
    nombre                      VARCHAR(150)   NOT NULL,
    email                       VARCHAR(150),
    telefono                    VARCHAR(30),
    ingreso_familiar            NUMERIC(15, 2) NOT NULL CHECK (ingreso_familiar >= 0),
    ahorro                      NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (ahorro >= 0),
    deudas_mensuales            NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (deudas_mensuales >= 0),
    intencion                   VARCHAR(10)    NOT NULL CHECK (intencion IN ('COMPRAR', 'ARRENDAR')),
    habitaciones_min            SMALLINT       NOT NULL DEFAULT 0 CHECK (habitaciones_min >= 0),
    zonas                       TEXT[]         NOT NULL DEFAULT '{}',
    preferencias                TEXT           NOT NULL DEFAULT '',
    -- Sin autorización de tratamiento de datos no se guarda el cliente: lo exige la propia base de datos.
    autorizacion_datos          BOOLEAN        NOT NULL CHECK (autorizacion_datos),
    autorizacion_fecha          TIMESTAMPTZ    NOT NULL,
    autorizacion_registrada_por BIGINT         NOT NULL REFERENCES usuario (id),
    creado_en                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en              TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_cliente_inmobiliaria ON cliente (inmobiliaria_id, nombre);

-- Tasas de crédito hipotecario por banco. Las carga el ADMIN; las de la demo van marcadas como "de ejemplo".
CREATE TABLE tasa_credito (
    id               BIGSERIAL PRIMARY KEY,
    inmobiliaria_id  BIGINT        NOT NULL REFERENCES inmobiliaria (id),
    banco            VARCHAR(100)  NOT NULL,
    tipo             VARCHAR(6)    NOT NULL CHECK (tipo IN ('VIS', 'NO_VIS')),
    -- Efectiva anual como fracción: 0.12 = 12 % E.A.
    tasa_ea          NUMERIC(9, 6) NOT NULL CHECK (tasa_ea > 0 AND tasa_ea < 1),
    vigente_desde    DATE          NOT NULL,
    fuente           VARCHAR(200)  NOT NULL,
    fecha_carga      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    es_ejemplo       BOOLEAN       NOT NULL DEFAULT FALSE,
    cargada_por      BIGINT        REFERENCES usuario (id)
);
CREATE INDEX ix_tasa_credito_vigente ON tasa_credito (inmobiliaria_id, tipo, vigente_desde DESC);
