-- Predia · V3: trazabilidad de las consultas al asistente de expedientes (quién preguntó qué y con qué evidencia).

CREATE TABLE consulta_rag (
    id               BIGSERIAL PRIMARY KEY,
    inmobiliaria_id  BIGINT      NOT NULL REFERENCES inmobiliaria (id),
    usuario_id       BIGINT      NOT NULL REFERENCES usuario (id),
    inmueble_id      BIGINT REFERENCES inmueble (id),
    pregunta         TEXT        NOT NULL,
    respuesta        TEXT,
    fragmentos_ids   BIGINT[]    NOT NULL DEFAULT '{}',
    sin_evidencia    BOOLEAN     NOT NULL DEFAULT FALSE,
    modo_manual      BOOLEAN     NOT NULL DEFAULT FALSE,
    modelo_chat      VARCHAR(80),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_consulta_rag_inmobiliaria ON consulta_rag (inmobiliaria_id, creado_en DESC);
