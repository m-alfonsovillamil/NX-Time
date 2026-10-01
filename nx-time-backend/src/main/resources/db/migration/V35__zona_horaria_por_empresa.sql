-- Plan del 30/09/2026, fase Z1: la zona horaria deja de ser la de Madrid para
-- todo el mundo y pasa a ser de cada empresa (ADR 032).
--
-- Los fichajes se guardan como instantes (TIMESTAMPTZ, ADR 002). Lo que
-- depende de la zona es a qué DÍA pertenece cada instante: el día de una
-- jornada, las horas extra diarias, el mes de un informe o de una firma. Hasta
-- ahora todo eso se calculaba con 'Europe/Madrid' escrito a mano, y una
-- empresa de Canarias habría visto los días desplazados una hora.
--
-- Nombre IANA ('Europe/Madrid', 'Atlantic/Canary'...). El servidor lo valida
-- con ZoneId antes de guardarlo; aquí no se repite la lista de zonas, que
-- cambia con cada versión de la base de datos de zonas.
--
-- Por defecto Madrid: las empresas que ya existen quedan exactamente igual.

ALTER TABLE empresas
    ADD COLUMN zona_horaria VARCHAR(64) NOT NULL DEFAULT 'Europe/Madrid';
