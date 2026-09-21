-- El refresh token deja de guardarse en claro, y se rota (Fase A11).
--
-- Hasta ahora la tabla guardaba el token tal cual: un volcado de la base --una
-- copia de seguridad, un acceso de lectura mal dado-- entregaba sesiones vivas
-- de treinta días, listas para usar. Y no rotaba: el mismo valor servía durante
-- todo ese mes, así que robarlo una vez daba acceso hasta que caducara.
--
-- Con la app Android eso era discutible pero acotado. Con un cliente WEB deja
-- de serlo: el token pasa a vivir en un navegador, donde cualquier XSS lo
-- alcanza. Esta migración es lo que hace defendible ese paso (ver bloque C).
--
-- Tres cambios:
--
--  1. Se guarda sha256(token), no el token. La comparación sigue siendo exacta
--     y la base deja de contener nada reutilizable. SHA-256 y no BCrypt a
--     propósito: esto no es una contraseña que alguien elige --es un UUID
--     aleatorio de 122 bits-- así que no hay diccionario contra el que
--     defenderse, y BCrypt en cada renovación costaría decenas de
--     milisegundos por petición sin comprar nada.
--
--  2. Cada renovación emite uno nuevo y marca el viejo como rotado. Un token
--     robado sirve, como mucho, hasta que el dueño renueve.
--
--  3. Si llega un token YA ROTADO, se revoca la familia entera. Es la parte
--     que de verdad protege: significa que dos clientes están usando la misma
--     cadena, y solo uno puede ser el legítimo. Ante la duda, fuera los dos y
--     que se vuelva a entrar.

-- Las sesiones vivas se van. No se puede hashear hacia atrás lo que ya no
-- tenemos --solo está el token, y hashearlo aquí exigiría pgcrypto-- y el
-- coste real es que cada persona vuelva a entrar una vez: la app ya trata el
-- 401 del refresco cerrando sesión y llevando al login.
DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens DROP CONSTRAINT uq_refresh_tokens_token;
ALTER TABLE refresh_tokens DROP COLUMN token;

ALTER TABLE refresh_tokens
    -- 64 caracteres: SHA-256 en hexadecimal. VARCHAR y no CHAR, como el resto
    -- del esquema: con CHAR, PostgreSQL lo declara bpchar y la validación de
    -- esquema de Hibernate rechaza el arranque.
    ADD COLUMN token_hash        VARCHAR(64) NOT NULL,
    -- Todos los tokens que descienden de un mismo login. Es lo que permite
    -- echar a un intruso sin tocar las demás sesiones de esa persona.
    ADD COLUMN familia           UUID        NOT NULL,
    -- De dónde salió, para darle una vida distinta: un navegador no merece
    -- treinta días (ver RefreshToken.Origen).
    ADD COLUMN origen            VARCHAR(16) NOT NULL DEFAULT 'ANDROID',
    ADD COLUMN rotado_en         TIMESTAMPTZ,
    ADD COLUMN sustituido_por_id BIGINT,
    ADD COLUMN revocado_en       TIMESTAMPTZ;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT ck_refresh_origen CHECK (origen IN ('ANDROID', 'WEB', 'IOS')),
    -- Rotado si y solo si apunta a su sucesor: una fila con fecha de rotación
    -- y sin sucesor, o al revés, sería un registro que miente sobre la cadena.
    ADD CONSTRAINT ck_refresh_rotado CHECK ((rotado_en IS NULL) = (sustituido_por_id IS NULL)),
    -- El booleano 'revocado' que ya existía y la fecha nueva cuentan lo mismo,
    -- así que no pueden discrepar.
    ADD CONSTRAINT ck_refresh_revocado CHECK (revocado = (revocado_en IS NOT NULL)),
    ADD CONSTRAINT fk_refresh_sustituido FOREIGN KEY (sustituido_por_id)
        REFERENCES refresh_tokens (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_refresh_token_hash ON refresh_tokens (token_hash);

-- Parcial: revocar una familia solo toca los que siguen vivos, y son pocos.
CREATE INDEX idx_refresh_familia ON refresh_tokens (familia) WHERE revocado = FALSE;

COMMENT ON COLUMN refresh_tokens.token_hash IS
    'sha256 del token. El token en claro solo existe en el cliente (ver V30).';
COMMENT ON COLUMN refresh_tokens.familia IS
    'Todos los tokens que vienen de un mismo login. Reutilizar uno rotado revoca la familia entera.';
