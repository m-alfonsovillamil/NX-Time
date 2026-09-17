package com.nxtime.nxtime.service.impl;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Lo que de verdad borra y anonimiza datos personales (ADR 016). Nada más.
 *
 * Va en SQL explícito, sentencia a sentencia, y no repartido por las
 * entidades, a propósito: es la parte del proyecto que destruye datos de
 * forma irreversible, y tiene que poder leerse entera en una pantalla para
 * saber qué se borra, qué se conserva y por qué. Se ejecuta dentro de la
 * transacción de quien lo llama (JdbcTemplate comparte la conexión), así que
 * si algo falla a medias no se queda nada a medias.
 *
 * <p>Las dos fases, y la razón de que sean dos:
 * <ul>
 *   <li>{@link #purgar}: al ejecutar la solicitud. Lo que la ley NO obliga a
 *       conservar.</li>
 *   <li>{@link #anonimizar}: 4 años después del último fichaje. Lo que había
 *       que conservar para una inspección, cuando ya no hay que conservarlo.</li>
 * </ul>
 */
@Component
class PersonalDataEraser {

    /** Lo que queda en los textos libres. No vacío: varios CHECK exigen texto. */
    static final String TEXTO_ELIMINADO = "[eliminado]";

    static final String NOMBRE_ANONIMO = "Persona eliminada";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    PersonalDataEraser(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Borra lo prescindible y deja la cuenta sin forma de entrar. Devuelve
     * cuántas filas se han ido de cada tabla, para el log.
     *
     * <p>Lo que se <b>conserva</b>: nombre, apellidos, correo, fichajes,
     * pausas, ausencias, vacaciones, correcciones, horas extra, proyectos y la
     * traza de auditoría. Es el registro horario y lo que lo explica.
     */
    Map<String, Integer> purgar(long usuarioId) {
        // Primero las candidaturas: congelan una copia del CV (ADR 013) con
        // una clave RESTRICT hacia adjuntos, así que con ellas vivas la base
        // no dejaría borrar el CV.
        int candidaturas = jdbc.update("DELETE FROM candidaturas WHERE usuario_id = ?", usuarioId);
        // CV y foto, vigentes y antiguos. adjunto_datos se va en cascada.
        int adjuntos = jdbc.update("DELETE FROM adjuntos WHERE usuario_id = ?", usuarioId);
        int avisos = jdbc.update("DELETE FROM avisos WHERE destinatario_id = ?", usuarioId);
        int sesiones = jdbc.update("DELETE FROM refresh_tokens WHERE usuario_id = ?", usuarioId);
        int codigos = jdbc.update("DELETE FROM codigos_acceso WHERE usuario_id = ?", usuarioId);

        // La cuenta: fuera la fecha de nacimiento, baja, y una contraseña que
        // nadie conoce (el hash de un UUID que no se guarda). Sin sesiones y
        // sin contraseña, no hay forma de entrar.
        jdbc.update("""
                UPDATE usuarios
                   SET fecha_nacimiento = NULL,
                       activo = FALSE,
                       fecha_baja = COALESCE(fecha_baja, NOW()),
                       contrasena = ?
                 WHERE id = ?
                """, contrasenaInutilizable(), usuarioId);

        return Map.of(
                "candidaturas", candidaturas,
                "adjuntos", adjuntos,
                "avisos", avisos,
                "sesiones", sesiones,
                "codigosDeAcceso", codigos);
    }

    /**
     * Quita la identidad de la persona y los textos que escribió.
     *
     * <p>Lo que <b>no se puede</b> tocar, y queda dicho en el ADR: el motivo de
     * cada línea de {@code auditoria_fichaje}. Esa tabla es append-only por
     * trigger (V5) y eso no se relaja ni para esto. Sin nombre ni correo en
     * {@code usuarios}, esos motivos dejan de estar asociados a nadie
     * identificable.
     */
    void anonimizar(long usuarioId) {
        jdbc.update("""
                UPDATE usuarios
                   SET nombre = ?,
                       apellidos = NULL,
                       email = 'eliminado-' || id || '@anonimo.invalid',
                       fecha_nacimiento = NULL,
                       puesto = NULL,
                       departamento_id = NULL,
                       contrasena = ?
                 WHERE id = ?
                """, NOMBRE_ANONIMO, contrasenaInutilizable(), usuarioId);

        // Los textos libres que escribió o que se escribieron sobre ella.
        jdbc.update("""
                UPDATE peticiones_ausencia
                   SET motivo = CASE WHEN motivo IS NULL THEN NULL ELSE ? END,
                       comentario_resolucion = CASE WHEN comentario_resolucion IS NULL THEN NULL ELSE ? END
                 WHERE usuario_id = ?
                """, TEXTO_ELIMINADO, TEXTO_ELIMINADO, usuarioId);

        jdbc.update("""
                UPDATE solicitudes_correccion
                   SET motivo = ?,
                       comentario_resolucion = CASE WHEN comentario_resolucion IS NULL THEN NULL ELSE ? END,
                       motivo_disputa = CASE WHEN motivo_disputa IS NULL THEN NULL ELSE ? END
                 WHERE solicitante_id = ?
                    OR registro_id IN (SELECT id FROM registros WHERE usuario_id = ?)
                """, TEXTO_ELIMINADO, TEXTO_ELIMINADO, TEXTO_ELIMINADO, usuarioId, usuarioId);

        jdbc.update("""
                UPDATE pausas_anadidas
                   SET motivo = ?
                 WHERE creada_por_id = ?
                    OR registro_id IN (SELECT id FROM registros WHERE usuario_id = ?)
                """, TEXTO_ELIMINADO, usuarioId, usuarioId);

        jdbc.update("""
                UPDATE avisos_horas_extra
                   SET justificacion = ?
                 WHERE usuario_id = ? AND justificacion IS NOT NULL
                """, TEXTO_ELIMINADO, usuarioId);

        // Canal de denuncias: solo el lado del DENUNCIANTE. Los mensajes del
        // instructor exigen autor identificado (CHECK de V13), y si esta
        // persona instruyó expedientes, esos siguen apuntando a su fila, que
        // ya dice "Persona eliminada".
        jdbc.update("UPDATE denuncias SET denunciante_id = NULL WHERE denunciante_id = ?", usuarioId);
        jdbc.update("""
                UPDATE denuncia_mensajes
                   SET autor_id = NULL
                 WHERE autor_id = ? AND autor_rol = 'DENUNCIANTE'
                """, usuarioId);
    }

    private String contrasenaInutilizable() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }
}
