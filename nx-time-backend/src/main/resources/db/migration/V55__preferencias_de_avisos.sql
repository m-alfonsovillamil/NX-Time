-- Cómo le llegan los avisos a cada persona (ADR 059; plan N, paso N9).
--
-- Solo se guarda lo que alguien cambia: una fila por persona y categoría que
-- haya tocado. Sin fila, todo llega (correo y push), como hasta ahora. La
-- campana de la aplicación no se puede apagar y por eso no está aquí, ni la
-- categoría OBLIGATORIOS, que la aplicación no deja guardar.
CREATE TABLE preferencias_avisos (
    usuario_id BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    categoria  VARCHAR(30) NOT NULL,
    correo     BOOLEAN     NOT NULL,
    push       BOOLEAN     NOT NULL,
    CONSTRAINT pk_preferencias_avisos PRIMARY KEY (usuario_id, categoria),
    CONSTRAINT ck_preferencias_avisos_categoria CHECK (categoria IN (
        'AUSENCIAS', 'CORRECCIONES', 'HORAS_EXTRA', 'INCIDENCIAS', 'OFERTAS', 'EQUIPO'))
);
-- Los permisos de nxtime_app los da el ALTER DEFAULT PRIVILEGES de V3.
