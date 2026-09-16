-- Correos en minúsculas, siempre (16/09/2026).
--
-- El correo se buscaba con una comparación exacta (findByEmail), así que
-- "Juan.Perez@empresa.com" y "juan.perez@empresa.com" eran dos cuentas
-- distintas para el sistema. Quien tecleaba su dirección con una mayúscula
-- distinta a la del alta no podía entrar, y al pulsar "He olvidado mi
-- contraseña" recibía un 202 y ningún correo: /auth/recuperar responde lo
-- mismo exista la cuenta o no (ADR 014), de modo que el fallo era invisible
-- para la persona y para nosotros. En el piloto habría dejado a gente fuera
-- sin dejar rastro en ningún log.
--
-- La normalización de verdad la hacen los DTO de entrada y un @PrePersist en
-- User; esto arregla lo ya guardado y pone la red que impide que vuelva.

UPDATE usuarios
   SET email = lower(email)
 WHERE email <> lower(email);

-- El UNIQUE de la columna no distingue lo que para nosotros ya es la misma
-- cuenta: con él, "Juan@x.com" y "juan@x.com" pueden convivir como dos filas.
-- Este índice funcional lo impide de verdad. Si esta migración falla aquí es
-- justo por eso -- hay dos cuentas que solo se diferencian en mayúsculas, y
-- hay que decidir a mano cuál se queda antes de volver a aplicarla.
CREATE UNIQUE INDEX ux_usuarios_email_lower ON usuarios (lower(email));
