-- Predia · V10: fotos de perfil de personas (asesores, clientes y vendedores).
-- Una sola tabla aparte para que listar personas nunca cargue bytes. Como en las fotos de inmuebles, solo se guardan copias
-- reducidas y recortadas en cuadrado (nunca el archivo original): «grande» para la ficha y «miniatura» para las listas.
-- Una foto es un dato personal: solo se guarda de un cliente o vendedor que autorizó el tratamiento de sus datos.

CREATE TABLE foto_perfil (
    id              BIGSERIAL    PRIMARY KEY,
    inmobiliaria_id BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    sujeto          VARCHAR(12)  NOT NULL CHECK (sujeto IN ('USUARIO', 'CLIENTE', 'PROPIETARIO')),
    sujeto_id       BIGINT       NOT NULL,
    grande          BYTEA        NOT NULL,
    miniatura       BYTEA        NOT NULL,
    hash            VARCHAR(64)  NOT NULL,
    actualizado_por BIGINT       REFERENCES usuario (id),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_foto_perfil UNIQUE (inmobiliaria_id, sujeto, sujeto_id)
);
