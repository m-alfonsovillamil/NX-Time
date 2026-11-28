package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.config.AsyncConfig;
import com.nxtime.nxtime.config.CondicionesDelServicio;
import com.nxtime.nxtime.repository.PushDeviceRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte cada aviso publicado en un push (Fase B5, ADR 028).
 *
 * <b>Cuelga del aviso, no de cada evento de negocio.</b> {@code NotificationListener}
 * tiene una veintena de métodos que publican aviso y correo; añadir una tercera
 * llamada a cada uno garantizaría que el próximo evento se olvidara de una. Todo
 * aviso pasa por {@code NoticeServiceImpl.publicar}, que al guardarlo publica
 * {@link NotificationEvents.NoticePublished}, y esto lo escucha. Así, todo aviso
 * futuro es también un push sin tocar nada; y lo que hoy es solo correo a
 * propósito (el borrado ya ejecutado) sigue sin push, que es lo correcto.
 *
 * <p>Mismo contrato que {@link EmailSender#enviar}: <b>como mucho una vez, y
 * sus fallos no suben</b>. El aviso ya está guardado y el correo ya salió; que
 * Google no conteste no puede convertirse en un error de nadie.
 *
 * <p>Sin credenciales de Firebase no existe {@link PushGateway} y esto no hace
 * nada: en local, en los tests y en cualquier despliegue sin la variable, la
 * aplicación funciona igual y solo le falta el push. El mismo criterio que
 * Sentry sin DSN.
 */
@Component
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final PushDeviceRepository deviceRepository;
    private final ObjectProvider<PushGateway> gateway;

    /** Lo que ha elegido cada persona (ADR 059). Null en los tests que no lo usan: todo llega. */
    private final com.nxtime.nxtime.service.PreferenciasDeAvisosService preferencias;

    /** El título de todos los push: el nombre del producto. */
    private final String titulo;

    @org.springframework.beans.factory.annotation.Autowired
    public PushSender(PushDeviceRepository deviceRepository, ObjectProvider<PushGateway> gateway,
            com.nxtime.nxtime.service.PreferenciasDeAvisosService preferencias, CondicionesDelServicio servicio) {
        this(deviceRepository, gateway, preferencias, servicio.producto());
    }

    /** Para los tests que no miran las preferencias: todo llega. */
    PushSender(PushDeviceRepository deviceRepository, ObjectProvider<PushGateway> gateway, String titulo) {
        this(deviceRepository, gateway, null, titulo);
    }

    PushSender(PushDeviceRepository deviceRepository, ObjectProvider<PushGateway> gateway,
            com.nxtime.nxtime.service.PreferenciasDeAvisosService preferencias, String titulo) {
        this.deviceRepository = deviceRepository;
        this.gateway = gateway;
        this.preferencias = preferencias;
        this.titulo = titulo;
    }

    /**
     * AFTER_COMMIT del aviso: si el INSERT del aviso no se confirma, no hay
     * push de algo que no existe. Y {@code @Async} aunque hoy {@code publicar}
     * ya corra en el ejecutor de correo: esperar a Google no puede acabar
     * nunca en el hilo de una petición.
     */
    @Async(AsyncConfig.EMAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticePublished(NotificationEvents.NoticePublished evento) {
        enviar(evento);
    }

    void enviar(NotificationEvents.NoticePublished evento) {
        PushGateway salida = gateway.getIfAvailable();
        if (salida == null) {
            return;
        }
        // Quien ha apagado el push de esa categoría lo sigue teniendo en la
        // campana (ADR 059): el aviso ya está guardado, solo no se manda al móvil.
        if (preferencias != null && !preferencias.quierePush(evento.destinatarioId(), evento.tipo())) {
            return;
        }
        try {
            List<String> tokens = deviceRepository.findTokensDeUsuarioActivo(evento.destinatarioId());
            if (tokens.isEmpty()) {
                return;
            }
            List<String> muertos = salida.enviar(tokens, Map.of(
                    "tipo", evento.tipo().name(),
                    "titulo", titulo,
                    "cuerpo", TextoDePush.de(evento.tipo()),
                    "ruta", evento.rutaDestino() == null ? "" : evento.rutaDestino()));
            if (!muertos.isEmpty()) {
                // Sin esta limpieza la tabla se llenaría de móviles que ya no
                // existen, y cada aviso intentaría llegar a todos ellos.
                deviceRepository.deleteByTokenIn(muertos);
                log.info("Borrados {} tokens de push que ya no valen (usuario {}).",
                        muertos.size(), evento.destinatarioId());
            }
            log.debug("Push {} enviado al usuario {} ({} dispositivos).",
                    evento.tipo(), evento.destinatarioId(), tokens.size());
        } catch (RuntimeException e) {
            // A propósito no se relanza: ver el Javadoc de la clase.
            log.error("No se pudo enviar el push {} al usuario {}: {}",
                    evento.tipo(), evento.destinatarioId(), e.getMessage());
        }
    }
}
