package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio para fichar y consultar fichajes.
 *
 * `peticion.tipo` ya no es un String libre sino el enum
 * TimeEntryAction (desde la Fase 1), así que la rama "acción no
 * válida" del `when` original ya no puede darse aquí: un valor
 * inválido lo rechaza Jackson al deserializar.
 *
 * horaEntrada/horaSalida/inicioPausaActual son Instant desde la
 * Fase 3 (antes LocalDateTime): un fichaje es un instante concreto,
 * no una fecha-hora sin zona (ver TimeEntry).
 */
@Service
@Transactional(readOnly = true)
public class TimeEntryServiceImpl implements TimeEntryService {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryServiceImpl.class);

    /** Límite de filas de los listados de historial (ver auditoría: antes no había ninguno). */
    private static final int HISTORY_PAGE_SIZE = 200;

    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final TimeEntryMapper timeEntryMapper;

    public TimeEntryServiceImpl(
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            TimeEntryMapper timeEntryMapper
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
        this.timeEntryMapper = timeEntryMapper;
    }

    // Desde la Fase 3 (PostgreSQL + IDENTITY) esto SÍ es una transacción
    // normal (ver el comentario homólogo en AuthServiceImpl.registerManager
    // sobre por qué antes no lo era).
    @Override
    @Transactional
    public TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userEmail));

        TimeEntry activeEntry = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user).orElse(null);

        TimeEntry result = switch (request.tipo()) {
            case INICIO -> {
                if (activeEntry != null) {
                    throw new BusinessException("Ya hay una jornada activa.");
                }
                TimeEntry newEntry = TimeEntry.builder()
                        .usuario(user)
                        .empresa(user.getEmpresa())
                        .horaEntrada(Instant.now())
                        .build();
                yield timeEntryRepository.save(newEntry);
            }
            case FIN -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa para finalizar.");
                }
                if (activeEntry.isEnPausa()) {
                    throw new BusinessException("No se puede finalizar la jornada mientras está en pausa.");
                }
                activeEntry.setHoraSalida(Instant.now());
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_INICIO -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa para pausar.");
                }
                if (activeEntry.isEnPausa()) {
                    throw new BusinessException("La jornada ya está en pausa.");
                }
                activeEntry.setEnPausa(true);
                activeEntry.setInicioPausaActual(Instant.now());
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_FIN -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa.");
                }
                if (!activeEntry.isEnPausa()) {
                    throw new BusinessException("La jornada no está en pausa.");
                }

                Instant inicioPausa = activeEntry.getInicioPausaActual();
                if (inicioPausa == null) {
                    // Invariante interna rota (no es un caso de negocio esperable): 500 genérico.
                    throw new IllegalStateException("No se encontró el inicio de la pausa para el fichaje " + activeEntry.getId());
                }

                Instant ahora = Instant.now();
                Duration duracionPausa = Duration.between(inicioPausa, ahora);

                activeEntry.setSegundosPausaAcumulados(activeEntry.getSegundosPausaAcumulados() + duracionPausa.getSeconds());
                activeEntry.setEnPausa(false);
                activeEntry.setInicioPausaActual(null);

                yield timeEntryRepository.save(activeEntry);
            }
        };

        log.info("Fichaje {} registrado para {} (fichaje id={})", request.tipo(), userEmail, result.getId());
        return result;
    }

    @Override
    public Optional<TimeEntry> getActiveTimeEntry(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user);
    }

    @Override
    public List<TimeEntry> getHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Pageable pageable = PageRequest.of(0, HISTORY_PAGE_SIZE);
        return timeEntryRepository.findHistoryByUsuario(user, pageable);
    }

    @Override
    public List<TeamTimeEntryDTO> getTeamHistory(String managerEmail) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor no encontrado con email: " + managerEmail));

        Company company = manager.getEmpresa();
        Pageable pageable = PageRequest.of(0, HISTORY_PAGE_SIZE);
        List<TimeEntry> companyEntries = timeEntryRepository.findTeamHistory(company, pageable);

        return companyEntries.stream().map(timeEntryMapper::toTeamDTO).toList();
    }
}
