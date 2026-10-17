-- El alta de empresas con la aprobación del operador, la aceptación de las
-- condiciones y el registro de lo que hace el operador (plan J, fase J4; ADR 046).
--
-- 1. Estado de cada empresa. Una empresa recién registrada queda PENDIENTE
--    hasta que el operador del servicio la aprueba; mientras tanto nadie de
--    ella puede entrar. SUSPENDIDA es una empresa que ya funcionaba y que el
--    operador ha parado (impago no hay, el servicio es gratuito: abuso, una
--    orden de una autoridad, o que lo pida la propia empresa). Las que ya
--    existían pasan a ACTIVA: ya estaban dentro.
ALTER TABLE empresas
    ADD COLUMN estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVA'
        CHECK (estado IN ('PENDIENTE', 'ACTIVA', 'SUSPENDIDA')),
    ADD COLUMN aprobada_en TIMESTAMPTZ,
    -- 2. Qué versión de las condiciones del servicio y del contrato de encargo
    --    aceptó la empresa, cuándo y quién. NULL en las de antes: se les pide
    --    al entrar su próximo ADMIN.
    ADD COLUMN condiciones_version VARCHAR(32),
    ADD COLUMN condiciones_aceptadas_en TIMESTAMPTZ,
    ADD COLUMN condiciones_aceptadas_por BIGINT REFERENCES usuarios (id) ON DELETE SET NULL;

COMMENT ON COLUMN empresas.estado IS
    'PENDIENTE hasta que la aprueba el operador; ACTIVA; SUSPENDIDA por el operador. Ver ADR 046.';
COMMENT ON COLUMN empresas.condiciones_version IS
    'La versión de las condiciones y del contrato de encargo que aceptó (application.legal.version).';
COMMENT ON COLUMN empresas.condiciones_aceptadas_por IS
    'Quién las aceptó en nombre de la empresa: el ADMIN que la registró o el que aceptó la versión nueva.';

-- Salvo las que alguien registró y nadie ha llegado a confirmar: sin esto, al
-- confirmar el correo después de esta migración entrarían sin aprobación.
UPDATE empresas e
   SET estado = 'PENDIENTE'
 WHERE EXISTS (SELECT 1 FROM usuarios u WHERE u.empresa_id = e.id)
   AND NOT EXISTS (SELECT 1 FROM usuarios u WHERE u.empresa_id = e.id AND u.correo_sin_confirmar_desde IS NULL);

CREATE INDEX ix_empresas_estado ON empresas (estado) WHERE estado <> 'ACTIVA';

-- 3. Lo que hace y lo que mira el operador del servicio (el hueco del ADR 040).
--    El panel de plataforma enseña los correos de los ADMIN de otras
--    empresas y ahora además aprueba, rechaza y suspende: que quede escrito,
--    y que no se pueda borrar.
--
--    Sin claves ajenas a propósito: una empresa rechazada se borra entera,
--    y lo que se hizo con ella tiene que quedar. Por eso van también el
--    nombre de la empresa y el correo del operador tal como eran.
CREATE TABLE acciones_de_plataforma (
    id               BIGSERIAL PRIMARY KEY,
    en               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    operador_id      BIGINT       NOT NULL,
    operador_email   VARCHAR(255) NOT NULL,
    empresa_id       BIGINT       NOT NULL,
    empresa_nombre   VARCHAR(255) NOT NULL,
    accion           VARCHAR(16)  NOT NULL
        CHECK (accion IN ('CONSULTAR', 'APROBAR', 'RECHAZAR', 'SUSPENDER', 'REACTIVAR')),
    motivo           VARCHAR(1000)
);

CREATE INDEX ix_acciones_de_plataforma_empresa ON acciones_de_plataforma (empresa_id, en DESC);

COMMENT ON TABLE acciones_de_plataforma IS
    'Lo que hace y consulta el operador desde el panel de plataforma. Solo inserción (ADR 046).';

-- Solo inserción, como la auditoría de fichajes (V5): con un trigger, porque
-- en Neon un REVOKE no basta contra un rol que tiene privilegios por defecto.
CREATE OR REPLACE FUNCTION impedir_modificacion_acciones_de_plataforma() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'acciones_de_plataforma es de solo inserción: no se admite %', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_acciones_de_plataforma_solo_insercion
    BEFORE UPDATE OR DELETE ON acciones_de_plataforma
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_acciones_de_plataforma();

CREATE TRIGGER tr_acciones_de_plataforma_no_truncate
    BEFORE TRUNCATE ON acciones_de_plataforma
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_modificacion_acciones_de_plataforma();

REVOKE UPDATE, DELETE, TRUNCATE ON acciones_de_plataforma FROM nxtime_app;
GRANT SELECT, INSERT ON acciones_de_plataforma TO nxtime_app;
GRANT USAGE, SELECT ON SEQUENCE acciones_de_plataforma_id_seq TO nxtime_app;
