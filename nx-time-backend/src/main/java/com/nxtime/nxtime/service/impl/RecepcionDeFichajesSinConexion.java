package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.FichajeRecibido;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.SinConexionDtos.Fichaje;
import com.nxtime.nxtime.dto.SinConexionDtos.Resultado;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.FichajeRecibidoRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recibe los fichajes que la app hizo sin conexión (ADR 054).
 *
 * <p>Cada uno va en su propia transacción, en orden: si uno se rechaza, los
 * anteriores ya aplicados se quedan, y los siguientes se intentan igual (una
 * pausa rechazada no tiene por qué impedir la salida). Cada resultado, también
 * el rechazo, se guarda en {@code fichajes_recibidos}, para contestar lo mismo
 * si la app lo vuelve a mandar.
 *
 * <p>La hora: si el móvil manda cuánto ha pasado desde el fichaje, medido con
 * {@code elapsedRealtime}, la hora es la de llegada menos eso. Ese reloj no se
 * puede cambiar a mano, así que no importa que la hora del móvil esté mal
 * puesta. Si el móvil se reinició, esa cuenta se pierde y solo queda la hora
 * del móvil, y la auditoría lo dice.
 *
 * <p>Lo que esto no puede evitar: la hora la pone el móvil. Quien manipule la
 * app podría mandar la hora que quiera. Por eso cada fichaje sin conexión queda
 * marcado en la jornada ({@code origen}) y en la auditoría con las dos horas,
 * cuándo ocurrió y cuándo llegó, y lo que llega pasadas 72 h lo tiene que dar
 * por bueno un gestor.
 */
@Service
public class RecepcionDeFichajesSinConexion {

    private static final Logger log = LoggerFactory.getLogger(RecepcionDeFichajesSinConexion.class);

    /** Lo que llega antes de esto se aplica sin más; lo de después, también, pero para revisar (decisión del 26/10). */
    public static final int HORAS_PARA_ACEPTARLO_SOLO = 72;

    /** Lo de hace más de esto ya no se aplica: un mes sin red no es un fichaje, es un reloj mal puesto. */
    static final Duration MAS_ANTIGUO_QUE_SE_ACEPTA = Duration.ofDays(31);

    /** Unos segundos de diferencia entre relojes no son un fichaje del futuro. */
    static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(2);

    private final TimeEntryService timeEntryService;
    private final UserRepository userRepository;
    private final FichajeRecibidoRepository recibidos;
    private final TransactionTemplate transaccion;
    private final Clock clock;

    @Autowired
    public RecepcionDeFichajesSinConexion(TimeEntryService timeEntryService, UserRepository userRepository,
            FichajeRecibidoRepository recibidos, TransactionTemplate transaccion) {
        this(timeEntryService, userRepository, recibidos, transaccion, Clock.systemUTC());
    }

    RecepcionDeFichajesSinConexion(TimeEntryService timeEntryService, UserRepository userRepository,
            FichajeRecibidoRepository recibidos, TransactionTemplate transaccion, Clock clock) {
        this.timeEntryService = timeEntryService;
        this.userRepository = userRepository;
        this.recibidos = recibidos;
        this.transaccion = transaccion;
        this.clock = clock;
    }

    public List<Resultado> recibir(String email, List<Fichaje> fichajes) {
        long usuarioId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"))
                .getId();
        List<Resultado> resultados = new ArrayList<>(fichajes.size());
        for (Fichaje fichaje : fichajes) {
            resultados.add(uno(usuarioId, fichaje));
        }
        return resultados;
    }

