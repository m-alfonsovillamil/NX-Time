-- La fecha de alta de una empresa (ADR 040).
--
-- Hasta ahora una empresa era un id, un nombre y una zona horaria: no había
-- forma de saber cuándo se registró. El panel de plataforma la enseña, y es lo
-- que deja contar las altas por semana.
--
-- Admite nulos a propósito. Las nuevas la llevan siempre (la pone la entidad
-- al crearlas); las de antes, la que se pueda deducir, y si no se puede
-- deducir ninguna se quedan sin ella: es mejor «anterior a octubre de 2026»
-- que una fecha inventada.
ALTER TABLE empresas ADD COLUMN creada_en TIMESTAMPTZ;

COMMENT ON COLUMN empresas.creada_en IS
    'Cuándo se dio de alta. En las anteriores a V41 es el rastro más antiguo que quedaba de ellas, o NULL.';

-- Lo más antiguo que se sabe de cada empresa: su primer fichaje, la primera
-- sesión de alguien suyo o el primer código de acceso que se le mandó a
-- alguien suyo. Es una estimación, y solo para las que ya existían.
UPDATE empresas e
SET creada_en = primera.cuando
FROM (
    SELECT rastros.empresa_id, MIN(rastros.cuando) AS cuando
    FROM (
        SELECT r.empresa_id, MIN(r.hora_entrada) AS cuando
        FROM registros r
        GROUP BY r.empresa_id
        UNION ALL
        SELECT u.empresa_id, MIN(t.creado_en)
        FROM refresh_tokens t
        JOIN usuarios u ON u.id = t.usuario_id
        GROUP BY u.empresa_id
        UNION ALL
        SELECT u.empresa_id, MIN(c.creado_en)
        FROM codigos_acceso c
        JOIN usuarios u ON u.id = c.usuario_id
        GROUP BY u.empresa_id
    ) rastros
    GROUP BY rastros.empresa_id
) primera
WHERE primera.empresa_id = e.id;
