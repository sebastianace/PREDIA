-- Predia · V5: cierre del negocio (propietario-vendedor, negocio, catálogo de requisitos y recordatorios).
-- En la base solo queda lo que registra una persona (o la sugerencia de la IA); el estado que se muestra
-- (VENCIDO, POR_VENCER, NO_APLICA…) lo calcula siempre el motor de reglas en Java.

CREATE TABLE propietario (
    id                          BIGSERIAL PRIMARY KEY,
    inmobiliaria_id             BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    tipo_persona                VARCHAR(10)  NOT NULL CHECK (tipo_persona IN ('NATURAL', 'JURIDICA')),
    nombre                      VARCHAR(150) NOT NULL,
    tipo_documento              VARCHAR(12)  NOT NULL,
    numero_documento            VARCHAR(30)  NOT NULL,
    email                       VARCHAR(150),
    telefono                    VARCHAR(30),
    estado_civil                VARCHAR(20)  CHECK (estado_civil IN ('SOLTERO', 'CASADO', 'UNION_MARITAL', 'DIVORCIADO', 'VIUDO')),
    -- Datos personales: sin la autorización de tratamiento de datos no se guarda (Ley 1581 de 2012).
    autorizacion_datos          BOOLEAN      NOT NULL CHECK (autorizacion_datos),
    autorizacion_fecha          TIMESTAMPTZ  NOT NULL,
    autorizacion_registrada_por BIGINT       NOT NULL REFERENCES usuario (id),
    creado_en                   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_propietario_inmobiliaria ON propietario (inmobiliaria_id, nombre);

CREATE TABLE negocio (
    id                     BIGSERIAL PRIMARY KEY,
    inmobiliaria_id        BIGINT      NOT NULL REFERENCES inmobiliaria (id),
    inmueble_id            BIGINT      NOT NULL REFERENCES inmueble (id),
    propietario_id         BIGINT      NOT NULL REFERENCES propietario (id),
    cliente_comprador_id   BIGINT      REFERENCES cliente (id) ON DELETE SET NULL,
    asesor_id              BIGINT      NOT NULL REFERENCES usuario (id),
    fecha_firma_tentativa  DATE,
    actua_por_apoderado    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Lo confirma el asesor; la IA no lo decide.
    hipoteca_vigente       VARCHAR(12) NOT NULL DEFAULT 'DESCONOCIDO' CHECK (hipoteca_vigente IN ('SI', 'NO', 'DESCONOCIDO')),
    estado                 VARCHAR(10) NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'FIRMADO', 'CANCELADO')),
    creado_en              TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_negocio_inmobiliaria ON negocio (inmobiliaria_id, estado);
CREATE INDEX ix_negocio_asesor ON negocio (inmobiliaria_id, asesor_id);

-- Los documentos subidos para un negocio (columna creada en la Fase 2) ahora sí apuntan a un negocio real.
ALTER TABLE documento
    ADD CONSTRAINT fk_documento_negocio FOREIGN KEY (negocio_id) REFERENCES negocio (id) ON DELETE SET NULL;

CREATE TABLE requisito_catalogo (
    id               BIGSERIAL PRIMARY KEY,
    inmobiliaria_id  BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    codigo           VARCHAR(50)  NOT NULL,
    nombre           VARCHAR(200) NOT NULL,
    descripcion      TEXT         NOT NULL DEFAULT '',
    orden            INT          NOT NULL DEFAULT 0,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    regla_aplica     VARCHAR(30)  NOT NULL CHECK (regla_aplica IN ('SIEMPRE', 'SI_VALORIZACION', 'SI_PROPIEDAD_HORIZONTAL',
                                   'SI_HIPOTECA', 'SI_CASADO_O_UNION', 'SI_APODERADO', 'SI_PERSONA_JURIDICA')),
    -- false = práctica recomendada: se muestra, pero no bloquea el "Listo para firmar".
    obligatorio      BOOLEAN      NOT NULL DEFAULT TRUE,
    regla_vigencia   VARCHAR(30)  NOT NULL DEFAULT 'NINGUNA' CHECK (regla_vigencia IN ('NINGUNA', 'DIAS_DESDE_EXPEDICION',
                                   'ANIO_EN_CURSO', 'MES_EN_CURSO')),
    vigencia_dias    INT          CHECK (vigencia_dias IS NULL OR vigencia_dias > 0),
    -- Une el requisito con lo que clasifica la IA al indexar un documento.
    tipo_documento   VARCHAR(60),
    -- Se usan para validar que un recordatorio no mencione documentos que no están en la lista.
    palabras_clave   TEXT[]       NOT NULL DEFAULT '{}',
    CONSTRAINT ux_requisito_codigo UNIQUE (inmobiliaria_id, codigo),
    CONSTRAINT ck_vigencia_dias CHECK (regla_vigencia <> 'DIAS_DESDE_EXPEDICION' OR vigencia_dias IS NOT NULL)
);

CREATE TABLE requisito_negocio (
    id                BIGSERIAL PRIMARY KEY,
    inmobiliaria_id   BIGINT      NOT NULL REFERENCES inmobiliaria (id),
    negocio_id        BIGINT      NOT NULL REFERENCES negocio (id) ON DELETE CASCADE,
    requisito_id      BIGINT      NOT NULL REFERENCES requisito_catalogo (id) ON DELETE CASCADE,
    documento_id      BIGINT      REFERENCES documento (id) ON DELETE SET NULL,
    estado_registrado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                      CHECK (estado_registrado IN ('PENDIENTE', 'SUGERIDO', 'RECIBIDO', 'NO_APLICA_MANUAL')),
    fecha_expedicion  DATE,
    sugerido_en       TIMESTAMPTZ,
    recibido_por      BIGINT      REFERENCES usuario (id),
    recibido_en       TIMESTAMPTZ,
    nota              TEXT,
    CONSTRAINT ux_requisito_negocio UNIQUE (negocio_id, requisito_id)
);
CREATE INDEX ix_requisito_negocio_inmobiliaria ON requisito_negocio (inmobiliaria_id, negocio_id);

CREATE TABLE recordatorio (
    id                  BIGSERIAL PRIMARY KEY,
    inmobiliaria_id     BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    negocio_id          BIGINT       NOT NULL REFERENCES negocio (id) ON DELETE CASCADE,
    destinatario_email  VARCHAR(150),
    estado              VARCHAR(16)  NOT NULL DEFAULT 'SUGERIDO'
                        CHECK (estado IN ('SUGERIDO', 'APROBADO', 'ENVIADO', 'DESCARTADO', 'ERROR_ENVIO')),
    -- Foto de lo que entregó el motor de reglas: el historial de "qué se pidió".
    items               JSONB        NOT NULL,
    asunto              VARCHAR(200) NOT NULL,
    cuerpo_sugerido     TEXT         NOT NULL,
    cuerpo_final        TEXT,
    origen_texto        VARCHAR(10)  NOT NULL CHECK (origen_texto IN ('IA', 'PLANTILLA')),
    creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    editado_por         BIGINT       REFERENCES usuario (id),
    aprobado_por        BIGINT       REFERENCES usuario (id),
    aprobado_en         TIMESTAMPTZ,
    enviado_en          TIMESTAMPTZ,
    error               TEXT
);
CREATE INDEX ix_recordatorio_bandeja ON recordatorio (inmobiliaria_id, estado, creado_en DESC);
CREATE INDEX ix_recordatorio_negocio ON recordatorio (negocio_id, creado_en DESC);
