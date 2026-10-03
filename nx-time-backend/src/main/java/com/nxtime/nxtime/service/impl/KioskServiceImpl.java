package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.Canonico;
import com.nxtime.nxtime.domain.Kiosk;
import com.nxtime.nxtime.domain.KioskPairing;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.WorkStatus;
import com.nxtime.nxtime.dto.ClockProjectsResponse;
import com.nxtime.nxtime.dto.KioskDtos.ConfirmKioskRequest;
import com.nxtime.nxtime.dto.KioskDtos.KioskClockRequest;
import com.nxtime.nxtime.dto.KioskDtos.KioskClockResponse;
import com.nxtime.nxtime.dto.KioskDtos.KioskCredential;
import com.nxtime.nxtime.dto.KioskDtos.KioskIdentity;
import com.nxtime.nxtime.dto.KioskDtos.KioskInfo;
import com.nxtime.nxtime.dto.KioskDtos.KioskPerson;
import com.nxtime.nxtime.dto.KioskDtos.KioskResponse;
import com.nxtime.nxtime.dto.KioskDtos.PairingStarted;
import com.nxtime.nxtime.dto.KioskDtos.PairingState;
import com.nxtime.nxtime.dto.KioskDtos.PairingStatus;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.kiosco.IdentificacionEnKiosco;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.KioskPairingRepository;
import com.nxtime.nxtime.repository.KioskRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.KioskService;
import com.nxtime.nxtime.service.TimeEntryService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link KioskService} y el ADR 033.
 *
 * <h2>Emparejar</h2>
 * La tablet pide un código y lo enseña; el ADMIN lo teclea y con eso nace el
 * kiosco, de su empresa. La tablet pregunta por el estado con un secreto que
 * solo ella tiene, y el token se genera y se le entrega en esa respuesta, una
 * sola vez. Del código, del secreto y del token solo se guarda el SHA-256.
 *
 * <h2>Fichar</h2>
 * La tablet manda la credencial (tarjeta, o nombre y PIN) al identificar y otra
 * vez al fichar: el servidor no guarda ningún «ya identificado». Así no hay
 * estado entre las dos llamadas que caducar ni que robar, y el fichaje pasa por
 * las mismas reglas que desde la app ({@link TimeEntryService#registrarDesdeKiosco}).
 */
@Service
@Transactional(readOnly = true)
public class KioskServiceImpl implements KioskService {

    private static final Logger log = LoggerFactory.getLogger(KioskServiceImpl.class);

    static final Duration VIGENCIA_DEL_CODIGO = Duration.ofMinutes(10);

    /** Sin 0/O ni 1/I/L: se teclea mirando otra pantalla, a veces desde lejos. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LARGO_DEL_CODIGO = 8;

    private final KioskRepository kioskRepository;
    private final KioskPairingRepository pairingRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final IdentificacionEnKiosco identificacion;
    private final TimeEntryService timeEntryService;
    private final Clock clock;
    private final SecureRandom azar = new SecureRandom();

    @Autowired
    public KioskServiceImpl(
            KioskRepository kioskRepository,
            KioskPairingRepository pairingRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            IdentificacionEnKiosco identificacion,
            TimeEntryService timeEntryService) {
        this(kioskRepository, pairingRepository, companyRepository, userRepository, identificacion,
                timeEntryService, Clock.systemUTC());
    }

    KioskServiceImpl(
            KioskRepository kioskRepository,
            KioskPairingRepository pairingRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            IdentificacionEnKiosco identificacion,
            TimeEntryService timeEntryService,
            Clock clock) {
        this.kioskRepository = kioskRepository;
        this.pairingRepository = pairingRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.identificacion = identificacion;
        this.timeEntryService = timeEntryService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Emparejar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public PairingStarted iniciarEmparejamiento() {
        Instant ahora = clock.instant();
        // El barrido de los viejos va aquí y no en una tarea programada: cada
        // tarea nueva es una base de datos despierta más (la cuota de Neon).
        pairingRepository.borrarCaducadosAntesDe(ahora.minus(Duration.ofDays(1)));

        String codigo = codigoNuevo();
        String secreto = aleatorio(32);
        Instant caducaEn = ahora.plus(VIGENCIA_DEL_CODIGO);
        pairingRepository.save(KioskPairing.builder()
                .codigoHash(Canonico.sha256(codigo))
                .secretoHash(Canonico.sha256(secreto))
                .caducaEn(caducaEn)
                .build());
        return new PairingStarted(codigo, secreto, caducaEn);
    }

    @Override
    @Transactional
    public PairingStatus estadoDelEmparejamiento(String secreto) {
        KioskPairing emparejamiento = pairingRepository.findBySecretoHash(Canonico.sha256(secreto))
                .orElseThrow(() -> new ResourceNotFoundException("Ese emparejamiento no existe."));
        Kiosk kiosco = emparejamiento.getKiosco();
        if (kiosco == null) {
            return new PairingStatus(emparejamiento.caducado(clock.instant())
                    ? PairingState.CADUCADO : PairingState.PENDIENTE, null, null);
        }
        if (emparejamiento.getEntregadoEn() != null) {
            // El token se entrega una vez. Quien pregunte después con el mismo
            // secreto ya no se lo lleva: si la tablet lo perdió, se empareja otra.
            return new PairingStatus(PairingState.ENTREGADO, null, null);
        }
        String token = aleatorio(32);
        kiosco.setTokenHash(Canonico.sha256(token));
        kioskRepository.save(kiosco);
        emparejamiento.setEntregadoEn(clock.instant());
        pairingRepository.save(emparejamiento);
        log.info("Kiosco {} de la empresa {} emparejado.", kiosco.getId(), kiosco.getEmpresa().getId());
        return new PairingStatus(PairingState.LISTO, token, info(kiosco));
    }

    // ------------------------------------------------------------------
    // Gestionar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public KioskResponse confirmar(ConfirmKioskRequest request, User actor) {
        String codigo = normalizar(request.codigo());
        KioskPairing emparejamiento = pairingRepository.findPendientes(Canonico.sha256(codigo), clock.instant())
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ese código no existe o ha caducado. Pide uno nuevo en la tablet."));

        Kiosk kiosco = kioskRepository.save(Kiosk.builder()
                .empresa(companyRepository.getReferenceById(actor.getEmpresa().getId()))
                .nombre(request.nombre().trim())
                .creadoPor(actor)
                .creadoEn(clock.instant())
                .build());
        emparejamiento.setKiosco(kiosco);
        pairingRepository.save(emparejamiento);
        log.info("{} ha dado de alta el kiosco {} («{}»).", actor.getId(), kiosco.getId(), kiosco.getNombre());
        return aRespuesta(kiosco);
    }

    @Override
    public List<KioskResponse> listar(User actor) {
        return kioskRepository.findByEmpresa_IdOrderByCreadoEnDesc(actor.getEmpresa().getId()).stream()
                .map(KioskServiceImpl::aRespuesta)
                .toList();
    }

    @Override
    @Transactional
    public void revocar(long kioscoId, User actor) {
        Kiosk kiosco = kioskRepository.findById(kioscoId)
                .orElseThrow(() -> new ResourceNotFoundException("Kiosco no encontrado."));
        if (kiosco.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Ese kiosco es de otra empresa.");
        }
        if (kiosco.getRevocadoEn() == null) {
            kiosco.setRevocadoEn(clock.instant());
            kioskRepository.save(kiosco);
            log.info("{} ha revocado el kiosco {}.", actor.getId(), kioscoId);
        }
    }

    // ------------------------------------------------------------------
    // Fichar
    // ------------------------------------------------------------------

    @Override
    public KioskInfo info(Kiosk kiosco) {
        return new KioskInfo(kiosco.getNombre(), kiosco.getEmpresa().getNombre(),
                kiosco.getEmpresa().getZonaHoraria());
    }

    @Override
    public List<KioskPerson> plantilla(Kiosk kiosco) {
        return userRepository
                .findByEmpresa_IdAndActivoTrueAndKioscoPinHashIsNotNullOrderByNombreAscApellidosAsc(
                        kiosco.getEmpresa().getId())
                .stream()
                .map(p -> new KioskPerson(p.getId(), p.getNombre(), p.getApellidos()))
                .toList();
    }

    /**
     * Sin transacción propia a propósito: la identificación tiene la suya, que
     * guarda los fallos de PIN aunque rechace, y las consultas de después son
     * de solo lectura.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public KioskIdentity identificar(Kiosk kiosco, KioskCredential credencial) {
        User persona = identificacion.identificar(kiosco, credencial);
        WorkStatus estado = timeEntryService.getActiveTimeEntry(persona.getEmail())
                .map(abierta -> abierta.isEnPausa() ? WorkStatus.EN_PAUSA : WorkStatus.TRABAJANDO)
                .orElse(WorkStatus.SIN_JORNADA);
        ClockProjectsResponse proyectos = timeEntryService.proyectosParaFichar(persona.getEmail());
        return new KioskIdentity(persona.getId(), persona.getNombre(), estado,
                proyectos.disponibles(), proyectos.enCurso());
    }

    /**
     * La identificación primero, en su transacción; el fichaje después, en la
     * suya ({@link TimeEntryService#registrarDesdeKiosco}). Si fueran una sola,
     * un fichaje rechazado ("ya hay una jornada activa") desharía también el
     * contador de PIN fallidos.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public KioskClockResponse fichar(Kiosk kiosco, KioskClockRequest request) {
        User persona = identificacion.identificar(kiosco, request.credencial());
        TimeEntry fichaje = timeEntryService.registrarDesdeKiosco(
                persona, kiosco, new TimeEntryRequest(request.tipo(), request.proyectoId()));
        kioskRepository.anotarUso(kiosco.getId(), clock.instant());
        return new KioskClockResponse(persona.getNombre(), request.tipo(), instanteDe(fichaje, request));
    }

    // ------------------------------------------------------------------

    /** El instante que se acaba de apuntar, para el «entrada a las 8:02». */
    private static Instant instanteDe(TimeEntry fichaje, KioskClockRequest request) {
        return switch (request.tipo()) {
            case INICIO -> fichaje.getHoraEntrada();
            case FIN -> fichaje.getHoraSalida();
            case PAUSA_INICIO -> fichaje.getInicioPausaActual();
            case PAUSA_FIN -> Instant.now();
        };
    }

    private static KioskResponse aRespuesta(Kiosk kiosco) {
        return new KioskResponse(kiosco.getId(), kiosco.getNombre(), kiosco.getCreadoEn(), kiosco.getUltimoUso(),
                kiosco.getRevocadoEn() == null);
    }

    /** Mayúsculas y sin separadores: se admite «abcd-2345» o «ABCD 2345» igual que «ABCD2345». */
    static String normalizar(String codigo) {
        return codigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String codigoNuevo() {
        StringBuilder codigo = new StringBuilder(LARGO_DEL_CODIGO);
        for (int i = 0; i < LARGO_DEL_CODIGO; i++) {
            codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    private String aleatorio(int bytes) {
        byte[] crudo = new byte[bytes];
        azar.nextBytes(crudo);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);
    }
}
