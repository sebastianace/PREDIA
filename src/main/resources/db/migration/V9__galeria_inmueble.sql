-- Predia · V9: galería de imágenes por inmueble.
-- Reemplaza la foto única (V6) y el plano único (V8) por una sola galería ordenada: cada imagen es una FOTO o un PLANO.
-- La primera FOTO por orden es la portada del inventario. Se guardan solo copias ya reducidas (nunca el archivo original):
-- «grande» para el visor y «miniatura» para las listas y la tira del carrusel.

CREATE TABLE imagen_inmueble (
    id              BIGSERIAL    PRIMARY KEY,
    inmueble_id     BIGINT       NOT NULL REFERENCES inmueble (id) ON DELETE CASCADE,
    inmobiliaria_id BIGINT       NOT NULL REFERENCES inmobiliaria (id),
    tipo            VARCHAR(5)   NOT NULL CHECK (tipo IN ('FOTO', 'PLANO')),
    orden           INTEGER      NOT NULL CHECK (orden >= 0),
    etiqueta        VARCHAR(60),
    grande          BYTEA        NOT NULL,
    miniatura       BYTEA        NOT NULL,
    ancho           INTEGER      NOT NULL CHECK (ancho > 0),
    alto            INTEGER      NOT NULL CHECK (alto > 0),
    -- Punto de la foto que debe quedar visible al recortarla (0 a 100). Un plano nunca se recorta.
    foco_x          SMALLINT     NOT NULL DEFAULT 50 CHECK (foco_x BETWEEN 0 AND 100),
    foco_y          SMALLINT     NOT NULL DEFAULT 50 CHECK (foco_y BETWEEN 0 AND 100),
    hash            VARCHAR(64)  NOT NULL,
    subida_por      BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    creada_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_imagen_inmueble ON imagen_inmueble (inmobiliaria_id, inmueble_id, orden);

-- Lo que ya estaba subido pasa a la galería: la foto de portada primero y el plano después.
INSERT INTO imagen_inmueble (inmueble_id, inmobiliaria_id, tipo, orden, grande, miniatura, ancho, alto, foco_x, foco_y,
                             hash, subida_por, creada_en)
SELECT inmueble_id, inmobiliaria_id, 'FOTO', 0, grande, miniatura, ancho, alto, foco_x, foco_y, hash, subida_por,
       actualizada_en
FROM foto_inmueble;

-- El plano antiguo no tenía miniatura: la imagen completa hace de miniatura hasta que se vuelva a subir.
INSERT INTO imagen_inmueble (inmueble_id, inmobiliaria_id, tipo, orden, etiqueta, grande, miniatura, ancho, alto, hash,
                             subida_por, creada_en)
SELECT inmueble_id, inmobiliaria_id, 'PLANO', 1, 'Plano', imagen, imagen, ancho, alto, hash, subido_por, actualizada_en
FROM plano_inmueble;

DROP TABLE foto_inmueble;
DROP TABLE plano_inmueble;
