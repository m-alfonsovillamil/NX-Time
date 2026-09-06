package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CalendarAbsenceDTO;
import com.nxtime.nxtime.dto.CalendarResponse;
import com.nxtime.nxtime.dto.HolidayRequest;
import com.nxtime.nxtime.dto.HolidayResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.service.CalendarService;
import com.nxtime.nxtime.service.HolidayCalendar;
import com.nxtime.nxtime.service.NationalHolidaySeeder;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CalendarServiceImpl implements CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarServiceImpl.class);

    /**
     * Authority que hace falta para ver el calendario de los compañeros.
     * Se reutiliza la de las ausencias en vez de inventar una
     * "calendario:leer:equipo": lo que se está enseñando SON las
     * ausencias del equipo, en otra forma. Dos authorities para el mismo
     * dato acabarían repartidas distinto y una de las dos pantallas
     * enseñaría lo que la otra oculta.
     */
    private static final String AUTHORITY_EQUIPO = "ausencia:leer:equipo";

    /**
     * Años que se aceptan consultar.
     *
     * No es una manía de validación: cada año que se mira por primera
     * vez se SIEMBRA (ver {@link NationalHolidaySeeder}), así que un
     * bucle pidiendo del año 1 al 999999 dejaría diez millones de filas
     * en la tabla. El rango cubre de sobra tanto el histórico de fichajes
     * como cualquier planificación razonable.
     */
    private static final int ANIO_MINIMO = 2000;
    private static final int ANIO_MAXIMO = 2100;

    private final HolidayRepository holidayRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final NationalHolidaySeeder nationalHolidaySeeder;
    private final HolidayCalendar holidayCalendar;

    public CalendarServiceImpl(
            HolidayRepository holidayRepository,
            AbsenceRequestRepository absenceRequestRepository,
            NationalHolidaySeeder nationalHolidaySeeder,
            HolidayCalendar holidayCalendar) {
        this.holidayRepository = holidayRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.nationalHolidaySeeder = nationalHolidaySeeder;
        this.holidayCalendar = holidayCalendar;
    }

    @Override
    public CalendarResponse verMes(int anio, int mes, boolean verEquipo, User actor) {
        YearMonth periodo = periodoValido(anio, mes);

        // Antes de leer nada: si es la primera vez que alguien mira este
        // año, sus festivos nacionales todavía no existen y el mes
        // saldría en blanco. Va en una transacción aparte (REQUIRES_NEW),
        // porque esta es de solo lectura.
        nationalHolidaySeeder.asegurarAnio(anio);

        LocalDate primerDia = periodo.atDay(1);
        LocalDate ultimoDia = periodo.atEndOfMonth();
        long empresaId = actor.getEmpresa().getId();

        List<HolidayResponse> festivos =
                holidayRepository.findAplicables(empresaId, primerDia, ultimoDia).stream()
                        .map(this::toResponse)
                        .toList();

        boolean incluyeEquipo = verEquipo && puedeVerAlEquipo(actor);
        List<AbsenceRequest> ausencias = incluyeEquipo
                ? absenceRequestRepository.findSolapadasDeEmpresa(empresaId, primerDia, ultimoDia)
                : absenceRequestRepository.findSolapadas(actor, primerDia, ultimoDia);

        return new CalendarResponse(
                anio,
                mes,
                incluyeEquipo,
                festivos,
                ausencias.stream().map(ausencia -> toResponse(ausencia, actor)).toList());
    }

    @Override
    @Transactional
    public HolidayResponse crear(HolidayRequest request, User actor) {
        HolidayScope ambito = ambitoDeEmpresa(request.ambito());

        holidayRepository.findDeEmpresaEnFecha(actor.getEmpresa().getId(), request.fecha())
                .ifPresent(existente -> {
                    throw new BusinessException(
                            "Esa fecha ya es festivo para la empresa: " + existente.getDescripcion());
                });

        Holiday festivo = holidayRepository.save(Holiday.builder()
                .empresa(actor.getEmpresa())
                .fecha(request.fecha())
                .descripcion(request.descripcion().trim())
                .ambito(ambito)
                .build());

        // Un festivo nuevo cambia los días hábiles del año, y de ahí sale
        // el saldo de vacaciones de todo el mundo: sin invalidar, el
        // calendario cacheado seguiría contando ese día como laborable
        // hasta seis horas después (ver CacheConfig).
        holidayCalendar.invalidar();
        log.info("{} ha creado el festivo '{}' del {}",
                actor.getEmail(), festivo.getDescripcion(), festivo.getFecha());
        return toResponse(festivo);
    }

    @Override
    @Transactional
    public HolidayResponse editar(long id, HolidayRequest request, User actor) {
        Holiday festivo = gestionable(id, actor);
        HolidayScope ambito = ambitoDeEmpresa(request.ambito());

        // Mover un festivo encima de otro que ya existe es un 409, pero
        // dejarlo en su propia fecha no lo es: sin este "no es él mismo",
        // corregir solo la descripción fallaría.
        Optional<Holiday> enLaFecha =
                holidayRepository.findDeEmpresaEnFecha(actor.getEmpresa().getId(), request.fecha());
        if (enLaFecha.isPresent() && enLaFecha.get().getId() != festivo.getId()) {
            throw new BusinessException(
                    "Esa fecha ya es festivo para la empresa: " + enLaFecha.get().getDescripcion());
        }

        festivo.setFecha(request.fecha());
        festivo.setDescripcion(request.descripcion().trim());
        festivo.setAmbito(ambito);
        holidayRepository.save(festivo);

        holidayCalendar.invalidar();
        return toResponse(festivo);
    }

    @Override
    @Transactional
    public void borrar(long id, User actor) {
        Holiday festivo = gestionable(id, actor);
        holidayRepository.delete(festivo);

        holidayCalendar.invalidar();
        log.info("{} ha borrado el festivo '{}' del {}",
                actor.getEmail(), festivo.getDescripcion(), festivo.getFecha());
    }

    /**
     * El festivo pedido, comprobando que se puede tocar.
     *
     * Son dos negativas distintas y se distinguen a propósito: un
     * nacional existe y es de todos (403 con una explicación), y uno de
     * otra empresa no es asunto de quien pregunta (el 403 de tenant que
     * exige el ADR 006). Un 404 en el primer caso mentiría, y un 404
     * en el segundo tampoco: {@link TenantAccessException} es lo que usa
     * el resto del proyecto.
     */
    private Holiday gestionable(long id, User actor) {
        Holiday festivo = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Festivo no encontrado."));

        if (!festivo.getAmbito().esDeEmpresa()) {
            throw new TenantAccessException(
                    "Los festivos nacionales los pone el sistema y son los mismos para todas las "
                            + "empresas: no se pueden editar ni borrar desde aquí.");
        }
        if (festivo.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar festivos de otra empresa.");
        }
        return festivo;
    }

    /**
     * Rechaza NACIONAL como ámbito de un festivo creado a mano.
     *
     * La base también lo impediría -- {@code ck_festivos_ambito_coherente}
     * no deja un NACIONAL con empresa -- pero llegaría como un 500 sin
     * explicación. Aquí es un 400 que dice qué pasa.
     */
    private HolidayScope ambitoDeEmpresa(HolidayScope ambito) {
        if (!ambito.esDeEmpresa()) {
            // 400 explícito y no el 409 por defecto de BusinessException:
            // no hay ningún conflicto con el estado del servidor, es que
            // el cliente ha mandado un valor que este endpoint no acepta.
            throw new BusinessException(
                    "Los festivos nacionales los pone el sistema. Elige un ámbito autonómico, "
                            + "local o de empresa.",
                    HttpStatus.BAD_REQUEST);
        }
        return ambito;
    }

    private YearMonth periodoValido(int anio, int mes) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("El mes tiene que estar entre 1 y 12.", HttpStatus.BAD_REQUEST);
        }
        if (anio < ANIO_MINIMO || anio > ANIO_MAXIMO) {
            throw new BusinessException(
                    "El año tiene que estar entre " + ANIO_MINIMO + " y " + ANIO_MAXIMO + ".",
                    HttpStatus.BAD_REQUEST);
        }
        return YearMonth.of(anio, mes);
    }

    private boolean puedeVerAlEquipo(User actor) {
        // Se pregunta a RoleAuthorities y no al SecurityContext a
        // propósito: es la misma fuente que alimenta el @PreAuthorize de
        // los endpoints, así que no puede decir una cosa distinta.
        return RoleAuthorities.forRole(actor.getRol()).contains(AUTHORITY_EQUIPO);
    }

    private HolidayResponse toResponse(Holiday festivo) {
        return new HolidayResponse(
                festivo.getId(),
                festivo.getFecha(),
                festivo.getDescripcion(),
                festivo.getAmbito(),
                festivo.getAmbito().esDeEmpresa());
    }

    private CalendarAbsenceDTO toResponse(AbsenceRequest ausencia, User actor) {
        User persona = ausencia.getUsuario();
        return new CalendarAbsenceDTO(
                ausencia.getId(),
                persona.getId(),
                persona.getNombre(),
                ausencia.getFechaInicio(),
                ausencia.getFechaFin(),
                ausencia.getTipo(),
                ausencia.getEstado(),
                persona.getId() == actor.getId());
    }
}
