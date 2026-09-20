-- Dos filas de auditoría no pueden decir que van detrás de la MISMA fila.
--
-- El encadenamiento se serializa desde el código con un advisory lock (ver
-- TimeEntryAuditRepository#bloquearCadena). Esto es el cinturón además del
-- tirante: si el lock faltara alguna vez --alguien escribe por otra vía, una
-- versión futura se lo salta sin darse cuenta-- la base rechaza el INSERT en
-- lugar de dejar la cadena bifurcada en silencio.
--
-- Bifurcada es peor que rota: VerificadorDeAuditoria informaría de "el enlace
-- con la fila anterior no cuadra" sobre un registro con valor legal que nadie
-- había tocado. Un error al insertar se ve; una acusación falsa dentro de
-- cuatro años, no.
--
-- hash_anterior es NULL en la primera fila de la cadena, y en PostgreSQL los
-- NULL no colisionan entre sí en un índice único, así que esa fila no estorba.

-- Antes de crear el índice: si la carrera ya ocurrió alguna vez, el CREATE
-- INDEX fallaría con "could not create unique index ... Key (hash_anterior)=
-- (...) is duplicated", que no le dice a nadie qué hacer. Este bloque lo
-- detecta y explica el problema, que es de datos y no de esquema.
DO $$
DECLARE
    bifurcaciones BIGINT;
BEGIN
    SELECT COUNT(*) INTO bifurcaciones FROM (
        SELECT hash_anterior
        FROM auditoria_fichaje
        WHERE hash_anterior IS NOT NULL
        GROUP BY hash_anterior
        HAVING COUNT(*) > 1
    ) duplicados;

    IF bifurcaciones > 0 THEN
        RAISE EXCEPTION
            'La cadena de auditoría ya está bifurcada en % punto(s): hay filas distintas que apuntan al mismo hash_anterior.',
            bifurcaciones
        USING HINT =
            'La carrera que esta migración previene ya ocurrió antes de aplicarla. Estas filas NO están manipuladas: '
            'se escribieron a la vez y se encadenaron mal. Localízalas con '
            'SELECT hash_anterior, COUNT(*) FROM auditoria_fichaje WHERE hash_anterior IS NOT NULL GROUP BY 1 HAVING COUNT(*) > 1; '
            'y decide qué hacer con ellas (documentándolo) antes de volver a aplicar esta migración.';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_auditoria_hash_anterior
    ON auditoria_fichaje (hash_anterior);

COMMENT ON INDEX uq_auditoria_hash_anterior IS
    'Una sola fila puede encadenarse detrás de cada fila. Respaldo del advisory lock de TimeEntryAuditListener (ver V27).';
