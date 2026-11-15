-- Horas extra compensadas con descanso (ADR 061; plan N, paso N14).
--
-- Al aceptar un aviso se elige si se paga o se compensa con descanso. Las
-- compensadas con descanso dentro de los cuatro meses siguientes no cuentan para
-- el tope de 80 horas al año (art. 35.2 ET). Las aceptadas antes de V58 se quedan
-- sin elegir, y cuentan como pagadas, que es como contaban.
ALTER TABLE avisos_horas_extra
    ADD COLUMN compensacion VARCHAR(10),
    ADD COLUMN descanso_disfrutado_en DATE,
    ADD CONSTRAINT ck_avisos_horas_extra_compensacion CHECK (
        (compensacion IS NULL AND descanso_disfrutado_en IS NULL)
        OR (estado = 'ACEPTADO' AND compensacion = 'PAGO' AND descanso_disfrutado_en IS NULL)
        OR (estado = 'ACEPTADO' AND compensacion = 'DESCANSO'));
