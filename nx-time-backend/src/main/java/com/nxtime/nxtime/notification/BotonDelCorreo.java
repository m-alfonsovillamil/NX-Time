package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.NoticeType;
import java.util.Map;
import java.util.Optional;

/**
 * El botón que lleva cada correo a su operativa: «Firmar mi registro» abre la
 * página de firmas, «Revisar la corrección» la bandeja de correcciones…
 *
 * <h2>El destino es el mismo que el del aviso de la app</h2>
 *
 * Cada correo de aquí va de la mano de un aviso dentro de la aplicación
 * ({@link NotificationListener} crea los dos), y el botón lleva al mismo sitio
 * que ese aviso: {@link NoticeType#getRutaDestinoPorDefecto()}. Esos destinos
 * son rutas de la web (secciones.ts los traduce tal cual), así que no hay una
 * segunda tabla de rutas que se pueda quedar atrás.
 *
 * <h2>Por qué solo estos</h2>
 *
 * Los correos de seguridad (códigos de acceso, segundo factor, cuenta ya
 * existente, cuenta vinculada con SSO) no llevan botón a propósito: un correo
 * que invita a pulsar para entrar es justo lo que imita un phishing, y no hay
 * que acostumbrar a nadie a hacerlo. Tampoco los del ciclo de vida de una
 * empresa, que ya dicen la web, ni los de un borrado ejecutado: esa cuenta ya
 * no existe.
 *
 * El enlace es la web a secas, sin ningún token: quien no tenga la sesión
 * abierta entra y la web le devuelve a esa página (ver AlEntrar en rutas.tsx).
 */
final class BotonDelCorreo {

    /** El texto del botón y el aviso cuyo destino comparte. */
    record Boton(NoticeType aviso, String texto) {

        String ruta() {
            return aviso.getRutaDestinoPorDefecto();
        }
    }

    private static final Map<String, Boton> POR_PLANTILLA = Map.ofEntries(
            Map.entry("absence-requested", new Boton(NoticeType.AUSENCIA_SOLICITADA, "Revisar la petición")),
            Map.entry("absence-resolved", new Boton(NoticeType.AUSENCIA_RESUELTA, "Ver mis ausencias")),
            Map.entry("correction-requested", new Boton(NoticeType.CORRECCION_SOLICITADA, "Revisar la corrección")),
            Map.entry("correction-disputed", new Boton(NoticeType.CORRECCION_EN_DISPUTA, "Revisar la corrección")),
            Map.entry("correction-resolved", new Boton(NoticeType.CORRECCION_RESUELTA, "Ver mi historial")),
            Map.entry("overtime-detected", new Boton(NoticeType.HORAS_EXTRA_DETECTADAS, "Ver las horas extra")),
            Map.entry("overtime-balance-near-limit",
                    new Boton(NoticeType.BOLSA_HORAS_EXTRA_AL_LIMITE, "Ver las horas extra")),
            Map.entry("overtime-summary", new Boton(NoticeType.RESUMEN_HORAS_EXTRA, "Revisar las horas extra")),
            Map.entry("schedule-incident-detected", new Boton(NoticeType.INCIDENCIA_DETECTADA, "Ver mis incidencias")),
            Map.entry("schedule-incident-summary", new Boton(NoticeType.RESUMEN_INCIDENCIAS, "Revisar las incidencias")),
            Map.entry("worked-on-non-working-day",
                    new Boton(NoticeType.TRABAJO_EN_DIA_NO_LABORABLE, "Ver el equipo")),
            Map.entry("schedule-differs-from-contract",
                    new Boton(NoticeType.CUADRANTE_DISTINTO_DE_JORNADA, "Ver el equipo")),
            Map.entry("signature-reminder", new Boton(NoticeType.RECORDATORIO_FIRMA, "Firmar mi registro")),
            Map.entry("signature-invalidated", new Boton(NoticeType.FIRMA_INVALIDADA, "Volver a firmar")),
            Map.entry("complaint-received", new Boton(NoticeType.DENUNCIA_RECIBIDA, "Abrir el canal de denuncias")),
            Map.entry("complaint-updated", new Boton(NoticeType.DENUNCIA_ACTUALIZADA, "Ver mi denuncia")),
            Map.entry("job-posting-published", new Boton(NoticeType.OFERTA_PUBLICADA, "Ver las ofertas")),
            Map.entry("job-application-received", new Boton(NoticeType.CANDIDATURA_RECIBIDA, "Ver las candidaturas")),
            Map.entry("job-application-updated", new Boton(NoticeType.CANDIDATURA_ACTUALIZADA, "Ver mis candidaturas")),
            Map.entry("deletion-requested", new Boton(NoticeType.BORRADO_SOLICITADO, "Revisar la solicitud")),
            Map.entry("deletion-rejected", new Boton(NoticeType.BORRADO_RECHAZADO, "Abrir mis ajustes")));

    private BotonDelCorreo() {
    }

    static Optional<Boton> de(String plantilla) {
        return Optional.ofNullable(POR_PLANTILLA.get(plantilla));
    }

    /** Para los tests: todas las plantillas que llevan botón. */
    static Map<String, Boton> todos() {
        return POR_PLANTILLA;
    }
}
