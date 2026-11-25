-- Predia · V11: el cierre de un negocio (una venta firmada) y su registro.
-- Hasta aquí el estado FIRMADO existía pero nada lo registraba. Ahora cerrar un negocio guarda cuándo se firmó, quién lo
-- confirmó y por qué valor; con esos datos (y no con un número escrito a mano) se calcula cuántos inmuebles vendió cada asesor.

ALTER TABLE negocio
    ADD COLUMN firmado_en   TIMESTAMPTZ,
    ADD COLUMN firmado_por  BIGINT REFERENCES usuario (id),
    ADD COLUMN valor_cierre NUMERIC(16, 2),
    ADD COLUMN nota_cierre  VARCHAR(500),
    ADD COLUMN cancelado_en TIMESTAMPTZ;

-- Si ya había negocios en estado FIRMADO (marcados por la API), se completan con lo que se sabe de ellos.
UPDATE negocio n
   SET firmado_en   = n.actualizado_en,
       firmado_por  = n.asesor_id,
       valor_cierre = COALESCE((SELECT i.precio_venta FROM inmueble i WHERE i.id = n.inmueble_id), 0)
 WHERE n.estado = 'FIRMADO';
UPDATE negocio SET cancelado_en = actualizado_en WHERE estado = 'CANCELADO';

-- Un negocio está FIRMADO si y solo si tiene su registro de cierre completo.
ALTER TABLE negocio
    ADD CONSTRAINT ck_negocio_cierre CHECK (
        (estado = 'FIRMADO') = (firmado_en IS NOT NULL AND firmado_por IS NOT NULL AND valor_cierre IS NOT NULL));

CREATE INDEX ix_negocio_asesor_estado ON negocio (inmobiliaria_id, asesor_id, estado);
