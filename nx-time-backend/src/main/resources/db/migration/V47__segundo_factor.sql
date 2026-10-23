-- Segundo factor (TOTP) y sesiones que se pueden cortar al momento (plan J,
-- fase J8; ADR 051).
--
-- 1. El secreto TOTP de cada persona, CIFRADO (AES-GCM con TOTP_CLAVE, que no
--    está en la base): una copia de la base no basta para generar códigos.
--    Sin confirmar (confirmado_en NULL) es una activación a medias: el
--    secreto ya se ha enseñado, pero hasta que la persona escribe un código
--    bueno no se le pide al entrar.
--
--    ultimo_paso: el último intervalo de 30 s cuyo código se aceptó. Un
--    código ya usado no vuelve a valer aunque siga dentro de la ventana
--    (RFC 6238, apartado 5.2): quien lo vea por encima del hombro llega tarde.
CREATE TABLE segundo_factor (
    usuario_id      BIGINT      PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    secreto_cifrado VARCHAR(255) NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmado_en   TIMESTAMPTZ,
    ultimo_paso     BIGINT
);

-- 2. Los códigos de recuperación: diez, de un solo uso. Solo su SHA-256
--    (tienen unos 50 bits de azar: no hace falta un hash lento). VARCHAR y no
--    CHAR, como en V13: Hibernate valida el tipo y CHAR no casa con String.
CREATE TABLE codigos_recuperacion (
    id          BIGSERIAL   PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    hash        VARCHAR(64) NOT NULL,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    usado_en    TIMESTAMPTZ,
    CONSTRAINT uq_codigos_recuperacion UNIQUE (usuario_id, hash)
);

-- 3. Cuándo se activó, en la fila de la persona: el filtro de cada petición
--    ya la lee, y así saber si alguien lo tiene no cuesta otra consulta. Lo
--    escriben sentencias propias, nunca un save de la entidad.
--
--    sesiones_validas_desde: «cerrar todas las sesiones» corta también los
--    access tokens ya emitidos (hasta ahora vivían sus 15 minutos). Un token
--    emitido antes de esta fecha no vale.
ALTER TABLE usuarios
    ADD COLUMN segundo_factor_desde   TIMESTAMPTZ,
    ADD COLUMN sesiones_validas_desde TIMESTAMPTZ;

-- 4. Si la sesión pasó por un segundo factor: un código TOTP, uno de
--    recuperación o Google/Microsoft. Las de antes de esta migración, no.
ALTER TABLE refresh_tokens
    ADD COLUMN con_segundo_factor BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. El ADMIN puede exigirlo a los ADMIN y RRHH de su empresa.
ALTER TABLE empresas
    ADD COLUMN exigir_segundo_factor BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN empresas.exigir_segundo_factor IS
    'Si los ADMIN y RRHH de la empresa tienen que entrar con segundo factor. Lo decide un ADMIN.';
