-- Cancelar una ausencia (ADR 058; plan N, paso N5).
--
-- Hasta ahora una petición solo podía acabar aprobada o rechazada, y una
-- aprobada no la podía deshacer nadie: si alguien cambiaba de planes, los días
-- se quedaban gastados en su saldo y la ausencia en el calendario del equipo.
-- CANCELADA es el cuarto estado: la retira quien la pidió, o la anula quien
-- aprueba las ausencias. Como una rechazada, ya no cuenta para nada; a
-- diferencia de ella, nadie la ha denegado, y dice quién la canceló y cuándo.

ALTER TABLE peticiones_ausencia
    ADD COLUMN cancelada_por_id   BIGINT REFERENCES usuarios (id),
    ADD COLUMN cancelada_en       TIMESTAMPTZ,
    ADD COLUMN motivo_cancelacion VARCHAR(500);

-- El CHECK de estados se reemplaza entero, con todos los valores.
ALTER TABLE peticiones_ausencia DROP CONSTRAINT ck_peticiones_estado;
ALTER TABLE peticiones_ausencia ADD CONSTRAINT ck_peticiones_estado
    CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA'));

-- Quién resolvió y quién canceló. Una CANCELADA conserva quién la aprobó si
-- llegó a aprobarse (por eso ahí no se exige nada de la resolución), y solo
-- una CANCELADA lleva datos de cancelación. NOT VALID, como el original de V4:
-- las filas anteriores a V4 no tienen resolutor y no se le inventa uno.
ALTER TABLE peticiones_ausencia DROP CONSTRAINT ck_peticiones_resolucion_coherente;
ALTER TABLE peticiones_ausencia ADD CONSTRAINT ck_peticiones_resolucion_coherente CHECK (
    (estado = 'PENDIENTE'
        AND aprobado_por_id IS NULL AND fecha_resolucion IS NULL
        AND cancelada_por_id IS NULL AND cancelada_en IS NULL)
    OR (estado IN ('APROBADA', 'RECHAZADA')
        AND aprobado_por_id IS NOT NULL AND fecha_resolucion IS NOT NULL
        AND cancelada_por_id IS NULL AND cancelada_en IS NULL)
    OR (estado = 'CANCELADA'
        AND cancelada_por_id IS NOT NULL AND cancelada_en IS NOT NULL)
) NOT VALID;

-- El aviso nuevo. El CHECK de tipos se reemplaza entero, con todos los valores.
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
    'PIN_KIOSCO_ANULADO_EQUIPO',
    -- 11/2026: una ausencia cancelada (ADR 058)
    'AUSENCIA_CANCELADA'
));
