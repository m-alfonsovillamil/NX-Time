package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ScheduleIncidentResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Incidencias de cuadrante: retrasos, salidas anticipadas y ausencias no
 * justificadas (Fase B2).
 *
 * <b>Se detecta, no se imputa</b>, como las horas extra (ADR 011). Ninguna
 * incidencia descuenta nada: es la constancia de que un día no cuadró con el
 * cuadrante, y el sitio donde queda la explicación. El recorrido también es el
 * de las horas extra: el sistema detecta, la persona explica, y alguien que no
 * es ella decide.
 */
public interface ScheduleIncidentService {

    /**
     * El barrido: compara lo fichado con el horario teórico de cada día del
     * rango, para todas las personas con cuadrante de todas las empresas.
     *
     * Idempotente: pasar dos veces por el mismo día no duplica nada, y las
     * pendientes que ya no proceden —porque una corrección arregló el
     * fichaje, o se aprobó después una baja— se retiran.
     *
     * @return cuántas incidencias nuevas ha creado
     */
    int detectar(LocalDate desde, LocalDate hasta);

    /** Las propias de un año, de la más reciente a la más antigua. */
    List<ScheduleIncidentResponse> mias(User actor, int anio);

    /**
     * La bandeja de quien revisa: las de su empresa sin las suyas. Por
     * defecto las que esperan decisión; con {@code resueltas}, las ya
     * decididas.
     */
    com.nxtime.nxtime.dto.PaginaDTO<ScheduleIncidentResponse> bandeja(User actor, boolean resueltas, org.springframework.data.domain.Pageable pagina);

    /** Quien la tiene explica qué pasó. Se puede rehacer hasta que alguien decida. */
    ScheduleIncidentResponse justificar(long incidenciaId, String texto, User actor);

    /**
     * Alguien que no es quien la tiene decide. Aceptar no pide motivo;
     * rechazar, sí.
     */
    ScheduleIncidentResponse resolver(long incidenciaId, boolean aceptar, String comentario, User actor);
}
