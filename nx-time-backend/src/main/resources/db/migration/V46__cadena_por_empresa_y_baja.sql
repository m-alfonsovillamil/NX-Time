-- Una cadena de auditoría por empresa, y la baja de una empresa con borrado
-- (plan J; ADR 050).
--
-- Hasta aquí la cadena de huellas era UNA para todas las empresas: cada fila
-- enlazaba con la anterior de la tabla, fuera de quien fuera (ADR 018). Así no
-- se podía borrar a una empresa que se va, como dicen las condiciones (punto
-- 9): quitar sus filas rompía la verificación de todas las demás.
--
-- Lo que se hace:
--   1. Las filas nuevas (version_hash 3) encadenan solo con las de SU empresa.
--   2. Las de antes se quedan como están, como «tramo común». No se reescribe
--      ni un hash: reescribir una cadena de integridad es exactamente lo que
--      haría quien quisiera manipularla, y lo ya comprobado dejaría de valer.
--   3. Al borrar una empresa, sus filas del tramo común dejan una lápida
--      (eslabones_retirados) con su id y sus dos hashes, sin nada de su
--      contenido: el tramo común se sigue pudiendo recorrer, enlace a enlace.
--   4. El borrado lo hace una función SECURITY DEFINER, y el trigger de la
--      auditoría solo lo deja pasar desde dentro de ella.

-- ---------------------------------------------------------------------
-- 1. La empresa de cada fila nueva
-- ---------------------------------------------------------------------
-- Solo en las nuevas: rellenarla en las viejas sería un UPDATE sobre una tabla
-- de solo inserción, y a las viejas se las encuentra por registros.empresa_id.
-- Sin clave ajena, como acciones_de_plataforma (V43): la fila tiene que poder
-- borrarse cuando ya no esté la empresa.
ALTER TABLE auditoria_fichaje ADD COLUMN empresa_id BIGINT;
ALTER TABLE auditoria_fichaje ADD CONSTRAINT ck_auditoria_empresa_en_v3
    CHECK (version_hash < 3 OR empresa_id IS NOT NULL);

-- La última fila de la cadena de una empresa, para encadenar la siguiente y
-- para recorrerla.
CREATE INDEX ix_auditoria_cadena_de_empresa ON auditoria_fichaje (empresa_id, id) WHERE version_hash >= 3;

COMMENT ON COLUMN auditoria_fichaje.empresa_id IS
    'La cadena a la que pertenece la fila (version_hash 3). Null en las de antes de V46, que forman el tramo común.';

-- ---------------------------------------------------------------------
-- 2. Los puntos de control, por cadena
-- ---------------------------------------------------------------------
-- Null: el tramo común. Una empresa: su cadena.
ALTER TABLE puntos_control_auditoria ADD COLUMN empresa_id BIGINT;
CREATE INDEX ix_puntos_control_por_cadena ON puntos_control_auditoria (empresa_id, hasta_id DESC);

