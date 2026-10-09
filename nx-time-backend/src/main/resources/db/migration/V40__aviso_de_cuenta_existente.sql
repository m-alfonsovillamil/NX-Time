-- Registrar una empresa con un correo que ya tiene cuenta manda un correo de
-- «ya tienes cuenta» a su dueño (ADR 037).
--
-- Desde el ADR 034 ese registro respondía lo de siempre («te hemos mandado un
-- código») y no mandaba nada, para no decir qué correos tienen cuenta. Quien
-- se registraba con su propio correo, olvidando que ya tenía cuenta, se
-- quedaba esperando un código que no iba a llegar y creía que el correo
-- fallaba.
--
-- El aviso se limita por cuenta, y el límite tiene que sobrevivir a un
-- reinicio: el registro es público, y sin él serviría para llenarle el buzón
-- a cualquiera que tenga cuenta. Por eso es una columna y no un contador en
-- memoria.
ALTER TABLE usuarios ADD COLUMN aviso_cuenta_existente_en TIMESTAMPTZ;

COMMENT ON COLUMN usuarios.aviso_cuenta_existente_en IS
    'Cuándo se le mandó por última vez el correo de «ya tienes cuenta». NULL: nunca.';
