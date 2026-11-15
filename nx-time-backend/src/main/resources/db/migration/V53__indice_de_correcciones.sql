-- El índice que le faltaba a la traza de auditoría (revisión del 15/11/2026).
--
-- Abrir la traza de un fichaje recorre su cadena de correcciones hacia delante
-- (TimeEntryServiceImpl.cadenaDeCorrecciones): «¿qué fichaje corrige a este?»,
-- una consulta por eslabón y siempre una más, la que ya no encuentra nada. Sin
-- índice en registro_original_id, cada una recorría la tabla registros entera.
-- Con el volumen de la prueba de carga (3.000 personas y un año, 780.000
-- jornadas) eran 10.000 páginas leídas y 37 ms por consulta en un portátil; en
-- la décima de CPU de Render, bastante más, y empujando fuera de la caché lo
-- que sí se usa.
--
-- Parcial: casi ninguna jornada es una corrección, así que el índice es
-- diminuto. De las 35 claves ajenas sin índice que tiene el esquema, es la
-- única que alguien consulta sobre una tabla grande: las demás o no se leen
-- por esa columna, o cuelgan de tablas pequeñas, o de filas que nunca se
-- borran.
CREATE INDEX ix_registros_registro_original ON registros (registro_original_id)
    WHERE registro_original_id IS NOT NULL;
