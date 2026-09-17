-- Un aviso nuevo: el resumen nocturno de horas extra para quien revisa
-- (16/09/2026).
--
-- Hasta ahora, del barrido de las 3:30 solo se enteraba el empleado. A los
-- gestores no se les avisaba de nada, y eso era deliberado: una empresa
-- mediana genera decenas de excesos al mes, y un correo por cada uno a cada
-- gestor es spam por diseño (ver OvertimeServiceImpl y NotificationEvents).
--
-- Pero el otro extremo tampoco valía: los gestores solo se enteraban si se
-- les ocurría abrir la bandeja, así que un exceso podía quedarse semanas sin
-- revisar sin que nadie lo supiera. Lo que resuelve la tensión no es el
-- canal sino el NIVEL DE AGREGACIÓN: un aviso por empresa y por noche, con
-- el recuento, en vez de uno por exceso. Las noches sin excesos no mandan
-- nada.
--
-- Esta migración no crea tablas: el resumen se guarda como cualquier otro
-- aviso. Lo único que hace falta es que su tipo quepa en el CHECK.

-- 🚨 El CHECK se REEMPLAZA ENTERO, así que hay que volver a listar TODOS
-- los tipos, no solo el que añade esta migración. Listar de menos no falla
-- al migrar: falla después, al insertar el aviso, dentro del listener
-- @Async y en silencio. Ya pasó una vez entre las fases E y F, y V12 lo
-- dejó escrito para que no se repitiera.
--
-- Van catorce: 3 de la A, 3 de la E, 2 de la F, 2 de la G, 3 de la H y este.
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
    -- 09/2026: el resumen nocturno para quien revisa
    'RESUMEN_HORAS_EXTRA'
));
