-- Aviso a quien aprueba ausencias cuando alguien empieza a trabajar en un
-- festivo o durante una ausencia aprobada (09/2026).
--
-- Solo cambia el CHECK de los tipos de aviso: el aviso no guarda nada que no
-- quepa ya en la tabla "avisos".

-- 🚨 El CHECK se reemplaza ENTERO: los dieciséis de V20 más el nuevo.
-- Listar de menos no falla al migrar, falla al insertar el aviso, dentro del
-- listener @Async y en silencio.
ALTER TABLE avisos DROP CONSTRAINT ck_avisos_tipo;
ALTER TABLE avisos ADD CONSTRAINT ck_avisos_tipo CHECK (tipo IN (
    -- Fase A
    'AUSENCIA_SOLICITADA',
    'AUSENCIA_RESUELTA',
    'BIENVENIDA',
    -- Fase E
    'CORRECCION_SOLICITADA',
    'CORRECCION_RESUELTA',
    'CORRECCION_EN_DISPUTA',
    -- Fase F
    'HORAS_EXTRA_DETECTADAS',
    'BOLSA_HORAS_EXTRA_AL_LIMITE',
    -- Fase G
    'DENUNCIA_RECIBIDA',
    'DENUNCIA_ACTUALIZADA',
    -- Fase H
    'OFERTA_PUBLICADA',
    'CANDIDATURA_RECIBIDA',
    'CANDIDATURA_ACTUALIZADA',
    -- 09/2026
    'RESUMEN_HORAS_EXTRA',
    -- 09/2026: borrado de datos (ADR 016)
    'BORRADO_SOLICITADO',
    'BORRADO_RECHAZADO',
    -- 09/2026: trabajar en festivo o con una ausencia aprobada
    'TRABAJO_EN_DIA_NO_LABORABLE'
));
