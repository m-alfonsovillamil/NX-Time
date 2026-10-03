-- El PIN del kiosco ya no se puede adivinar con el tiempo (revisión de
-- seguridad del 1/10/2026, ADR 034).
--
-- Hasta aquí, cinco fallos bloqueaban el PIN quince minutos y el contador
-- volvía a cero. Probando a ritmo, eran unos 480 PIN por persona y día: un
-- PIN de 4 cifras caía en menos de un mes delante de la tablet. Ahora se
-- cuentan también los bloqueos, y al tercero el PIN se anula: hay que elegir
-- otro en la app. Así, contra un PIN, se pueden probar 15 como mucho.
--
-- Se cuenta por persona y no se pone a cero al acertar: si no, quien prueba
-- tendría 14 intentos cada vez que el dueño ficha bien. Vuelve a cero al
-- elegir un PIN nuevo.
ALTER TABLE usuarios ADD COLUMN kiosco_pin_bloqueos INT NOT NULL DEFAULT 0;

-- Los dos avisos de un PIN anulado: a su dueño, para que elija otro, y a
-- quien gestiona los kioscos, porque alguien ha estado probando.
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
    'TRABAJO_EN_DIA_NO_LABORABLE',
    -- B1: un cuadrante asignado que no suma la jornada contratada
    'CUADRANTE_DISTINTO_DE_JORNADA',
    -- B2: la incidencia, a quien la tiene; el resumen, a quien revisa
    'INCIDENCIA_DETECTADA',
    'RESUMEN_INCIDENCIAS',
    -- B3: el mes que se puede firmar, y la firma que una corrección tumbó
    'RECORDATORIO_FIRMA',
    'FIRMA_INVALIDADA',
    -- 10/2026: un PIN de kiosco anulado tras tres bloqueos (ADR 034)
    'PIN_KIOSCO_ANULADO',
    'PIN_KIOSCO_ANULADO_EQUIPO'
));
