-- Solicitudes de borrado que RRHH o ADMIN registra en nombre de alguien
-- (ADR 016).
--
-- Quien ya está de baja no puede entrar en la aplicación a pedir el borrado,
-- y el derecho no se pierde por eso: lo pide por correo o por carta, y la
-- empresa tiene que poder dejar constancia. registrada_por_id dice quién la
-- registró; NULL es que la pidió la propia persona desde la app.

ALTER TABLE solicitudes_borrado
    ADD COLUMN registrada_por_id BIGINT;

ALTER TABLE solicitudes_borrado
    ADD CONSTRAINT fk_borrados_registrada_por
        FOREIGN KEY (registrada_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

-- Nadie registra "en nombre" de sí mismo: para eso está Ajustes. Y así una
-- solicitud propia no puede hacerse pasar por recibida de fuera.
--
-- El motivo (cómo llegó la petición) lo exige el servicio y no un CHECK: la
-- anonimización lo pone a NULL cuatro años después, y un CHECK lo impediría.
ALTER TABLE solicitudes_borrado
    ADD CONSTRAINT ck_borrados_registrada_por_otro
        CHECK (registrada_por_id IS NULL OR registrada_por_id <> usuario_id);
