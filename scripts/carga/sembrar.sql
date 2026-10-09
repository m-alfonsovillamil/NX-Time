-- Volumen para la prueba de carga (docs/PRUEBA-DE-CARGA.md).
--
-- Añade, a una base que YA tiene los datos de demo, muchas empresas con su
-- plantilla, un año de fichajes y su traza de auditoría. Solo para bases
-- desechables: no hay forma de quitarlo después (la traza es de solo
-- inserción).
--
--   psql -v empresas=100 -v personas=30 -v dias=365 -f scripts/carga/sembrar.sql
--
-- Hay que lanzarlo con el rol dueño de la base (en local, "nxtime"), con el
-- backend PARADO: la traza se encadena aquí a mano y un fichaje a la vez
-- rompería la cadena.
--
-- Qué deja:
--   - :empresas empresas «Carga 0001»…, con tres departamentos cada una.
--   - :personas personas por empresa: p1 es ADMIN, p2 RRHH, p3 y p4 GESTOR y
--     el resto EMPLEADO. Correo p<k>@carga<n>.test; la contraseña es la de la
--     demo (se copia el hash de una cuenta de demo: calcular BCrypt en SQL no
--     se puede).
--   - Una jornada cerrada por persona y día laborable de los últimos :dias
--     días, SIN hoy: así a «las 9:00» nadie tiene la jornada abierta.
--   - Dos movimientos de auditoría por jornada (la entrada y la salida),
--     encadenados. Van con version_hash = 1, como los anteriores a V26: de
--     esos se comprueba el enlace con el anterior y no el contenido, que es
--     lo único que se puede fabricar sin la aplicación.
--
-- Lo que NO siembra, y la prueba no mide: cuadrantes, proyectos, ausencias.

\set ON_ERROR_STOP on
\timing on

BEGIN;

-- Un hash de contraseña válido, de la demo.
SELECT contrasena AS hash_demo FROM usuarios WHERE email = 'javier.lopez@techcorp.demo' \gset

INSERT INTO empresas (nombre)
SELECT 'Carga ' || lpad(n::text, 4, '0')
FROM generate_series(1, :empresas) n;

INSERT INTO departamentos (empresa_id, nombre)
SELECT e.id, d.nombre
FROM empresas e
CROSS JOIN (VALUES ('Operaciones'), ('Oficina'), ('Almacén')) d(nombre)
WHERE e.nombre LIKE 'Carga %';

INSERT INTO usuarios (email, nombre, apellidos, contrasena, rol, empresa_id, departamento_id)
SELECT 'p' || k || '@carga' || substr(e.nombre, 7)::int || '.test',
       'Persona ' || k,
       'De ' || e.nombre,
       :'hash_demo',
       CASE WHEN k = 1 THEN 'ADMIN' WHEN k = 2 THEN 'RRHH' WHEN k <= 4 THEN 'GESTOR' ELSE 'EMPLEADO' END,
       e.id,
       (SELECT d.id FROM departamentos d WHERE d.empresa_id = e.id ORDER BY d.id OFFSET (k % 3) LIMIT 1)
FROM empresas e
CROSS JOIN generate_series(1, :personas) k
WHERE e.nombre LIKE 'Carga %';

-- Una jornada por persona y día laborable (de lunes a viernes), sin hoy.
-- Entra entre las 7:00 y las 7:30 UTC y sale ocho horas y pico después, con
-- media hora de pausa: segundos sueltos a propósito, como las de verdad.
INSERT INTO registros (usuario_id, empresa_id, hora_entrada, hora_salida, segundos_pausa_acumulados)
SELECT u.id,
       u.empresa_id,
       entrada,
       entrada + INTERVAL '8 hours' + (random() * INTERVAL '40 minutes'),
       1800
FROM usuarios u
JOIN empresas e ON e.id = u.empresa_id AND e.nombre LIKE 'Carga %'
CROSS JOIN LATERAL (
    SELECT (dia + TIME '07:00' + (random() * INTERVAL '30 minutes')) AT TIME ZONE 'UTC' AS entrada
    FROM generate_series(CURRENT_DATE - :dias, CURRENT_DATE - 1, INTERVAL '1 day') dia
    WHERE EXTRACT(ISODOW FROM dia) < 6
) dias;

-- La traza: dos movimientos por jornada, en el orden en que habrían pasado, y
-- cada uno con el hash del anterior. El primero enlaza con lo último que
-- hubiera en la tabla (o con nada, si está vacía).
SELECT COALESCE(MAX(id), 0) AS base FROM auditoria_fichaje \gset
-- Con la tabla vacía no hay fila, y \gset sin fila es un error: de ahí la cadena vacía.
SELECT COALESCE((SELECT hash FROM auditoria_fichaje ORDER BY id DESC LIMIT 1), '') AS ultimo_hash \gset

INSERT INTO auditoria_fichaje
    (id, registro_id, usuario_id, modificado_por_id, accion, valor_anterior, valor_nuevo,
     fecha_hora, ip, hash_anterior, hash, version_hash)
SELECT :base + m.n,
       m.registro_id,
       m.usuario_id,
       m.usuario_id,
       m.accion,
       CASE WHEN m.accion = 'MODIFICACION'
            THEN jsonb_build_object('id', m.registro_id, 'horaEntrada', m.hora_entrada, 'horaSalida', NULL) END,
       jsonb_build_object('id', m.registro_id, 'horaEntrada', m.hora_entrada,
                          'horaSalida', CASE WHEN m.accion = 'MODIFICACION' THEN m.cuando END),
       m.cuando,
       '203.0.113.7',
       CASE WHEN m.n = 1 THEN NULLIF(:'ultimo_hash', '') ELSE encode(sha256(('carga:' || (m.n - 1))::bytea), 'hex') END,
       encode(sha256(('carga:' || m.n)::bytea), 'hex'),
       1
FROM (
    SELECT row_number() OVER (ORDER BY mov.cuando, mov.registro_id, mov.accion) AS n, mov.*
    FROM (
        SELECT r.id AS registro_id, r.usuario_id, r.hora_entrada, 'CREACION' AS accion, r.hora_entrada AS cuando
        FROM registros r
        JOIN empresas e ON e.id = r.empresa_id AND e.nombre LIKE 'Carga %'
        UNION ALL
        SELECT r.id, r.usuario_id, r.hora_entrada, 'MODIFICACION', r.hora_salida
        FROM registros r
        JOIN empresas e ON e.id = r.empresa_id AND e.nombre LIKE 'Carga %'
    ) mov
) m;

-- Que el siguiente movimiento, el que escriba la aplicación, siga la numeración.
SELECT setval(pg_get_serial_sequence('auditoria_fichaje', 'id'), (SELECT MAX(id) FROM auditoria_fichaje));

COMMIT;

ANALYZE;

SELECT (SELECT COUNT(*) FROM empresas) AS empresas,
       (SELECT COUNT(*) FROM usuarios) AS personas,
       (SELECT COUNT(*) FROM registros) AS jornadas,
       (SELECT COUNT(*) FROM auditoria_fichaje) AS movimientos,
       pg_size_pretty(pg_database_size(current_database())) AS ocupa;
