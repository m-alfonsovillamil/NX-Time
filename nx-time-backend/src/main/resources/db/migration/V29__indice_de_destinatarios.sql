-- El índice que sirve a "a quién hay que avisar" (Fase A5).
--
-- Hasta ahora esa pregunta se resolvía trayendo TODA la plantilla de la
-- empresa a memoria y filtrándola en Java: una vez por cada petición de
-- ausencia, corrección, denuncia o solicitud de borrado, y también al fichar
-- en un día no laborable, que es camino caliente. Cada usuario llegaba además
-- con su empresa y su departamento, que son relaciones EAGER.
--
-- Ahora la consulta es
--   WHERE empresa_id = ? AND activo = true AND rol IN (...)
-- y este índice la cubre entera, en ese mismo orden de selectividad: la
-- empresa descarta casi todo, activo descarta a las bajas, y el rol deja los
-- dos o tres a los que de verdad hay que avisar.
--
-- Vale también para findByEmpresaAndActivoTrue (el aviso de oferta interna,
-- que va a toda la plantilla): usa el prefijo (empresa_id, activo).
CREATE INDEX idx_usuarios_empresa_activo_rol ON usuarios (empresa_id, activo, rol);

COMMENT ON INDEX idx_usuarios_empresa_activo_rol IS
    'Cubre la consulta de destinatarios de avisos (ver UserRepository.findDestinatarios).';