-- ---------------------------------------------------------------------
-- 3. Las lápidas del tramo común
-- ---------------------------------------------------------------------
CREATE TABLE eslabones_retirados (
    -- El id que tenía la fila en auditoria_fichaje: es su sitio en el tramo común.
    id            BIGINT      PRIMARY KEY,
    hash_anterior VARCHAR(64),
    hash          VARCHAR(64) NOT NULL,
    empresa_id    BIGINT      NOT NULL,
    retirado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE eslabones_retirados IS
    'Filas del tramo común de una empresa ya borrada: solo su sitio y sus hashes, para seguir pudiendo recorrer el tramo. ADR 050.';

-- Y constancia de cada empresa que se borró y por qué: el nombre y las fechas,
-- nada de su gente.
CREATE TABLE empresas_borradas (
    empresa_id          BIGINT       PRIMARY KEY,
    nombre              VARCHAR(255) NOT NULL,
    baja_solicitada_en  TIMESTAMPTZ  NOT NULL,
    borrada_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    filas_retiradas     BIGINT       NOT NULL,
    movimientos_borrados BIGINT      NOT NULL
);

CREATE OR REPLACE FUNCTION impedir_modificacion_constancias() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% es de solo inserción: no se admite %', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_eslabones_retirados_solo_insercion BEFORE UPDATE OR DELETE ON eslabones_retirados
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_constancias();
CREATE TRIGGER tr_eslabones_retirados_no_truncate BEFORE TRUNCATE ON eslabones_retirados
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_modificacion_constancias();
CREATE TRIGGER tr_empresas_borradas_solo_insercion BEFORE UPDATE OR DELETE ON empresas_borradas
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_constancias();
CREATE TRIGGER tr_empresas_borradas_no_truncate BEFORE TRUNCATE ON empresas_borradas
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_modificacion_constancias();

-- La aplicación las lee; escribir, solo la función de borrado.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON eslabones_retirados, empresas_borradas FROM nxtime_app;
GRANT SELECT ON eslabones_retirados, empresas_borradas TO nxtime_app;

-- ---------------------------------------------------------------------
-- 4. La baja de la empresa
-- ---------------------------------------------------------------------
ALTER TABLE empresas
    ADD COLUMN baja_solicitada_en TIMESTAMPTZ,
    ADD COLUMN baja_solicitada_por BIGINT REFERENCES usuarios (id) ON DELETE SET NULL;

COMMENT ON COLUMN empresas.baja_solicitada_en IS
    'Cuándo pidió el ADMIN la baja. A los 30 días la tarea nocturna borra la empresa entera (ADR 050).';

-- ---------------------------------------------------------------------
-- 5. El trigger de la auditoría, con una sola puerta
-- ---------------------------------------------------------------------
-- Borrar solo se admite cuando lo hace la función de abajo: dentro de ella
-- current_user es su dueño y no el rol de la aplicación (SECURITY DEFINER), y
-- ha dejado dicho qué empresa está borrando. Un DELETE de la aplicación por su
-- cuenta sigue chocando aquí, aunque ponga la variable a mano: current_user
-- seguiría siendo ella.
CREATE OR REPLACE FUNCTION impedir_modificacion_auditoria() RETURNS trigger AS $$
DECLARE
    empresa_de_la_fila BIGINT;
BEGIN
    IF TG_OP = 'DELETE' AND current_user <> session_user
       AND current_setting('nxtime.borrando_empresa', true) IS NOT NULL THEN
        empresa_de_la_fila := COALESCE(OLD.empresa_id,
            (SELECT r.empresa_id FROM registros r WHERE r.id = OLD.registro_id));
        IF empresa_de_la_fila::text = current_setting('nxtime.borrando_empresa', true) THEN
            RETURN OLD;
        END IF;
    END IF;
    RAISE EXCEPTION
        'auditoria_fichaje es append-only: no se admite % (RD-ley 8/2019)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 6. Borrar una empresa entera
-- ---------------------------------------------------------------------
-- En la base y en un solo sitio, en orden de dependencias. Si mañana aparece
-- una tabla nueva con datos de la empresa y no se añade aquí, el borrado falla
-- por su clave ajena (y BorradoDeEmpresaIT comprueba que no queda nada en
-- ninguna tabla con empresa_id o usuario_id).
CREATE OR REPLACE FUNCTION borrar_empresa(p_empresa BIGINT) RETURNS BIGINT
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_nombre      VARCHAR(255);
    v_baja        TIMESTAMPTZ;
    v_retiradas   BIGINT;
    v_movimientos BIGINT;
BEGIN
    SELECT nombre, baja_solicitada_en INTO v_nombre, v_baja FROM empresas WHERE id = p_empresa FOR UPDATE;
    IF v_nombre IS NULL THEN
        RAISE EXCEPTION 'No existe la empresa %', p_empresa;
    END IF;
    -- La única condición, y en la base: la baja pedida hace más de 30 días.
    IF v_baja IS NULL OR v_baja > now() - INTERVAL '30 days' THEN
        RAISE EXCEPTION 'La empresa % no tiene una baja pedida hace más de 30 días', p_empresa
            USING ERRCODE = 'insufficient_privilege';
    END IF;

    PERFORM set_config('nxtime.borrando_empresa', p_empresa::text, true);

    -- Las lápidas del tramo común, antes de borrar las filas.
    INSERT INTO eslabones_retirados (id, hash_anterior, hash, empresa_id)
    SELECT a.id, a.hash_anterior, a.hash, p_empresa
      FROM auditoria_fichaje a JOIN registros r ON r.id = a.registro_id
     WHERE r.empresa_id = p_empresa AND a.version_hash < 3;
    GET DIAGNOSTICS v_retiradas = ROW_COUNT;

    DELETE FROM auditoria_fichaje
     WHERE empresa_id = p_empresa
        OR registro_id IN (SELECT id FROM registros WHERE empresa_id = p_empresa);
    GET DIAGNOSTICS v_movimientos = ROW_COUNT;
    DELETE FROM puntos_control_auditoria WHERE empresa_id = p_empresa;
    -- Y los del tramo común: llevan sumadas las filas que acaban de pasar a
    -- lápida como «comprobadas». Esa noche se vuelve a recorrer el tramo entero
    -- (ya no crece) y se anota uno con las cuentas de verdad.
    DELETE FROM puntos_control_auditoria WHERE empresa_id IS NULL;

    -- Lo que cuelga de los fichajes.
    DELETE FROM tramos_proyecto WHERE empresa_id = p_empresa;
    DELETE FROM imputaciones_proyecto WHERE empresa_id = p_empresa;
    DELETE FROM pausas_anadidas WHERE empresa_id = p_empresa;
    DELETE FROM avisos_horas_extra WHERE empresa_id = p_empresa;
    DELETE FROM incidencias_cuadrante WHERE empresa_id = p_empresa;
    DELETE FROM solicitudes_correccion WHERE empresa_id = p_empresa;
    UPDATE registros SET registro_original_id = NULL WHERE empresa_id = p_empresa;
    DELETE FROM registros WHERE empresa_id = p_empresa;

    DELETE FROM kiosco_emparejamientos WHERE kiosco_id IN (SELECT id FROM kioscos WHERE empresa_id = p_empresa);
    DELETE FROM kioscos WHERE empresa_id = p_empresa;

    -- Lo demás de la empresa.
    DELETE FROM firmas_mensuales WHERE empresa_id = p_empresa;
    DELETE FROM solicitudes_borrado WHERE empresa_id = p_empresa;
    DELETE FROM denuncias WHERE empresa_id = p_empresa;
    DELETE FROM candidaturas WHERE oferta_id IN (SELECT id FROM ofertas_internas WHERE empresa_id = p_empresa)
                                OR usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = p_empresa);
    DELETE FROM ofertas_internas WHERE empresa_id = p_empresa;
    DELETE FROM adjuntos WHERE empresa_id = p_empresa;
    DELETE FROM asignaciones_horario WHERE empresa_id = p_empresa;
    DELETE FROM excepciones_horario WHERE empresa_id = p_empresa;
    DELETE FROM plantillas_horario WHERE empresa_id = p_empresa;
    DELETE FROM asignaciones_proyecto WHERE empresa_id = p_empresa;
    DELETE FROM proyectos WHERE empresa_id = p_empresa;
    DELETE FROM peticiones_ausencia WHERE empresa_id = p_empresa;
    DELETE FROM avisos WHERE empresa_id = p_empresa;
    DELETE FROM festivos WHERE empresa_id = p_empresa;

    -- La gente, y lo que cuelga de cada persona.
    DELETE FROM saldo_vacaciones WHERE usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = p_empresa);
    DELETE FROM codigos_acceso WHERE usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = p_empresa);
    DELETE FROM identidades_externas WHERE usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = p_empresa);
    DELETE FROM dispositivos_push WHERE usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = p_empresa);
    DELETE FROM refresh_tokens WHERE usuario_id IN (SELECT id FROM usuarios WHERE empresa_id = p_empresa);
    UPDATE empresas SET responsable_canal_id = NULL, condiciones_aceptadas_por = NULL, baja_solicitada_por = NULL
     WHERE id = p_empresa;
    DELETE FROM usuarios WHERE empresa_id = p_empresa;
    DELETE FROM departamentos WHERE empresa_id = p_empresa;

    INSERT INTO empresas_borradas (empresa_id, nombre, baja_solicitada_en, filas_retiradas, movimientos_borrados)
    VALUES (p_empresa, v_nombre, v_baja, v_retiradas, v_movimientos);
    DELETE FROM empresas WHERE id = p_empresa;

    -- Ya sin empresa, el trigger de V44 deja borrar su registro de accesos.
    DELETE FROM accesos_a_datos WHERE empresa_id = p_empresa;

    RETURN v_movimientos;
END;
$$;

REVOKE ALL ON FUNCTION borrar_empresa(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION borrar_empresa(BIGINT) TO nxtime_app;

COMMENT ON FUNCTION borrar_empresa(BIGINT) IS
    'Borra una empresa entera, auditoría incluida, si pidió la baja hace más de 30 días. Única puerta del trigger de V5. ADR 050.';
