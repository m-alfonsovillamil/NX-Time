-- Justificante en una ausencia (ADR 062; plan N, paso N13).
--
-- Un parte de baja o un justificante médico es un dato de salud (art. 9 RGPD):
-- apagado por defecto en cada empresa, solo lo ven la persona y quien tenga
-- `ausencia:justificante:leer` (RRHH y ADMIN), y cada descarga de otra persona
-- queda anotada en accesos_a_datos.
--
-- Tabla propia y no `adjuntos`: aquellos son del perfil (un CV y una foto
-- vigentes por persona) y este es de una ausencia, uno por ausencia. Los bytes
-- van en la misma fila porque aquí no hay entidad JPA que los cargue sin
-- querer: se lee con consultas que solo piden `contenido` al descargar.
CREATE TABLE justificantes_ausencia (
    peticion_id     BIGINT       PRIMARY KEY,
    nombre_original VARCHAR(255) NOT NULL,
    mime            VARCHAR(100) NOT NULL,
    tamano_bytes    BIGINT       NOT NULL CHECK (tamano_bytes > 0),
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    contenido       BYTEA        NOT NULL,

    -- Borrar la ausencia (la baja de una empresa) se lleva su justificante.
    CONSTRAINT fk_justificantes_peticion
        FOREIGN KEY (peticion_id) REFERENCES peticiones_ausencia(id) ON DELETE CASCADE
);

ALTER TABLE empresas ADD COLUMN justificantes_en_ausencias BOOLEAN NOT NULL DEFAULT FALSE;

-- Bajar un justificante de otra persona se anota, como un CV o una denuncia.
ALTER TABLE accesos_a_datos DROP CONSTRAINT accesos_a_datos_que_check;
ALTER TABLE accesos_a_datos ADD CONSTRAINT accesos_a_datos_que_check
    CHECK (que IN ('CV', 'PERFIL', 'INFORME_MENSUAL', 'INFORME_EMPRESA', 'DENUNCIA', 'EXPORTACION', 'JUSTIFICANTE'));

GRANT SELECT, INSERT, UPDATE, DELETE ON justificantes_ausencia TO nxtime_app;
