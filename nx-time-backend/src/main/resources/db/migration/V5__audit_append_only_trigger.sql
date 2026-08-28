-- Fase 11: la auditoría de fichajes pasa a ser append-only POR TRIGGER,
-- no solo por permisos.
--
-- Por qué hacía falta este cambio
-- ------------------------------------------------------------------
-- La Fase 8 protegió "auditoria_fichaje" revocando UPDATE y DELETE al
-- rol de la aplicación (ver V3__audit_trail.sql). Eso funciona en el
-- PostgreSQL de docker-compose, donde "nxtime_app" es un rol normal, y
-- está verificado en IncompleteTimeEntrySchedulerIT y a mano.
--
-- Pero al conectar el proyecto a Neon se comprobó que ALLÍ NO SE
-- SOSTIENE: Neon mete a TODOS los roles que creas en "neon_superuser",
-- esa pertenencia no se puede revocar ("permission denied to revoke
-- role") y con ella el rol de aplicación ejecuta UPDATE y DELETE aunque
-- solo se le hayan concedido SELECT e INSERT. Comprobado con una tabla
-- de prueba antes de escribir esto: el UPDATE decía "UPDATE 1".
--
-- Es decir, la garantía más importante del módulo de auditoría
-- --que una fila escrita no se pueda alterar-- habría sido decorativa
-- justo en producción, que es donde importa.
--
-- Un trigger no depende de los privilegios del rol: se dispara igual
-- para todo el mundo, y solo el DUEÑO de la tabla puede desactivarlo
-- (ALTER TABLE ... DISABLE TRIGGER exige ser propietario). Como Flyway
-- migra con el rol dueño y la aplicación corre con otro distinto, la
-- aplicación no puede quitárselo de encima.
--
-- Los GRANT/REVOKE de V3 se MANTIENEN: donde sí valen (local, y
-- cualquier PostgreSQL normal) siguen siendo la primera barrera. Esto
-- es una segunda, que además es portable.

CREATE OR REPLACE FUNCTION impedir_modificacion_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION
        'auditoria_fichaje es append-only: no se admite % (RD-ley 8/2019)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

-- UPDATE y DELETE, fila a fila.
CREATE TRIGGER tr_auditoria_fichaje_append_only
    BEFORE UPDATE OR DELETE ON auditoria_fichaje
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_auditoria();

-- TRUNCATE aparte: un trigger FOR EACH ROW NO se dispara con TRUNCATE
-- (no recorre filas), así que sin esto quedaría un hueco por el que
-- vaciar la tabla entera de una sola sentencia. Hoy los permisos ya lo
-- impiden en Neon, pero no conviene que la garantía dependa de eso --
-- es exactamente el tipo de suposición que esta migración corrige.
CREATE TRIGGER tr_auditoria_fichaje_no_truncate
    BEFORE TRUNCATE ON auditoria_fichaje
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_modificacion_auditoria();

COMMENT ON FUNCTION impedir_modificacion_auditoria() IS
    'Hace append-only la auditoría de fichajes con independencia de los privilegios del rol. '
    'Ver V5__audit_append_only_trigger.sql: en Neon los REVOKE de V3 no bastan.';
