-- La ubicación al fichar (plan L; ADR 056).
--
-- Logroot guarda dónde se fichó, en ese momento y nada más. NX Time igual:
-- solo al entrar y al salir, nunca un seguimiento. Es un dato personal nuevo
-- (art. 90 LOPDGDD), así que:
--   - es opcional por empresa y viene apagado; lo enciende un ADMIN;
--   - si no hay ubicación (permiso denegado, sin GPS) se ficha igual, y queda
--     dicho por qué no la hay;
--   - se guarda redondeada a 4 decimales (unos 11 m);
--   - a los 12 meses se borran las coordenadas (ConservacionDeDatos) y queda
--     solo el estado;
--   - NO entra en la auditoría ni en la huella del mes: lo que está en la
--     auditoría no se puede borrar nunca.
ALTER TABLE empresas ADD COLUMN ubicacion_al_fichar BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE registros
    ADD COLUMN entrada_ubicacion   VARCHAR(20),
    ADD COLUMN entrada_latitud     DOUBLE PRECISION,
    ADD COLUMN entrada_longitud    DOUBLE PRECISION,
    ADD COLUMN entrada_precision_m INTEGER,
    ADD COLUMN salida_ubicacion    VARCHAR(20),
    ADD COLUMN salida_latitud      DOUBLE PRECISION,
    ADD COLUMN salida_longitud     DOUBLE PRECISION,
    ADD COLUMN salida_precision_m  INTEGER;

ALTER TABLE registros ADD CONSTRAINT ck_registros_entrada_ubicacion
    CHECK (entrada_ubicacion IN ('CAPTURADA', 'DENEGADA', 'NO_DISPONIBLE'));
ALTER TABLE registros ADD CONSTRAINT ck_registros_salida_ubicacion
    CHECK (salida_ubicacion IN ('CAPTURADA', 'DENEGADA', 'NO_DISPONIBLE'));
ALTER TABLE registros ADD CONSTRAINT ck_registros_entrada_coordenadas
    CHECK (entrada_latitud IS NULL OR (entrada_latitud BETWEEN -90 AND 90 AND entrada_longitud BETWEEN -180 AND 180));
ALTER TABLE registros ADD CONSTRAINT ck_registros_salida_coordenadas
    CHECK (salida_latitud IS NULL OR (salida_latitud BETWEEN -90 AND 90 AND salida_longitud BETWEEN -180 AND 180));

-- Para el borrado de los 12 meses: solo las que aún tienen coordenadas.
CREATE INDEX ix_registros_con_coordenadas ON registros (hora_entrada)
    WHERE entrada_latitud IS NOT NULL OR salida_latitud IS NOT NULL;
