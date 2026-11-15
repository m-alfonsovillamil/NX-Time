-- Las vacaciones que sobran de un año pasan al siguiente (ADR 060; plan N,
-- paso N12), si la empresa lo enciende, hasta una fecha tope.
--
-- Solo el ajuste: lo arrastrado no se guarda, se calcula al leer el saldo,
-- como lo consumido (Fase 9): un contador habría que mantenerlo al aprobar,
-- rechazar o cancelar en el año anterior, y cualquier fallo lo dejaría
-- mintiendo. Apagado por defecto: lo decide cada empresa según su convenio.
ALTER TABLE empresas
    ADD COLUMN arrastre_vacaciones BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN arrastre_vacaciones_hasta VARCHAR(5) NOT NULL DEFAULT '03-31',
    ADD CONSTRAINT ck_empresas_arrastre_hasta
        CHECK (arrastre_vacaciones_hasta ~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$');
