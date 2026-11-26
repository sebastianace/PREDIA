-- Predia · V8: portal del comprador.
-- El comprador entra a su portal por invitación y ve solo los inmuebles que su asesor le compartió.

ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check CHECK (rol IN ('ADMIN', 'ASESOR', 'PROPIETARIO', 'COMPRADOR'));
-- Borrar un cliente borra también su cuenta (privacidad: «Eliminar cliente y sus datos»).
ALTER TABLE usuario ADD COLUMN cliente_id BIGINT REFERENCES cliente (id) ON DELETE CASCADE;
ALTER TABLE usuario ADD CONSTRAINT usuario_cliente_check CHECK ((rol = 'COMPRADOR') = (cliente_id IS NOT NULL));
CREATE UNIQUE INDEX ux_usuario_cliente ON usuario (cliente_id) WHERE cliente_id IS NOT NULL;

-- La lista blanca: lo que no esté aquí, el comprador no lo ve.
CREATE TABLE inmueble_compartido (
    id              BIGSERIAL PRIMARY KEY,
    inmobiliaria_id BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    cliente_id      BIGINT       NOT NULL REFERENCES cliente (id) ON DELETE CASCADE,
    inmueble_id     BIGINT       NOT NULL REFERENCES inmueble (id) ON DELETE CASCADE,
    nota            VARCHAR(500),
    compartido_por  BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    compartido_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    interes         VARCHAR(14)  CHECK (interes IN ('ME_INTERESA', 'NO_ME_INTERESA')),
    interes_en      TIMESTAMPTZ,
    CONSTRAINT ux_compartido UNIQUE (cliente_id, inmueble_id)
);
CREATE INDEX ix_compartido_inmueble ON inmueble_compartido (inmueble_id);

-- «Quiero más información»: un aviso al asesor. Nunca un correo ni una respuesta automática.
CREATE TABLE solicitud_comprador (
    id              BIGSERIAL PRIMARY KEY,
    inmobiliaria_id BIGINT        NOT NULL REFERENCES inmobiliaria (id),
    cliente_id      BIGINT        NOT NULL REFERENCES cliente (id) ON DELETE CASCADE,
    inmueble_id     BIGINT        REFERENCES inmueble (id) ON DELETE SET NULL,
    mensaje         VARCHAR(1000) NOT NULL,
    creada_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atendida_en     TIMESTAMPTZ,
    atendida_por    BIGINT        REFERENCES usuario (id) ON DELETE SET NULL
);
CREATE INDEX ix_solicitud_pendientes ON solicitud_comprador (inmobiliaria_id, creada_en) WHERE atendida_en IS NULL;

-- El plano de un inmueble: una sola copia reducida, que se muestra completa (un plano no admite recorte).
CREATE TABLE plano_inmueble (
    inmueble_id     BIGINT      PRIMARY KEY REFERENCES inmueble (id) ON DELETE CASCADE,
    inmobiliaria_id BIGINT      NOT NULL REFERENCES inmobiliaria (id),
    imagen          BYTEA       NOT NULL,
    ancho           INTEGER     NOT NULL CHECK (ancho > 0),
    alto            INTEGER     NOT NULL CHECK (alto > 0),
    hash            VARCHAR(64) NOT NULL,
    subido_por      BIGINT      REFERENCES usuario (id) ON DELETE SET NULL,
    actualizada_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_plano_inmobiliaria ON plano_inmueble (inmobiliaria_id);
