-- Predia · V6: foto de portada de cada inmueble.
-- Se guardan solo dos copias ya reducidas (grande para el detalle y miniatura para las listas), nunca el archivo original.
-- Viven en una tabla aparte para que listar inmuebles no arrastre los bytes de las imágenes.
-- Es una decisión distinta a la de los PDF: el PDF del cliente no se conserva; la foto de un inmueble sí, porque es
-- material comercial de la propia inmobiliaria.

CREATE TABLE foto_inmueble (
    inmueble_id     BIGINT      PRIMARY KEY REFERENCES inmueble (id) ON DELETE CASCADE,
    inmobiliaria_id BIGINT      NOT NULL REFERENCES inmobiliaria (id),
    grande          BYTEA       NOT NULL,
    miniatura       BYTEA       NOT NULL,
    ancho           INTEGER     NOT NULL CHECK (ancho > 0),
    alto            INTEGER     NOT NULL CHECK (alto > 0),
    -- Punto de la imagen que debe quedar visible al recortarla (0 a 100, desde arriba/izquierda).
    foco_x          SMALLINT    NOT NULL DEFAULT 50 CHECK (foco_x BETWEEN 0 AND 100),
    foco_y          SMALLINT    NOT NULL DEFAULT 50 CHECK (foco_y BETWEEN 0 AND 100),
    hash            VARCHAR(64) NOT NULL,
    subida_por      BIGINT      REFERENCES usuario (id) ON DELETE SET NULL,
    actualizada_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_foto_inmueble_inmobiliaria ON foto_inmueble (inmobiliaria_id);
