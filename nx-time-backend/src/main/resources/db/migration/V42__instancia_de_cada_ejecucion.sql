-- Qué instancia del servicio hizo cada ejecución de una tarea programada (ADR 042).
--
-- Las tareas nocturnas son un @Scheduled dentro del proceso. Con una sola
-- instancia, cada una corre una vez; con dos (un plan de pago con réplicas, o
-- el rato en que conviven la vieja y la nueva durante un despliegue), correrían
-- dos veces, y varias mandan correos.
--
-- Con esta columna, antes de empezar una tarea se mira si OTRA instancia ya la
-- ha hecho en este turno, y entonces no se repite. No hace falta nada más que
-- la base, que es lo único que comparten.
--
-- Admite nulos: las ejecuciones anteriores no lo tienen, y no cuentan como de
-- nadie.
ALTER TABLE ejecuciones_tarea ADD COLUMN instancia VARCHAR(36);

COMMENT ON COLUMN ejecuciones_tarea.instancia IS
    'Identificador al azar de la instancia que la ejecutó (uno por arranque). NULL en las anteriores a V42.';
