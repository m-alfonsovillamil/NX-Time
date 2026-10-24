package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.AsyncConfig;
import com.nxtime.nxtime.domain.AccessCodeType;
import com.nxtime.nxtime.domain.InvitacionPendiente;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.EstadoInvitacionesResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AccessCodeRepository;
import com.nxtime.nxtime.repository.InvitacionPendienteRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.service.InvitacionesService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ver {@link InvitacionesService} y el ADR 052.
 *
 * <b>El cupo de hoy</b> es lo menor de dos cosas, contando los códigos de alta
 * de las últimas 24 horas:
 * <ul>
 *   <li>las 50 altas al día de cada empresa (ADR 034), que ya frenaban a quien
 *       usara una cuenta de ADMIN para mandar correo a granel;</li>
 *   <li>el tope de la instalación entera ({@code application.mail.altas-por-dia}),
 *       por debajo del que corta el proveedor de correo.</li>
 * </ul>
 *
 * <b>Cada invitación sale en su propia transacción</b>, con el mismo
 * {@link AccessCodeService#emitirCodigoDeAlta} que un alta normal: si el correo
 * no sale, esa sola se queda en la cola con el error, y no se deshace ninguna
 * cuenta. Si el fallo es del proveedor o del cupo, la tanda para: insistir no
 * arreglaría nada.
 *
 * Las tandas en marcha se apuntan en memoria: con una sola instancia basta
 * (README, «pensado para una sola instancia»).
 */
@Service
@Transactional(readOnly = true)
public class InvitacionesServiceImpl implements InvitacionesService {

    private static final Logger log = LoggerFactory.getLogger(InvitacionesServiceImpl.class);

    private final InvitacionPendienteRepository pendientes;
    private final AccessCodeRepository codigos;
    private final UserRepository usuarios;
    private final AccessCodeService accessCodeService;
    private final Executor ejecutor;
    private final TransactionTemplate cadaUna;
    private final int altasPorDiaEnLaInstalacion;
    private final Clock reloj;

    /** Empresas con una tanda en marcha: no se empieza otra hasta que acabe. */
    private final Set<Long> enMarcha = ConcurrentHashMap.newKeySet();

    @Autowired
    public InvitacionesServiceImpl(
            InvitacionPendienteRepository pendientes,
            AccessCodeRepository codigos,
            UserRepository usuarios,
            AccessCodeService accessCodeService,
            @Qualifier(AsyncConfig.EMAIL_EXECUTOR) Executor ejecutor,
            PlatformTransactionManager transacciones,
            @Value("${application.mail.altas-por-dia:250}") int altasPorDiaEnLaInstalacion) {
        this(pendientes, codigos, usuarios, accessCodeService, ejecutor, transacciones, altasPorDiaEnLaInstalacion,
                Clock.systemUTC());
    }

    InvitacionesServiceImpl(
            InvitacionPendienteRepository pendientes,
            AccessCodeRepository codigos,
            UserRepository usuarios,
            AccessCodeService accessCodeService,
            Executor ejecutor,
            PlatformTransactionManager transacciones,
            int altasPorDiaEnLaInstalacion,
            Clock reloj) {
        this.pendientes = pendientes;
        this.codigos = codigos;
        this.usuarios = usuarios;
        this.accessCodeService = accessCodeService;
        this.ejecutor = ejecutor;
        this.cadaUna = new TransactionTemplate(transacciones);
        this.cadaUna.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.altasPorDiaEnLaInstalacion = altasPorDiaEnLaInstalacion;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void encolar(long empresaId, Collection<Long> nuevos) {
        Instant ahora = reloj.instant();
        for (Long usuarioId : nuevos) {
            pendientes.save(InvitacionPendiente.builder().usuarioId(usuarioId).empresaId(empresaId).creadaEn(ahora).build());
        }
    }

    @Override
    public EstadoInvitacionesResponse estado(User actor) {
        long empresaId = actor.getEmpresa().getId();
        return new EstadoInvitacionesResponse(pendientes.countByEmpresaId(empresaId), cupoDeHoy(empresaId), 0);
    }

    @Override
    public EstadoInvitacionesResponse enviarPendientes(User actor) {
        long empresaId = actor.getEmpresa().getId();
        long quedan = pendientes.countByEmpresaId(empresaId);
        int cupo = cupoDeHoy(empresaId);
        int tanda = (int) Math.min(quedan, cupo);
        if (tanda == 0 || !enMarcha.add(empresaId)) {
            return new EstadoInvitacionesResponse(quedan, cupo, 0);
        }
        List<Long> ids = pendientes.findByEmpresaIdOrderByCreadaEnAscUsuarioIdAsc(empresaId, PageRequest.of(0, tanda))
                .stream().map(InvitacionPendiente::getUsuarioId).toList();
        log.info("Empresa {}: mandando {} invitaciones de alta ({} pendientes, cupo {}).", empresaId, ids.size(),
                quedan, cupo);
        try {
            ejecutor.execute(() -> mandar(empresaId, ids));
        } catch (RuntimeException e) {
            enMarcha.remove(empresaId);
            throw e;
        }
        return new EstadoInvitacionesResponse(quedan, cupo, ids.size());
    }

    /** Lo que puede salir todavía hoy para esta empresa. */
    int cupoDeHoy(long empresaId) {
        Instant haceUnDia = reloj.instant().minus(Duration.ofDays(1));
        long deLaEmpresa = codigos.countByUsuario_Empresa_IdAndTipoAndCreadoEnAfter(empresaId, AccessCodeType.ALTA,
                haceUnDia);
        long deTodas = codigos.countByTipoAndCreadoEnAfter(AccessCodeType.ALTA, haceUnDia);
        long cupo = Math.min(AccessCodeServiceImpl.ALTAS_POR_EMPRESA_Y_DIA - deLaEmpresa,
                altasPorDiaEnLaInstalacion - deTodas);
        return (int) Math.max(0, cupo);
    }

    /** Cómo ha ido una invitación. */
    enum Resultado { MANDADA, YA_NO_HACE_FALTA }

    /** La tanda, en segundo plano. Cada una en su transacción. */
    void mandar(long empresaId, List<Long> ids) {
        int mandadas = 0;
        try {
            for (Long usuarioId : ids) {
                try {
                    if (cadaUna.execute(estado -> mandarUna(usuarioId)) == Resultado.MANDADA) {
                        mandadas++;
                    }
                } catch (BusinessException e) {
                    // Sin cupo o sin correo (ver la clase): se apunta y la tanda para.
                    apuntarFallo(usuarioId, e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    log.error("Invitación de alta de {}: error inesperado", usuarioId, e);
                    apuntarFallo(usuarioId, "Error inesperado al mandarla.");
                    break;
                }
            }
        } finally {
            enMarcha.remove(empresaId);
            log.info("Empresa {}: {} invitaciones de alta mandadas.", empresaId, mandadas);
        }
    }

    /**
     * Manda una. Si falla, lanza: la transacción se deshace entera (ni código
     * ni cola tocada) y quien llama apunta el fallo en otra.
     */
    private Resultado mandarUna(long usuarioId) {
        InvitacionPendiente invitacion = pendientes.findById(usuarioId).orElse(null);
        if (invitacion == null) {
            return Resultado.YA_NO_HACE_FALTA;
        }
        User usuario = usuarios.findById(usuarioId).orElse(null);
        if (usuario == null || !usuario.isActivo()) {
            // Dado de baja antes de que le llegara: ya no hay a quién invitar.
            pendientes.delete(invitacion);
            return Resultado.YA_NO_HACE_FALTA;
        }
        accessCodeService.emitirCodigoDeAlta(usuario, usuario.getEmpresa().getNombre());
        pendientes.delete(invitacion);
        return Resultado.MANDADA;
    }

    private void apuntarFallo(long usuarioId, String motivo) {
        // En una transacción aparte: la de la invitación se ha deshecho.
        cadaUna.executeWithoutResult(estado -> pendientes.findById(usuarioId).ifPresent(fila -> {
            fila.setIntentos(fila.getIntentos() + 1);
            fila.setUltimoIntento(reloj.instant());
            fila.setUltimoError(motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 200)));
            pendientes.save(fila);
        }));
        log.warn("Invitación de alta de {} sin mandar: {}", usuarioId, motivo);
    }
}
