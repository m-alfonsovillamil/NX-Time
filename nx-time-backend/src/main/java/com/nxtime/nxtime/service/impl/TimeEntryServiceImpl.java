package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Migración 1:1 de ServicioFichajeImpl.kt. El único cambio de
 * comportamiento intencionado es que `peticion.tipo` ya no es un String
 * libre sino el enum TimeEntryAction (ver TimeEntryRequest), así que la
 * rama "acción no válida" del `when` original ya no puede darse aquí: un
 * valor inválido lo rechaza Jackson al deserializar, antes de llegar a
 * este servicio.
 */
@Service
public class TimeEntryServiceImpl implements TimeEntryService {

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

    @Override
    public TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con email: " + userEmail));

        TimeEntry activeEntry = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user).orElse(null);

        return switch (request.tipo()) {
            case INICIO -> {
                if (activeEntry != null) {
                    throw new IllegalStateException("Ya hay una jornada activa.");
                }
                TimeEntry newEntry = TimeEntry.builder()
                        .usuario(user)
                        .horaEntrada(LocalDateTime.now())
                        .build();
                yield timeEntryRepository.save(newEntry);
            }
            case FIN -> {
                if (activeEntry == null) {
                    throw new IllegalStateException("No hay jornada activa para finalizar.");
                }
                if (activeEntry.isEnPausa()) {
                    throw new IllegalStateException("No se puede finalizar la jornada mientras está en pausa.");
                }
                activeEntry.setHoraSalida(LocalDateTime.now());
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_INICIO -> {
                if (activeEntry == null) {
                    throw new IllegalStateException("No hay jornada activa para pausar.");
                }
                if (activeEntry.isEnPausa()) {
                    throw new IllegalStateException("La jornada ya está en pausa.");
                }
                activeEntry.setEnPausa(true);
                activeEntry.setInicioPausaActual(LocalDateTime.now());
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_FIN -> {
                if (activeEntry == null) {
                    throw new IllegalStateException("No hay jornada activa.");
                }
                if (!activeEntry.isEnPausa()) {
                    throw new IllegalStateException("La jornada no está en pausa.");
                }

                LocalDateTime inicioPausa = activeEntry.getInicioPausaActual();
                if (inicioPausa == null) {
                    throw new IllegalStateException("Error: No se encontró el inicio de la pausa.");
                }

                LocalDateTime ahora = LocalDateTime.now();
                Duration duracionPausa = Duration.between(inicioPausa, ahora);

                activeEntry.setMinutosPausaAcumulados(activeEntry.getMinutosPausaAcumulados() + duracionPausa.toMinutes());
                activeEntry.setEnPausa(false);
                activeEntry.setInicioPausaActual(null);

                yield timeEntryRepository.save(activeEntry);
            }
        };
    }

    @Override
    public Optional<TimeEntry> getActiveTimeEntry(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user);
    }

    @Override
    public List<TimeEntry> getHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return timeEntryRepository.findByUsuarioOrderByHoraEntradaDesc(user);
    }

    @Override
    public List<TeamTimeEntryDTO> getTeamHistory(String managerEmail) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new EntityNotFoundException("Gestor no encontrado con email: " + managerEmail));

        Company company = manager.getEmpresa();
        List<User> companyUsers = userRepository.findByEmpresa(company);
        List<TimeEntry> companyEntries = timeEntryRepository.findByUsuarioInOrderByHoraEntradaDesc(companyUsers);

        return companyEntries.stream().map(timeEntryMapper::toTeamDTO).toList();
    }
}
