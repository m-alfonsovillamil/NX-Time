-- Medios días (ADR 063; plan N, paso N11).
--
-- Una ausencia de UN solo día puede ser de medio día: cuenta 0,5 en el saldo
-- y en los informes, y ese día se sigue trabajando la otra mitad. No se
-- distingue mañana de tarde: el sistema no lo necesita para contar y sería un
-- dato más que mantener.
--
-- Los días no se guardan en ninguna columna (se cuentan al leer con el
-- calendario laboral), así que basta con la marca.
ALTER TABLE peticiones_ausencia ADD COLUMN medio_dia BOOLEAN NOT NULL DEFAULT FALSE;

-- Medio día de un rango no significa nada: ¿la mitad de cuál?
ALTER TABLE peticiones_ausencia ADD CONSTRAINT ck_peticiones_medio_dia_de_un_dia
    CHECK (NOT medio_dia OR fecha_inicio = fecha_fin);
