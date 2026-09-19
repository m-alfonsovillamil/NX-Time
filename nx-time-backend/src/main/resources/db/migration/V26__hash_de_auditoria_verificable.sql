-- Distingue las filas de auditoría cuyo hash SE PUEDE recalcular.
--
-- Hasta ahora ninguna: el hash se firmaba sobre datos que la base no
-- conserva igual (los nanosegundos de la marca de tiempo, que TIMESTAMPTZ
-- trunca a microsegundos, y el texto del JSON, que jsonb reescribe al
-- guardarlo). Comprobado ejecutando: de 18 filas de una base real, cero
-- recalculables. Ver HuellaDeAuditoria.
--
-- Desde la versión 2 se firma lo que de verdad se guarda, así que un
-- verificador puede recalcular y comparar. Las filas de antes se quedan en
-- la 1 y solo admiten la comprobación del enlace con la anterior; el dato
-- que haría falta para recalcularlas se perdió y no hay forma de
-- recuperarlo, así que se marcan en vez de darlas por buenas.
--
-- ADD COLUMN con DEFAULT no reescribe la tabla (PostgreSQL 11+) y no
-- dispara el trigger append-only de V5, que vigila UPDATE y DELETE de
-- filas, no cambios de esquema.
ALTER TABLE auditoria_fichaje
    ADD COLUMN version_hash SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN auditoria_fichaje.version_hash IS
    'Cómo se calculó el hash: 1 = no recalculable (ver V26), 2 = sobre la forma canónica, verificable.';
