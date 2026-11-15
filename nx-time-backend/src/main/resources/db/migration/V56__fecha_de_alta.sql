-- La fecha de alta de cada persona (plan N, paso N10).
--
-- Un dato de la ficha que RRHH necesita (antigüedad, nóminas, el primer año de
-- vacaciones) y que el modelo no tenía: la analítica usaba la primera jornada
-- fichada en su lugar (ADR 026), y lo sigue haciendo, porque es lo correcto
-- para ella: quien entró en 2015 no estuvo ausente hasta que su empresa empezó
-- a usar NX Time.
--
-- Las personas nuevas la reciben al crearse (hoy, en la zona de su empresa, si
-- nadie da otra). Las que ya existían se quedan con lo más antiguo que dejaron,
-- su primera jornada, como hizo V41 con las empresas; quien no fichó nunca,
-- sin ella, que es mejor que inventarla.
ALTER TABLE usuarios ADD COLUMN fecha_alta DATE;

UPDATE usuarios u
SET fecha_alta = primera.dia
FROM (
    SELECT r.usuario_id, MIN((r.hora_entrada AT TIME ZONE e.zona_horaria)::date) AS dia
    FROM registros r
    JOIN empresas e ON e.id = r.empresa_id
    GROUP BY r.usuario_id
) primera
WHERE primera.usuario_id = u.id;
