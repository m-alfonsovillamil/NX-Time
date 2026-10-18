-- El RGPD dentro del producto (plan J, fase J5; ADR 047).
--
-- 1. A quién se dirige un empleado para ejercer sus derechos: un correo o un
--    enlace a la política de privacidad de SU empresa, que es la responsable
--    del tratamiento. Lo pone el ADMIN; sin él, el aviso dice «tu empresa».
ALTER TABLE empresas
    ADD COLUMN contacto_privacidad VARCHAR(255);

COMMENT ON COLUMN empresas.contacto_privacidad IS
    'Correo o enlace (https) al que dirigirse para ejercer los derechos del RGPD. Lo pone el ADMIN.';

-- 2. Cuándo vio cada persona el aviso de privacidad de la primera entrada.
--    NULL en todas las de antes: lo verán una vez, como quien entra nuevo.
ALTER TABLE usuarios
    ADD COLUMN aviso_privacidad_visto_en TIMESTAMPTZ;

-- 3. Quién ha consultado datos personales de otra persona: el CV de una
--    candidatura, un perfil ajeno, el registro mensual de otro, el Excel de
--    la empresa, un expediente de denuncia o una exportación. Lo propio no se
--    anota: no es un acceso a datos de nadie más.
--
--    Sin claves ajenas, como acciones_de_plataforma (V43): la fila tiene que
--    sobrevivir a la anonimización y al borrado de una cuenta. Solo números:
--    los nombres se buscan al leer, y una persona anonimizada sale como tal.
CREATE TABLE accesos_a_datos (
    id          BIGSERIAL PRIMARY KEY,
    en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    empresa_id  BIGINT      NOT NULL,
    quien_id    BIGINT      NOT NULL,
    que         VARCHAR(24) NOT NULL
        CHECK (que IN ('CV', 'PERFIL', 'INFORME_MENSUAL', 'INFORME_EMPRESA', 'DENUNCIA', 'EXPORTACION')),
    -- De quién son los datos. NULL cuando son de muchos (el Excel de la
    -- empresa) o cuando decirlo sería romper la confidencialidad (una
    -- denuncia: quién la puso no se anota).
    sobre_id    BIGINT,
    detalle     VARCHAR(160)
);

CREATE INDEX ix_accesos_a_datos_empresa ON accesos_a_datos (empresa_id, en DESC);
CREATE INDEX ix_accesos_a_datos_en ON accesos_a_datos (en);

COMMENT ON TABLE accesos_a_datos IS
    'Consultas de datos personales ajenos. Solo inserción; se borra a los 2 años o con la empresa (ADR 047).';

-- Solo inserción, con dos salidas: lo que tiene más de dos años (el plazo de
-- conservación, docs/cumplimiento/conservacion.md) y lo de una empresa que ya
-- no existe. Nada más se puede tocar, tampoco desde la aplicación.
CREATE OR REPLACE FUNCTION proteger_accesos_a_datos() RETURNS trigger AS $$
BEGIN
    -- Anidado y no con AND: en el trigger del TRUNCATE no hay fila (OLD).
    IF TG_OP = 'DELETE' THEN
        IF OLD.en < now() - INTERVAL '2 years'
           OR NOT EXISTS (SELECT 1 FROM empresas e WHERE e.id = OLD.empresa_id) THEN
            RETURN OLD;
        END IF;
    END IF;
    RAISE EXCEPTION 'accesos_a_datos es de solo inserción: no se admite %', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_accesos_a_datos_solo_insercion
    BEFORE UPDATE OR DELETE ON accesos_a_datos
    FOR EACH ROW EXECUTE FUNCTION proteger_accesos_a_datos();

CREATE TRIGGER tr_accesos_a_datos_no_truncate
    BEFORE TRUNCATE ON accesos_a_datos
    FOR EACH STATEMENT EXECUTE FUNCTION proteger_accesos_a_datos();

REVOKE UPDATE, TRUNCATE ON accesos_a_datos FROM nxtime_app;
GRANT SELECT, INSERT, DELETE ON accesos_a_datos TO nxtime_app;
GRANT USAGE, SELECT ON SEQUENCE accesos_a_datos_id_seq TO nxtime_app;
