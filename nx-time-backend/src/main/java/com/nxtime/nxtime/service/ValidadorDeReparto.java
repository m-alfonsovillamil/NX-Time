package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.exception.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Comprueba que un reparto de horas por proyecto es legítimo, venga por donde
 * venga.
 *
 * <h2>Por qué es un componente aparte</h2>
 *
 * A una jornada se le puede cambiar el reparto por dos caminos:
 *
 * <ul>
 *   <li>{@code PUT /api/v1/fichaje/{id}/imputaciones} — el reparto libre de la
 *       semana en curso, que pasa por {@code AllocationEditService};</li>
 *   <li>{@code POST /api/v1/fichaje/{id}/correcciones} — una corrección que
 *       lleva el reparto dentro, que pasa por {@code CorrectionService}.</li>
 * </ul>
 *
 * El primero validaba; el segundo no. Solo comprobaba que el proyecto fuera de
 * la misma empresa, así que un empleado podía imputar sus horas a
 * <b>cualquier proyecto de la empresa</b>, incluido uno en el que no había
 * estado nunca, y al aprobarse la corrección se escribía sin volver a mirar.
 * El coste no es un error visible: son informes de coste por proyecto
 * contaminados, que es justo lo que esos informes existen para evitar.
 *
 * El javadoc de {@code CorrectionRequestDTO#reparto} llegó a decir que «lo
 * valida quien lo arma»; era cierto para el camino de
 * {@code AllocationEditService}, que efectivamente valida antes de construir
 * el DTO, y falso para quien llamara al endpoint directamente — que es
 * cualquiera con un cliente HTTP.
 *
 * Tener la regla en un sitio y no en dos es lo mismo que ya se argumenta en
 * {@code TimeEntryAuditListener} sobre {@link
 * com.nxtime.nxtime.audit.HuellaDeAuditoria}: con dos copias, la que se queda
 * atrás es la que falla, y aquí la que se quedó atrás era la única que
 * protegía el dato.
 *
 * <h2>Validar dos veces no molesta</h2>
 *
 * Por el camino de {@code AllocationEditService} el reparto se valida al
 * armarlo y otra vez al guardarlo en la solicitud. Es intencionado: la segunda
 * pasada es idempotente —ya no quedan ceros ni duplicados— y quitarla
 * significaría que la protección dependiera de por dónde entró la petición,
 * que es exactamente el fallo que esto arregla.
 */
@Component
public class ValidadorDeReparto {

    /**
     * La zona que decide de qué día es una jornada. Ver ADR 002 y el comentario
     * de {@code DateFormats} en la app: un fichaje es un instante, pero «los
     * proyectos de ese día» solo tiene sentido en una zona concreta.
     */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /**
     * Tope de líneas. No es una regla de negocio sino un freno: sin él, una
     * petición con miles de líneas se recorre entera antes de rechazarse.
     * Generoso a propósito — quien reparta una jornada entre más de cincuenta
     * proyectos tiene un problema distinto.
     */
    public static final int MAX_LINEAS = 50;

    private final ProjectAllocationService projectAllocationService;

    public ValidadorDeReparto(ProjectAllocationService projectAllocationService) {
        this.projectAllocationService = projectAllocationService;
    }

    /**
     * Una línea del reparto, sin atarse al DTO del que venga.
     *
     * @param proyectoId a qué proyecto
     * @param minutos cuántos minutos, cero para sacarlo del reparto
     */
    public record Linea(long proyectoId, long minutos) {
    }

    /**
     * El reparto con los proyectos resueltos, en segundos, listo para guardar.
     *
     * Los proyectos válidos son los que <b>el dueño del fichaje</b> tenía
     * asignados ese día, no los del actor: cuando RRHH corrige el fichaje de
     * otra persona, lo que cuenta es dónde estaba ella.
     *
     * @throws BusinessException 403 si algún proyecto no estaba asignado ese
     *     día, 400 si hay duplicados, si se pasa del tope o si queda vacío
     */
    public Map<Project, Long> validarYResolver(TimeEntry fichaje, List<Linea> lineas) {
        if (lineas.size() > MAX_LINEAS) {
            throw new BusinessException(
                    "El reparto no puede tener más de " + MAX_LINEAS + " líneas.", HttpStatus.BAD_REQUEST);
        }

        LocalDate dia = fichaje.getHoraEntrada().atZone(MADRID).toLocalDate();
        Map<Long, Project> disponibles = new LinkedHashMap<>();
        projectAllocationService.proyectosDelDia(fichaje.getUsuario(), dia)
                .forEach(proyecto -> disponibles.put(proyecto.getId(), proyecto));

        Map<Project, Long> reparto = new LinkedHashMap<>();
        for (Linea linea : lineas) {
            Project proyecto = disponibles.get(linea.proyectoId());
            if (proyecto == null) {
                // 403 y no 404 a propósito: decir "ese proyecto no existe"
                // distinguiría un proyecto ajeno de uno inventado, y eso es un
                // oráculo sobre los proyectos de la empresa.
                throw new BusinessException(
                        "Ese día no estabas asignado a alguno de los proyectos del reparto.", HttpStatus.FORBIDDEN);
            }
            if (reparto.put(proyecto, linea.minutos() * 60) != null) {
                throw new BusinessException("El mismo proyecto aparece dos veces en el reparto.",
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Las líneas a cero se quitan: es la forma de sacar un proyecto del
        // reparto, y guardar un cero solo ensucia los informes.
        reparto.values().removeIf(segundos -> segundos == 0);
        if (reparto.isEmpty()) {
            throw new BusinessException("El reparto no puede ser todo a cero.", HttpStatus.BAD_REQUEST);
        }
        return reparto;
    }
}
