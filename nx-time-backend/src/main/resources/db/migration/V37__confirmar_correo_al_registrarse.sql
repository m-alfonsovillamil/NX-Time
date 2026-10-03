-- Quien registra una empresa confirma su correo antes de entrar (revisión de
-- seguridad del 1/10/2026, ADR 034).
--
-- El registro de empresas es público. Hasta aquí, cualquiera podía registrar
-- una con el correo que quisiera, entrar al momento como su ADMIN y dar de
-- alta a gente: cada alta manda un correo a la dirección que se teclee. Era
-- un relé de correo abierto con el nombre de NX Time en el remitente.
--
-- Ahora el registro deja la cuenta pendiente y manda un código a ese correo;
-- hasta canjearlo no se puede entrar. Se marca lo pendiente y no lo
-- confirmado: así ninguna cuenta que ya existe cambia (todas tienen la
-- columna a NULL), y las que da de alta un ADMIN tampoco, porque esas ya
-- demuestran su correo con el código de alta (ADR 014).
ALTER TABLE usuarios ADD COLUMN correo_sin_confirmar_desde TIMESTAMPTZ;

COMMENT ON COLUMN usuarios.correo_sin_confirmar_desde IS
    'Desde cuándo espera a confirmar su correo quien registró la empresa. NULL: confirmado o no aplica.';

-- El código de la confirmación es un código de acceso más (mismo hash,
-- mismos intentos, mismo tope por hora), de otro tipo.
ALTER TABLE codigos_acceso DROP CONSTRAINT ck_codigos_acceso_tipo;
ALTER TABLE codigos_acceso ADD CONSTRAINT ck_codigos_acceso_tipo
    CHECK (tipo IN ('ALTA', 'RECUPERACION', 'CONFIRMACION'));