    private Resultado uno(long usuarioId, Fichaje fichaje) {
        Optional<FichajeRecibido> previo = recibidos.findByUsuarioIdAndIdCliente(usuarioId, fichaje.idCliente());
        if (previo.isPresent()) {
            return resultado(previo.get(), true, null);
        }

        Instant recibido = clock.instant();
        Instant ocurrido;
        String deDonde;
        if (fichaje.msDesdeElFichaje() != null) {
            ocurrido = recibido.minusMillis(fichaje.msDesdeElFichaje());
            deDonde = "hora medida en el móvil";
        } else {
            ocurrido = fichaje.horaDelDispositivo();
            deDonde = "hora del reloj del móvil, que se reinició";
        }

        if (ocurrido.isAfter(recibido.plus(TOLERANCIA_FUTURO))) {
            return rechazar(usuarioId, fichaje, recibido,
                    "La hora de este fichaje es posterior a ahora: revisa la fecha y la hora del móvil.");
        }
        if (ocurrido.isAfter(recibido)) {
            ocurrido = recibido;
        }
        if (Duration.between(ocurrido, recibido).compareTo(MAS_ANTIGUO_QUE_SE_ACEPTA) > 0) {
            return rechazar(usuarioId, fichaje, recibido,
                    "Este fichaje es de hace más de un mes y ya no se puede enviar. Pide una corrección.");
        }
        boolean tarde = Duration.between(ocurrido, recibido).compareTo(Duration.ofHours(HORAS_PARA_ACEPTARLO_SOLO)) > 0;

        Instant hora = ocurrido;
        try {
            FichajeRecibido guardado = transaccion.execute(estado -> {
                User persona = userRepository.findById(usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
                String motivo = "Fichado sin conexión el " + TimeEntryServiceImpl.hora(hora, persona.zona())
                        + " (" + deDonde + "), recibido el " + TimeEntryServiceImpl.hora(recibido, persona.zona());
                TimeEntry registro = timeEntryService.registrarSinConexion(persona,
                        new TimeEntryRequest(fichaje.tipo(), fichaje.proyectoId()), hora, motivo, tarde);
                return recibidos.save(FichajeRecibido.builder()
                        .usuarioId(usuarioId)
                        .idCliente(fichaje.idCliente())
                        .registroId(registro.getId())
                        .resultado(tarde ? FichajeRecibido.Resultado.A_REVISAR : FichajeRecibido.Resultado.APLICADO)
                        .motivo(tarde ? "Llegó pasadas " + HORAS_PARA_ACEPTARLO_SOLO
                                + " horas: lo tiene que dar por bueno quien aprueba las correcciones." : null)
                        .recibidoEn(recibido)
                        .build());
            });
            return resultado(guardado, false, hora);
        } catch (BusinessException e) {
            return rechazar(usuarioId, fichaje, recibido, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Lo más probable: el mismo fichaje llegó dos veces a la vez y el
            // otro envío ganó. Si no, otra regla de la base (una segunda
            // jornada abierta a la vez desde otro sitio).
            return recibidos.findByUsuarioIdAndIdCliente(usuarioId, fichaje.idCliente())
                    .map(otro -> resultado(otro, true, null))
                    .orElseGet(() -> rechazar(usuarioId, fichaje, recibido,
                            "Choca con otro fichaje que ha llegado a la vez. Revisa tu jornada."));
        }
    }

    private Resultado rechazar(long usuarioId, Fichaje fichaje, Instant recibido, String motivo) {
        log.info("Fichaje sin conexión {} del usuario {} rechazado: {}", fichaje.idCliente(), usuarioId, motivo);
        try {
            FichajeRecibido guardado = transaccion.execute(estado -> recibidos.save(FichajeRecibido.builder()
                    .usuarioId(usuarioId)
                    .idCliente(fichaje.idCliente())
                    .resultado(FichajeRecibido.Resultado.RECHAZADO)
                    .motivo(motivo.length() > 500 ? motivo.substring(0, 500) : motivo)
                    .recibidoEn(recibido)
                    .build()));
            return resultado(guardado, false, null);
        } catch (DataIntegrityViolationException e) {
            return recibidos.findByUsuarioIdAndIdCliente(usuarioId, fichaje.idCliente())
                    .map(otro -> resultado(otro, true, null))
                    .orElseThrow(() -> e);
        }
    }

    private static Resultado resultado(FichajeRecibido fila, boolean yaRecibido, Instant hora) {
        return new Resultado(fila.getIdCliente(), fila.getResultado(), yaRecibido, fila.getMotivo(),
                fila.getRegistroId(), hora);
    }
}
