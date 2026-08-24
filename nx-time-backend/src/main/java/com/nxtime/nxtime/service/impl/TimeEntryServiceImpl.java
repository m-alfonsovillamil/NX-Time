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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio para fichar y consultar fichajes.
 *
 * `peticion.tipo` ya no es un String libre sino el enum
 * TimeEntryAction (desde la Fase 1), así que la rama "acción no
 * válida" del `when` original ya no puede darse aquí: un valor
 * inválido lo rechaza Jackson al deserializar.
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

    // Sin transacción: la rama INICIO inserta un TimeEntry nuevo con
    // GenerationType.TABLE, y eso se bloquea contra la conexión aislada
    // del propio generador de IDs si el método entero va envuelto en
    // una transacción de Spring (ver el comentario detallado en
    // AuthServiceImpl.registerManager -- mismo problema de SQLite).
    // Las otras 3 ramas (FIN/PAUSA_INICIO/PAUSA_FIN) solo actualizan un
    // TimeEntry ya existente y no les afectaría, pero el método es una
    // sola unidad y comparte propagación.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
                        .horaEntrada(LocalDateTime.now())
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
                activeEntry.setHoraSalida(LocalDateTime.now());
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
                activeEntry.setInicioPausaActual(LocalDateTime.now());
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_FIN -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa.");
                }
                if (!activeEntry.isEnPausa()) {
                    throw new BusinessException("La jornada no está en pausa.");
                }

                LocalDateTime inicioPausa = activeEntry.getInicioPausaActual();
                if (inicioPausa == null) {
                    // Invariante interna rota (no es un caso de negocio esperable): 500 genérico.
                    throw new IllegalStateException("No se encontró el inicio de la pausa para el fichaje " + activeEntry.getId());
                }

                LocalDateTime ahora = LocalDateTime.now();
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
