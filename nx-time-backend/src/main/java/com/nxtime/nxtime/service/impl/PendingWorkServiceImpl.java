package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.OvertimeStatus;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PendingWorkResponse;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.OvertimeService;
import com.nxtime.nxtime.service.PendingWorkService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuenta llamando a <b>los mismos métodos que llenan cada bandeja</b>, y no
 * con consultas {@code COUNT} propias.
 *
 * Es menos eficiente, y es a propósito. Qué correcciones "te tocan" lo decide
 * {@code CorrectionServiceImpl.puedeResolver}, y qué horas extra se revisan lo
 * decide {@code OvertimeServiceImpl.delEquipo}, que quita las propias. Un
 * {@code COUNT} en SQL obligaría a copiar esas reglas, y la primera vez que
 * una cambiara sin la otra el panel diría 3 y la bandeja enseñaría 2. El
 * volumen lo permite: son las solicitudes vivas, no el histórico.
 */
@Service
@Transactional(readOnly = true)
public class PendingWorkServiceImpl implements PendingWorkService {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final AbsenceService absenceService;
    private final CorrectionService correctionService;
    private final OvertimeService overtimeService;
    private final Clock clock;

    @Autowired
    public PendingWorkServiceImpl(
            AbsenceService absenceService,
            CorrectionService correctionService,
            OvertimeService overtimeService) {
        this(absenceService, correctionService, overtimeService, Clock.systemUTC());
    }

    PendingWorkServiceImpl(
            AbsenceService absenceService,
            CorrectionService correctionService,
            OvertimeService overtimeService,
            Clock clock) {
        this.absenceService = absenceService;
        this.correctionService = correctionService;
        this.overtimeService = overtimeService;
        this.clock = clock;
    }

    @Override
    public PendingWorkResponse contar(User actor) {
        Set<String> authorities = RoleAuthorities.forRole(actor.getRol());

        // Sin la authority de una bandeja, 0 y no un 403: el panel pide los
        // tres de una vez, y un GESTOR sin alguna no debe quedarse sin los otros.
        int ausencias = authorities.contains("ausencia:aprobar")
                ? absenceService.getPendingRequests(actor.getEmail()).size()
                : 0;

        int correcciones = correctionService.pendientesParaMi(actor).size();

        // El mismo año que la bandeja cuando no se le pasa ninguno (ver
        // OvertimeController.anioOEsteAnio), y solo los ABIERTO: los ya
        // aceptados o justificados no esperan nada.
        int horasExtra = authorities.contains("horasextra:revisar")
                ? (int) overtimeService.delEquipo(actor, LocalDate.now(clock.withZone(MADRID)).getYear()).stream()
                        .filter(aviso -> OvertimeStatus.ABIERTO.name().equals(aviso.estado()))
                        .count()
                : 0;

        return new PendingWorkResponse(ausencias, correcciones, horasExtra);
    }
}
