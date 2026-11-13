package com.nxtime.nxtime.scheduled;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.service.NationalHolidaySeeder;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deja sembrados los festivos nacionales del año en curso y del siguiente:
 * al arrancar y cada noche.
 *
 * <b>Por qué.</b> Hasta noviembre de 2026 solo se sembraban al abrir el
 * calendario de ese año ({@code CalendarServiceImpl}). Mientras nadie lo
 * abría, todo lo demás contaba sus días como laborables: unas vacaciones de
 * Navidad pedidas en diciembre descontaban el 1 y el 6 de enero, dos días de
 * más, y lo mismo las horas extra semanales, la jornada teórica y el aviso de
 * trabajar en festivo. El del año siguiente va por adelantado justo por eso:
 * en diciembre ya se piden vacaciones de enero.
 *
 * <b>Por qué por adelantado y no al leerlos.</b> Se probó a sembrarlos en
 * {@code HolidayCalendar} la primera vez que se consultaba un año, y con
 * ocho fichajes a la vez el pool de conexiones se agotaba: cada hilo tenía su
 * transacción abierta y el sembrador ({@code REQUIRES_NEW}) pedía otra. En
 * producción el pool es de cinco.
 *
 * Fuera del monitor de tareas a propósito: si una noche no corre, lo hace el
 * siguiente arranque, y en el plan gratuito de Render el servicio arranca
 * varias veces al día. Idempotente: si ya están, son dos COUNT.
 */
@Component
public class SiembraDeFestivos {

    private static final Logger log = LoggerFactory.getLogger(SiembraDeFestivos.class);

    private static final ZoneId MADRID = ZoneId.of(ScheduledTask.ZONA);

    private final NationalHolidaySeeder sembrador;

    public SiembraDeFestivos(NationalHolidaySeeder sembrador) {
        this.sembrador = sembrador;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        sembrarDesde(LocalDate.now(MADRID));
    }

    /** Antes de las tareas nocturnas, que ya cuentan con ellos. */
    @Scheduled(cron = "0 50 1 * * *", zone = ScheduledTask.ZONA)
    public void cadaNoche() {
        sembrarDesde(LocalDate.now(MADRID));
    }

    /** El año de {@code hoy} y el siguiente. */
    public void sembrarDesde(LocalDate hoy) {
        try {
            sembrador.asegurarAnio(hoy.getYear());
            sembrador.asegurarAnio(hoy.getYear() + 1);
        } catch (RuntimeException e) {
            // Que no tumbe el arranque: el calendario los siembra igual al
            // abrirlo, y la noche siguiente se vuelve a intentar.
            log.error("No se han podido sembrar los festivos nacionales de {} y {}", hoy.getYear(),
                    hoy.getYear() + 1, e);
        }
    }
}
