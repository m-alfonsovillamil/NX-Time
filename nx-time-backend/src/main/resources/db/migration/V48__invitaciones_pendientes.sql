-- Las invitaciones que faltan por mandar (plan K; ADR 052).
--
-- Dar de alta a alguien manda un correo con el código para elegir contraseña.
-- Al importar una plantilla entera, no todos pueden salir a la vez:
--   - como mucho 50 altas al día por empresa (ADR 034, contra quien use una
--     cuenta de ADMIN para mandar correo a granel);
--   - y el proveedor de correo tiene su propio tope diario para toda la
--     instalación (300 en el plan gratuito de Brevo).
-- Lo que no cabe hoy se queda aquí, con la cuenta ya creada, y se manda en
-- cuanto haya cupo. Al mandarla, la fila se borra.
CREATE TABLE invitaciones_pendientes (
    usuario_id      BIGINT       PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    empresa_id      BIGINT       NOT NULL REFERENCES empresas (id) ON DELETE CASCADE,
    creada_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    intentos        INTEGER      NOT NULL DEFAULT 0,
    ultimo_intento  TIMESTAMPTZ,
    ultimo_error    VARCHAR(200)
);

CREATE INDEX ix_invitaciones_pendientes_empresa ON invitaciones_pendientes (empresa_id, creada_en);
