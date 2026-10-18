-- El canal de denuncias, a la altura de la Ley 2/2023 (plan J, fase J6; ADR 048).
--
-- 1. El Responsable del Sistema Interno de Información (art. 8). Lo designa el
--    ADMIN entre la gente de la empresa, y desde ese momento solo él instruye:
--    los ADMIN dejan de ver el canal. Sin designar, instruyen los ADMIN, como
--    hasta ahora (ADR 012), y se les pide que lo designen.
--    Columna y no relación JPA: se lee en cada petición para resolver los
--    permisos, y así no hay que cargar a nadie más.
ALTER TABLE empresas
    ADD COLUMN responsable_canal_id BIGINT REFERENCES usuarios (id) ON DELETE SET NULL,
    ADD COLUMN responsable_canal_desde TIMESTAMPTZ,
    -- Cómo comunicar algo de palabra o pedir una reunión (arts. 7.2 y 9.2):
    -- la vía verbal no tiene por qué pasar por la aplicación, pero hay que
    -- decir cuál es. La escribe el ADMIN o el Responsable.
    ADD COLUMN canal_via_verbal VARCHAR(500);

COMMENT ON COLUMN empresas.responsable_canal_id IS
    'Responsable del Sistema Interno de Información (Ley 2/2023, art. 8): el único que instruye. Null: los ADMIN.';

ALTER TABLE denuncias
    -- Un domicilio, correo o lugar seguro para las notificaciones (art. 7.2).
    -- Opcional, y quien lo da sabe que puede identificarle.
    ADD COLUMN contacto_notificaciones VARCHAR(255),
    -- Cuándo se pasó a investigación. Sin ella, a los tres meses se suprime el
    -- contenido (art. 32.3). Se fecha al pasar a EN_INVESTIGACION.
    ADD COLUMN investigacion_iniciada_en TIMESTAMPTZ,
    -- La respuesta se amplió de tres a seis meses por su complejidad (art. 9.2.d).
    ADD COLUMN respuesta_prorrogada_en TIMESTAMPTZ,
    -- Cuándo se suprimió el contenido: queda solo lo que dice que existió
    -- (fecha, categoría, estado), como evidencia del funcionamiento del sistema.
    ADD COLUMN contenido_suprimido_en TIMESTAMPTZ;

-- Las de antes: las que están o estuvieron en investigación, desde su acuse.
-- Las ARCHIVADAS no se sabe si pasaron por investigación; se tratan como si
-- no, que es lo que protege a quien aparece en ellas.
UPDATE denuncias
   SET investigacion_iniciada_en = COALESCE(acuse_recibo_en, creado_en)
 WHERE estado IN ('EN_INVESTIGACION', 'RESUELTA');

-- Una denuncia suprimida se cierra aunque no llegara a tener acuse (las de
-- antes del acuse automático): no se inventa una fecha que no existió.
ALTER TABLE denuncias DROP CONSTRAINT ck_denuncias_acuse_antes_del_cierre;
ALTER TABLE denuncias ADD CONSTRAINT ck_denuncias_acuse_antes_del_cierre CHECK (
    resuelta_en IS NULL OR acuse_recibo_en IS NOT NULL OR contenido_suprimido_en IS NOT NULL
);

-- La pasada nocturna busca las que llevan tres meses sin investigar.
CREATE INDEX idx_denuncias_sin_investigar ON denuncias (creado_en)
    WHERE investigacion_iniciada_en IS NULL AND contenido_suprimido_en IS NULL;
