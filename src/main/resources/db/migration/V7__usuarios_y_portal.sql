-- Predia · V7: gestión de usuarios, invitaciones y portal del propietario.

-- El propietario que vende puede entrar a su portal. Un usuario propietario siempre apunta a su ficha;
-- uno del personal (ADMIN, ASESOR) nunca.
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check CHECK (rol IN ('ADMIN', 'ASESOR', 'PROPIETARIO'));
ALTER TABLE usuario ADD COLUMN propietario_id BIGINT REFERENCES propietario (id) ON DELETE CASCADE;
ALTER TABLE usuario ADD CONSTRAINT usuario_propietario_check CHECK ((rol = 'PROPIETARIO') = (propietario_id IS NOT NULL));
CREATE UNIQUE INDEX ux_usuario_propietario ON usuario (propietario_id) WHERE propietario_id IS NOT NULL;

-- Mientras no active su cuenta (con el enlace de invitación) el usuario no tiene contraseña y no puede entrar.
ALTER TABLE usuario ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT usuario_activo_con_clave_check CHECK (NOT activo OR password_hash IS NOT NULL);
ALTER TABLE usuario ADD COLUMN creado_por BIGINT REFERENCES usuario (id) ON DELETE SET NULL;
ALTER TABLE usuario ADD COLUMN ultimo_ingreso TIMESTAMPTZ;

-- Enlace para crear (o recuperar) la contraseña. Solo se guarda el SHA-256 del token: el token en claro va
-- únicamente en el enlace. Vence a las 72 horas y sirve una sola vez.
CREATE TABLE invitacion_acceso (
    id              BIGSERIAL PRIMARY KEY,
    inmobiliaria_id BIGINT      NOT NULL REFERENCES inmobiliaria (id),
    usuario_id      BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    expira_en       TIMESTAMPTZ NOT NULL,
    usada_en        TIMESTAMPTZ,
    creada_por      BIGINT      REFERENCES usuario (id) ON DELETE SET NULL,
    creada_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_invitacion_usuario ON invitacion_acceso (usuario_id, creada_en DESC);

-- Quién hizo la sugerencia de un requisito: la IA (al clasificar un documento) o el propietario (desde su portal).
ALTER TABLE requisito_negocio ADD COLUMN sugerido_por VARCHAR(12) CHECK (sugerido_por IN ('IA', 'PROPIETARIO'));
UPDATE requisito_negocio SET sugerido_por = 'IA' WHERE estado_registrado = 'SUGERIDO';
