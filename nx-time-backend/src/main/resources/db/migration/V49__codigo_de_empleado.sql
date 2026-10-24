-- El código de empleado (plan K, paso 1b; ADR 053).
--
-- El número con el que la empresa conoce a cada persona en su ERP o en su
-- programa de nóminas (en Dynamics 365, el «número de personal»). Es lo que
-- permite casar las horas de NX Time con esos sistemas: el nombre se repite y
-- se escribe de mil maneras, y el correo cambia.
--
-- Opcional: null si la empresa no lo usa. Único dentro de cada empresa, sin
-- distinguir mayúsculas («e001» y «E001» serían la misma persona para quien
-- lo teclea). Entre empresas se puede repetir: cada una numera a su gente.
ALTER TABLE usuarios ADD COLUMN codigo_empleado VARCHAR(50);

CREATE UNIQUE INDEX ux_usuarios_empresa_codigo_empleado
    ON usuarios (empresa_id, upper(codigo_empleado))
    WHERE codigo_empleado IS NOT NULL;
