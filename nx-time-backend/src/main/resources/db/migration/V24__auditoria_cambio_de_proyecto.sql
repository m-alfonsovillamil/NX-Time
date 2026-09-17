-- Cambiar de proyecto durante la jornada queda en la traza del fichaje (ADR 017).
--
-- 🚨 El CHECK se reemplaza ENTERO: las nueve acciones de V19 más la nueva. La
-- columna ya es VARCHAR(30) desde V11; la nueva mide 17.
ALTER TABLE auditoria_fichaje DROP CONSTRAINT auditoria_fichaje_accion_check;
ALTER TABLE auditoria_fichaje ADD CONSTRAINT auditoria_fichaje_accion_check
    CHECK (accion IN (
        'CREACION',
        'MODIFICACION',
        'CORRECCION',
        'ANULACION',
        'SOLICITUD_CORRECCION',
        'RECHAZO_CORRECCION',
        'DISPUTA',
        'PAUSA_ANADIDA',
        'PAUSA_ANULADA',
        'PROYECTO_CAMBIADO'
    ));
