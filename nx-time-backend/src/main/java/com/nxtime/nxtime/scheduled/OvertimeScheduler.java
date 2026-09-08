package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.service.OvertimeService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barre cada noche los fichajes recientes buscando excesos de jornada
 * (Fase F).
 *
 * <b>Va a las 3:30 y no a las 3:00 por una razón concreta</b>: a las 3:00
 * corre {@link IncompleteTimeEntryScheduler}, que cierra las jornadas que
 * nadie cerró. Una jornada abierta no tiene hora de salida, así que no
 * entra en el agregado y no puede producir un aviso. Si los dos procesos
 * se cruzaran, media hora de fichajes olvidados se quedaría sin mirar
 * hasta la noche siguiente.
 *
 * <b>Y mira catorce días atrás, no solo ayer.</b> Desde la Fase E las
 * correcciones son rutina: alguien pide el jueves que le arreglen el
 * fichaje del lunes pasado, y cuando se aprueba, ese lunes cambia. Un
 * barrido que solo mirara el día anterior nunca se enteraría. La ventana
 * cubre además dos semanas ISO completas, que es lo que necesita el
 * umbral semanal.
 *
 * Volver a pasar por los mismos días es seguro por construcción: el
 * índice único {@code uq_horas_extra_usuario_fecha_tipo} (V12) y la
 * lógica de {@code OvertimeServiceImpl#detectar} hacen que la segunda
 * pasada actualice o retire, nunca duplique.
 */
@Component
public class OvertimeScheduler {

    private static final Logger log = LoggerFactory.getLogger(OvertimeScheduler.class);

    /** Ver el Javadoc de la clase. */
    private static final int DIAS_HACIA_ATRAS = 14;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final OvertimeService overtimeService;

    public OvertimeScheduler(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Madrid")
    public void detectarHorasExtra() {
        LocalDate hoy = LocalDate.now(MADRID);
        // Hasta AYER: el día en curso está a medias y una jornada sin
        // cerrar no tiene horas que contar.
        LocalDate hasta = hoy.minusDays(1);
        LocalDate desde = hoy.minusDays(DIAS_HACIA_ATRAS);

        try {
            int tocados = overtimeService.detectar(desde, hasta);
            log.info("Revisión de horas extra {} .. {}: {} avisos nuevos o actualizados.",
                    desde, hasta, tocados);
        } catch (RuntimeException e) {
            // Se traga el fallo a propósito: si esto revienta, el
            // scheduler de Spring deja de reintentar el método pero la
            // aplicación sigue en pie. Un barrido perdido se recupera
            // solo la noche siguiente -- la ventana de catorce días
            // existe también para esto.
            log.error("Falló el barrido de horas extra {} .. {}: {}", desde, hasta, e.getMessage(), e);
        }
    }
}
