package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.NoticeType;

/**
 * Lo que dice un push, según el tipo de aviso (Fase B5, ADR 028).
 *
 * <b>Genérico a propósito.</b> Un push viaja por los servidores de Google, y
 * el aviso de verdad —"tu ausencia del 3 al 7 de marzo ha sido rechazada:
 * coincide con el cierre"— es dato personal y a veces delicado. El push solo
 * dice QUE hay algo y DÓNDE mirarlo; el contenido se lee al abrir la app, por
 * el canal de siempre. Es el mismo criterio que ya seguían los avisos del
 * canal de denuncias, llevado a todos.
 *
 * Un {@code switch} sin {@code default}: el día que se añada un tipo de aviso,
 * esto deja de compilar hasta que alguien decida qué dice su push.
 */
public final class TextoDePush {

    public static final String TITULO = "NX Time";

    private TextoDePush() {
    }

    public static String de(NoticeType tipo) {
        return switch (tipo) {
            case AUSENCIA_SOLICITADA -> "Tienes una ausencia del equipo por revisar.";
            case AUSENCIA_RESUELTA -> "Hay novedades en tus ausencias.";
            case BIENVENIDA -> "Tu cuenta ya está activa.";
            case CORRECCION_SOLICITADA -> "Tienes una corrección de fichaje por revisar.";
            case CORRECCION_RESUELTA -> "Hay novedades en una corrección que pediste.";
            case CORRECCION_EN_DISPUTA -> "Hay una corrección en disputa por resolver.";
            case HORAS_EXTRA_DETECTADAS -> "Se han detectado horas extra en tu jornada.";
            case BOLSA_HORAS_EXTRA_AL_LIMITE -> "Tu bolsa anual de horas extra se está agotando.";
            case RESUMEN_HORAS_EXTRA -> "Hay horas extra del equipo por revisar.";
            case DENUNCIA_RECIBIDA -> "Hay novedades en el canal de denuncias.";
            case DENUNCIA_ACTUALIZADA -> "Hay novedades en una denuncia tuya.";
            case OFERTA_PUBLICADA -> "Hay una vacante interna nueva.";
            case CANDIDATURA_RECIBIDA -> "Alguien se ha presentado a una de tus ofertas.";
            case CANDIDATURA_ACTUALIZADA -> "Hay novedades en tu candidatura.";
            case BORRADO_SOLICITADO -> "Hay una solicitud de borrado de datos por revisar.";
            case BORRADO_RECHAZADO -> "Hay novedades en tu solicitud de borrado de datos.";
            case TRABAJO_EN_DIA_NO_LABORABLE -> "Alguien del equipo ha fichado en un día no laborable.";
            case CUADRANTE_DISTINTO_DE_JORNADA -> "Un cuadrante asignado no cuadra con la jornada contratada.";
            case INCIDENCIA_DETECTADA -> "Tienes incidencias de cuadrante nuevas.";
            case RESUMEN_INCIDENCIAS -> "Hay incidencias de cuadrante del equipo por revisar.";
            case RECORDATORIO_FIRMA -> "Ya puedes firmar tu registro horario del mes.";
            case FIRMA_INVALIDADA -> "Una corrección ha invalidado tu firma mensual.";
        };
    }
}
