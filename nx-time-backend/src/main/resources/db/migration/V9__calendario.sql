-- Fase C (calendario laboral): de qué ÁMBITO es cada festivo.
--
-- Va numerada V9 y no V8 aunque en `main` el V8 todavía no exista: el
-- V8 es `V8__adjuntos.sql`, que vive en la rama de la fase B2 y entra
-- antes que esta. Flyway acepta huecos, pero NO acepta que aparezca un
-- V8 después de haber aplicado un V9 (`outOfOrder` está desactivado),
-- así que **la fase B2 se mergea antes que esta**.
--
-- ---------------------------------------------------------------------
-- Ámbito del festivo
-- ---------------------------------------------------------------------
-- Hasta ahora la tabla distinguía dos casos por la clave foránea:
-- `empresa_id IS NULL` = nacional, con valor = "propio de la empresa"
-- (ver V4 y Holiday.java). Eso basta para calcular días hábiles, pero
-- no para PINTAR un calendario ni para explicar de dónde sale cada día:
-- un festivo autonómico, uno local y un día de convenio son tres cosas
-- distintas que hoy se guardan idénticas.
--
-- La distinción nacional/no-nacional sigue viviendo en `empresa_id`, y
-- el CHECK de más abajo impide que las dos columnas se contradigan. El
-- ámbito no la sustituye: la detalla.
ALTER TABLE festivos ADD COLUMN ambito VARCHAR(20);

-- Los que ya hay: nacional si no tiene empresa, y si la tiene lo único
-- que se puede afirmar con certeza es que es propio de esa empresa. Los
-- que sembró DemoDataSeeder eran exactamente eso ("Día de convenio"),
-- así que EMPRESA no inventa nada; adivinar AUTONOMICO o LOCAL sí lo
-- haría.
UPDATE festivos SET ambito = CASE WHEN empresa_id IS NULL THEN 'NACIONAL' ELSE 'EMPRESA' END;

ALTER TABLE festivos ALTER COLUMN ambito SET NOT NULL;

ALTER TABLE festivos ADD CONSTRAINT ck_festivos_ambito
    CHECK (ambito IN ('NACIONAL', 'AUTONOMICO', 'LOCAL', 'EMPRESA'));

-- Las dos columnas dicen lo mismo desde dos ángulos, así que hay una
-- combinación imposible en cada sentido: un NACIONAL con empresa (sería
-- nacional solo para una empresa, que es una contradicción) y un
-- AUTONOMICO/LOCAL/EMPRESA sin ella (nadie sabría a quién aplica, y el
-- índice `uq_festivos_nacional_fecha` lo trataría como nacional).
--
-- Se escribe como una igualdad de dos booleanos y no como dos CHECK
-- separados porque es una sola regla: o ambos lados son ciertos o
-- ninguno.
ALTER TABLE festivos ADD CONSTRAINT ck_festivos_ambito_coherente
    CHECK ((ambito = 'NACIONAL') = (empresa_id IS NULL));

-- ---------------------------------------------------------------------
-- Por qué NO se guarda quién creó cada festivo
-- ---------------------------------------------------------------------
-- Se valoró un `creado_por_id` para la traza. No entra: los nacionales
-- los siembra el sistema (NationalHolidayGenerator) y no tienen autor
-- humano, así que la columna sería NULL en la mayoría de las filas y
-- NULL tendría que significar "lo hizo el sistema" -- exactamente el
-- matiz que en `auditoria_fichaje` hizo falta documentar en V4. Un
-- festivo, además, no es un dato que se dispute: o el día es festivo o
-- no lo es, y quien lo tecleó no cambia esa respuesta.
