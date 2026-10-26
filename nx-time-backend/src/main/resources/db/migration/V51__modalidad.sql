-- Presencial o a distancia (plan L; ADR 055).
--
-- Logroot apunta en cada fichaje si se trabaja en el centro o a distancia, y
-- la Ley 10/2021 de trabajo a distancia hace que a la empresa le interese
-- saberlo. Es opcional por empresa: la decide un ADMIN en sus ajustes, y
-- sin ella no se pregunta nada.
ALTER TABLE empresas ADD COLUMN preguntar_modalidad BOOLEAN NOT NULL DEFAULT FALSE;

-- La de cada jornada, elegida al entrar. Null: la empresa no la pregunta, o
-- se fichó con una app que aún no la conoce (no se bloquea fichar por eso).
ALTER TABLE registros ADD COLUMN modalidad VARCHAR(20);
ALTER TABLE registros ADD CONSTRAINT ck_registros_modalidad
    CHECK (modalidad IN ('PRESENCIAL', 'A_DISTANCIA'));

-- Se puede corregir después, como las horas: null es «no se toca».
ALTER TABLE solicitudes_correccion ADD COLUMN modalidad_propuesta VARCHAR(20);
ALTER TABLE solicitudes_correccion ADD CONSTRAINT ck_solicitudes_correccion_modalidad_propuesta
    CHECK (modalidad_propuesta IN ('PRESENCIAL', 'A_DISTANCIA'));
