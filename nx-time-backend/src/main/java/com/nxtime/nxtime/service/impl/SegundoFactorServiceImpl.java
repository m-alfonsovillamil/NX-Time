package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.SegundoFactor;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ActivacionSegundoFactorResponse;
import com.nxtime.nxtime.dto.SegundoFactorResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CodigoDeRecuperacionRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.SegundoFactorRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.LimitadorDeIntentosPorCuenta;
import com.nxtime.nxtime.security.SegundoFactorObligatorio;
import com.nxtime.nxtime.security.TurnoDeContrasena;
import com.nxtime.nxtime.security.totp.CodigosDeRecuperacion;
import com.nxtime.nxtime.security.totp.SecretosTotp;
import com.nxtime.nxtime.security.totp.Totp;
import com.nxtime.nxtime.service.SegundoFactorService;
import com.nxtime.nxtime.web.QrEnSvg;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ver {@link SegundoFactorService} y el ADR 051.
 *
 * Los métodos que comprueban la contraseña van sin transacción y abren la
 * suya solo para escribir, como el login ({@code AuthServiceImpl#login}): la
 * contraseña es BCrypt, y hacerlo con una conexión cogida es lo que en la
 * prueba de carga dejaba sin conexiones al resto (ADR 041).
 */
@Service
@Transactional(readOnly = true)
public class SegundoFactorServiceImpl implements SegundoFactorService {

    private static final Logger log = LoggerFactory.getLogger(SegundoFactorServiceImpl.class);

    /** El nombre con el que sale la cuenta en la aplicación de autenticación. */
    static final String EMISOR = "NX Time";

    /**
     * El límite de intentos de los códigos va con su propia clave, separado
     * del de la contraseña. Con la misma, acertar la contraseña ponía la cuenta
     * a cero, y quien la supiera podía probar códigos sin fin: entrar, cinco
     * códigos, entrar otra vez...
     */
    static final String LIMITE = "2fa:";

    static final String CODIGO_INCORRECTO = "El código no es correcto.";

    private final SegundoFactorRepository segundosFactores;
    private final CodigoDeRecuperacionRepository codigos;
    private final UserRepository usuarios;
    private final RefreshTokenRepository sesiones;
    private final SecretosTotp secretos;
    private final SegundoFactorObligatorio obligatorio;
    private final LimitadorDeIntentosPorCuenta limitador;
    private final PasswordEncoder passwordEncoder;
    private final TurnoDeContrasena turnoDeContrasena;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate escritura;
    private final Clock reloj;

    @Autowired
    public SegundoFactorServiceImpl(
            SegundoFactorRepository segundosFactores,
            CodigoDeRecuperacionRepository codigos,
            UserRepository usuarios,
            RefreshTokenRepository sesiones,
            SecretosTotp secretos,
            SegundoFactorObligatorio obligatorio,
            LimitadorDeIntentosPorCuenta limitador,
            PasswordEncoder passwordEncoder,
            TurnoDeContrasena turnoDeContrasena,
            ApplicationEventPublisher eventos,
            PlatformTransactionManager transacciones) {
        this(segundosFactores, codigos, usuarios, sesiones, secretos, obligatorio, limitador, passwordEncoder,
                turnoDeContrasena, eventos, transacciones, Clock.systemUTC());
    }

    SegundoFactorServiceImpl(
            SegundoFactorRepository segundosFactores,
            CodigoDeRecuperacionRepository codigos,
            UserRepository usuarios,
            RefreshTokenRepository sesiones,
            SecretosTotp secretos,
            SegundoFactorObligatorio obligatorio,
            LimitadorDeIntentosPorCuenta limitador,
            PasswordEncoder passwordEncoder,
            TurnoDeContrasena turnoDeContrasena,
            ApplicationEventPublisher eventos,
            PlatformTransactionManager transacciones,
            Clock reloj) {
        this.segundosFactores = segundosFactores;
        this.codigos = codigos;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.secretos = secretos;
        this.obligatorio = obligatorio;
        this.limitador = limitador;
        this.passwordEncoder = passwordEncoder;
        this.turnoDeContrasena = turnoDeContrasena;
        this.eventos = eventos;
        this.escritura = new TransactionTemplate(transacciones);
        this.reloj = reloj;
    }

    @Override
    public SegundoFactorResponse estado(User usuario) {
        Optional<SegundoFactor> suyo = activo(usuario.getId());
        Optional<SegundoFactorObligatorio.Motivo> motivo = obligatorio.motivo(usuario);
        return new SegundoFactorResponse(
                suyo.isPresent(),
                suyo.map(SegundoFactor::getConfirmadoEn).orElse(null),
                motivo.isPresent(),
                motivo.map(SegundoFactorObligatorio.Motivo::texto).orElse(null),
                suyo.isPresent() ? codigos.quedan(usuario.getId()) : 0);
    }

    @Override
    @Transactional
    public ActivacionSegundoFactorResponse iniciarActivacion(User usuario) {
        byte[] secreto = Totp.nuevoSecreto();
        // Si había una activación a medias, se sustituye: el QR anterior deja
        // de valer, y el último paso usado vuelve a estar vacío. Si ya está
        // activo, la sentencia no lo toca.
        if (segundosFactores.empezar(usuario.getId(), secretos.cifrar(usuario.getId(), secreto), reloj.instant()) == 0) {
            throw new BusinessException("Ya tienes el segundo factor activado. Para cambiar de móvil, desactívalo "
                    + "y vuelve a activarlo.", HttpStatus.CONFLICT);
        }
        String uri = Totp.uri(EMISOR, usuario.getEmail(), secreto);
        return new ActivacionSegundoFactorResponse(Totp.base32(secreto), uri,
                QrEnSvg.de(uri, "Código QR para la aplicación de autenticación"));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<String> confirmarActivacion(User usuario, String contrasena, String codigo, Optional<UUID> sesion) {
        exigirContrasena(usuario, contrasena);
        List<String> nuevos = escritura.execute(estado -> {
            SegundoFactor pendiente = segundosFactores.findById(usuario.getId())
                    .filter(fila -> !fila.confirmado())
                    .orElseThrow(() -> new BusinessException("No hay ninguna activación empezada, o ya está activo. "
                            + "Vuelve a empezar.", HttpStatus.CONFLICT));
            if (!codigoDeLaAplicacion(usuario, pendiente, codigo)) {
                throw new BusinessException(CODIGO_INCORRECTO + " Comprueba que el móvil tiene bien la hora.",
                        HttpStatus.BAD_REQUEST);
            }
            Instant ahora = reloj.instant();
            pendiente.setConfirmadoEn(ahora);
            segundosFactores.save(pendiente);
            usuarios.marcarSegundoFactor(usuario.getId(), ahora);
            // La sesión desde la que lo activa acaba de demostrar que tiene el
            // móvil: deja de estar limitada a activarlo (ver
            // SegundoFactorObligatorioFilter). El cliente renueva el access
            // token para que lo diga.
            sesion.ifPresent(familia -> sesiones.marcarConSegundoFactor(familia, usuario));
            // Dentro de la transacción: el correo sale al confirmarse, y sin
            // una no saldría (el evento es AFTER_COMMIT).
            avisar(usuario, "ACTIVADO", "Has activado el segundo factor en NX Time", Map.of());
            return codigosNuevos(usuario.getId());
        });
        log.info("Segundo factor activado: {}", usuario.getId());
        return nuevos;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void desactivar(User usuario, String contrasena, String codigo) {
        obligatorio.motivo(usuario).ifPresent(motivo -> {
            throw new BusinessException("No puedes desactivarlo: " + motivo.texto().toLowerCase(java.util.Locale.ROOT)
                    + ". Si has cambiado de móvil, usa un código de recuperación para entrar y genera otros.",
                    HttpStatus.FORBIDDEN);
        });
        exigirContrasena(usuario, contrasena);
        escritura.executeWithoutResult(estado -> {
            SegundoFactor suyo = activo(usuario.getId())
                    .orElseThrow(() -> new BusinessException("No tienes el segundo factor activado.", HttpStatus.CONFLICT));
            if (!codigoCualquiera(usuario, suyo, codigo)) {
                throw new BusinessException(CODIGO_INCORRECTO, HttpStatus.BAD_REQUEST);
            }
            borrar(usuario.getId());
            avisar(usuario, "DESACTIVADO", "Has desactivado el segundo factor en NX Time", Map.of());
        });
        log.info("Segundo factor desactivado: {}", usuario.getId());
    }

    @Override
    @Transactional
    public List<String> regenerarCodigos(User usuario, String codigo) {
        SegundoFactor suyo = activo(usuario.getId())
                .orElseThrow(() -> new BusinessException("No tienes el segundo factor activado.", HttpStatus.CONFLICT));
        if (!codigoDeLaAplicacion(usuario, suyo, codigo)) {
            throw new BusinessException(CODIGO_INCORRECTO, HttpStatus.BAD_REQUEST);
        }
        List<String> nuevos = codigosNuevos(usuario.getId());
        avisar(usuario, "CODIGOS", "Tienes códigos de recuperación nuevos en NX Time", Map.of());
        return nuevos;
    }

    @Override
    @Transactional
    public void quitar(long usuarioId, User quien) {
        User persona = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        if (persona.getEmpresa().getId() != quien.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar empleados de otra empresa.");
        }
        // Las mismas reglas que dar de baja (AuthServiceImpl#setEmployeeActive).
        if (persona.getId() == quien.getId()) {
            throw new BusinessException("El tuyo lo desactivas tú, en Ajustes.", HttpStatus.FORBIDDEN);
        }
        if (persona.getRol().ordinal() > quien.getRol().ordinal()) {
            throw new BusinessException("No puedes quitárselo a alguien con un rol por encima del tuyo.",
                    HttpStatus.FORBIDDEN);
        }
        if (segundosFactores.findById(usuarioId).isEmpty()) {
            return;
        }
        borrar(usuarioId);
        log.warn("{} ha quitado el segundo factor de {}", quien.getId(), usuarioId);
        avisar(persona, "QUITADO", "Te han quitado el segundo factor en NX Time",
                Map.of("quien", (quien.getNombre() + " " + (quien.getApellidos() == null ? "" : quien.getApellidos())).strip()));
    }

    /*
     * noRollbackFor, como el segundoPaso del login que la llama: el límite de
     * intentos lanza un 429, y sin esto la transacción de fuera queda marcada
     * para deshacerse y el 429 acababa en un 500 (lo encontró SegundoFactorIT).
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public boolean verificarAlEntrar(User usuario, String codigo) {
        Optional<SegundoFactor> suyo = activo(usuario.getId());
        if (suyo.isEmpty()) {
            return false;
        }
        return codigoCualquiera(usuario, suyo.get(), codigo);
    }

    /** La fila de quien lo tiene activo; vacío si no lo tiene o está a medias. */
    private Optional<SegundoFactor> activo(long usuarioId) {
        return segundosFactores.findById(usuarioId).filter(SegundoFactor::confirmado);
    }

    /** Un código de la aplicación o uno de recuperación, con el límite de intentos. */
    private boolean codigoCualquiera(User usuario, SegundoFactor suyo, String codigo) {
        if (!CodigosDeRecuperacion.parece(codigo)) {
            return codigoDeLaAplicacion(usuario, suyo, codigo);
        }
        limitador.comprobar(LIMITE + usuario.getEmail());
        Instant ahora = reloj.instant();
        boolean gastado = codigos.gastar(usuario.getId(), CodigosDeRecuperacion.hash(codigo), ahora) == 1;
        if (!gastado) {
            limitador.fallo(LIMITE + usuario.getEmail());
            return false;
        }
        limitador.acierto(LIMITE + usuario.getEmail());
        int quedan = codigos.quedan(usuario.getId());
        log.warn("{} ha usado un código de recuperación; le quedan {}", usuario.getId(), quedan);
        avisar(usuario, "RECUPERACION", "Se ha usado un código de recuperación en tu cuenta de NX Time",
                Map.of("quedan", quedan));
        return true;
    }

    /**
     * Un código de la aplicación, con el límite de intentos. Si vale, su paso
     * queda gastado: no vuelve a valer, ni aunque lleguen dos peticiones a la
     * vez con él ({@link SegundoFactorRepository#usarPaso}).
     */
    private boolean codigoDeLaAplicacion(User usuario, SegundoFactor suyo, String codigo) {
        limitador.comprobar(LIMITE + usuario.getEmail());
        String limpio = codigo == null ? "" : codigo.replaceAll("\\s", "");
        byte[] secreto = secretos.descifrar(usuario.getId(), suyo.getSecretoCifrado());
        OptionalLong paso = Totp.verificar(secreto, limpio, reloj.instant(), suyo.getUltimoPaso());
        boolean vale = paso.isPresent() && segundosFactores.usarPaso(usuario.getId(), paso.getAsLong()) == 1;
        if (vale) {
            limitador.acierto(LIMITE + usuario.getEmail());
        } else {
            limitador.fallo(LIMITE + usuario.getEmail());
        }
        return vale;
    }

    private List<String> codigosNuevos(long usuarioId) {
        codigos.borrarLosDe(usuarioId);
        List<String> nuevos = CodigosDeRecuperacion.nuevos();
        nuevos.forEach(codigo -> codigos.guardar(usuarioId, CodigosDeRecuperacion.hash(codigo)));
        return nuevos;
    }

    private void borrar(long usuarioId) {
        segundosFactores.findById(usuarioId).ifPresent(segundosFactores::delete);
        codigos.borrarLosDe(usuarioId);
        usuarios.marcarSegundoFactor(usuarioId, null);
    }

    /**
     * La contraseña de quien ya tiene sesión, con el límite de intentos de su
     * cuenta y por turnos, como {@code AuthServiceImpl#contrasenaCorrecta}.
     * Responde 400 y no 401: un 401 los clientes lo entienden como «tu sesión
     * ya no vale» y te sacan.
     */
    private void exigirContrasena(User usuario, String contrasena) {
        limitador.comprobar(usuario.getEmail());
        boolean correcta = turnoDeContrasena.conTurno(
                () -> passwordEncoder.matches(contrasena, usuario.getContrasena()));
        if (!correcta) {
            limitador.fallo(usuario.getEmail());
            throw new BusinessException("La contraseña no es correcta.", HttpStatus.BAD_REQUEST);
        }
        limitador.acierto(usuario.getEmail());
    }

    /** El correo de cada cambio: quien tenga la sesión de otro no puede hacerlo sin que se entere. */
    private void avisar(User usuario, String que, String asunto, Map<String, Object> extra) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nombre", usuario.getNombre());
        variables.put("email", usuario.getEmail());
        variables.put("que", que);
        variables.putAll(extra);
        eventos.publishEvent(new NotificationEvents.CorreoDirecto(usuario.getEmail(), "second-factor", asunto, variables));
    }
}
