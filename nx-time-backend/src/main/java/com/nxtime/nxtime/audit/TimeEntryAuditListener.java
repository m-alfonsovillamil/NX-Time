package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Persiste cada {@link TimeEntryAuditEvent} como una fila de {@link
 * TimeEntryAudit}, con encadenamiento de hashes tipo blockchain: cada
 * fila guarda el hash SHA-256 de la fila anterior además del suyo
 * propio (sobre sus propios campos + ese hash anterior), así que
 * alterar cualquier fila ya escrita -- directamente en la base de
 * datos, saltándose la aplicación -- rompe el hash de todo lo que
 * viene después. Es un detalle adicional al permiso de solo
 * INSERT/SELECT que ya tiene el rol de la aplicación sobre esta tabla
 * (ver V3__audit_trail.sql): aquello impide escribir/borrar con las
 * credenciales normales; esto detecta una manipulación hecha con
 * otras credenciales (un superusuario, un backup restaurado a mano...).
 *
 * fase BEFORE_COMMIT (no AFTER_COMMIT ni @Async): se ejecuta dentro de
 * la MISMA transacción que el fichaje, antes de que se confirme. Si
 * escribir la auditoría falla, la transacción entera hace rollback --
 * el fichaje tampoco se guarda. Decisión consciente (ver plan, Fase 8):
 * "si no se puede auditar, no se ficha", en vez de arriesgarse a un
 * fichaje real sin traza de auditoría.
 *
 * Limitación conocida (aceptable para el alcance de este proyecto, ver
 * el comentario equivalente en LoginRateLimitFilter): el hash de la
 * fila "anterior" se calcula con una simple consulta SELECT ... ORDER
 * BY id DESC LIMIT 1, sin bloqueo. Con varias instancias de la
 * aplicación escribiendo a la vez podría haber una condición de
 * carrera al encadenar -- para una única instancia, como esta, no es
 * un problema real.
 *
 * El hash NO se calcula aquí: lo hace {@link HuellaDeAuditoria}, que es la
 * misma pieza que usa {@link VerificadorDeAuditoria} para comprobarlo. Tener
 * dos sitios donde se decide qué se firma sería tener dos verdades, y la que
 * fallara sería la de verificar -- justo la que tiene que ser de fiar.
 *
 * Ahí está explicado, además, por qué hasta septiembre de 2026 la cadena no se
 * podía comprobar: se firmaban unos nanosegundos que la base trunca y un texto
 * JSON que jsonb reescribe.
 */
@Component
public class TimeEntryAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryAuditListener.class);

    private final TimeEntryAuditRepository auditRepository;
    private final HuellaDeAuditoria huella;

    public TimeEntryAuditListener(TimeEntryAuditRepository auditRepository, HuellaDeAuditoria huella) {
        this.auditRepository = auditRepository;
        this.huella = huella;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTimeEntryAudit(TimeEntryAuditEvent event) {
        TimeEntryAudit row = event.auditRow();
        // Truncada a microsegundos: es la precisión de la columna, y firmar
        // unos nanosegundos que la base va a tirar hacía imposible recalcular
        // el hash después (ver HuellaDeAuditoria).
        row.setFechaHora(Instant.now().truncatedTo(ChronoUnit.MICROS));
        row.setIp(currentClientIp());

        String hashAnterior = auditRepository.findTopByOrderByIdDesc()
                .map(TimeEntryAudit::getHash)
                .orElse(null);
        row.setHashAnterior(hashAnterior);
        row.setVersionHash(HuellaDeAuditoria.VERSION_VERIFICABLE);
        row.setHash(huella.calcular(row, hashAnterior));

        auditRepository.save(row);
        // modificadoPor == null significa "acción automática del
        // sistema" (ver IncompleteTimeEntryScheduler), no "autor
        // desconocido": es un valor esperado, no un caso a la defensiva.
        String autor = (row.getModificadoPor() != null) ? row.getModificadoPor().getEmail() : "sistema";
        log.info("Auditoría registrada: fichaje={}, acción={}, por={}",
                row.getRegistro().getId(), row.getAccion(), autor);
    }

    // Solo hay petición HTTP real cuando esto se dispara desde un
    // controlador (el caso normal); en un test unitario del servicio
    // con el publisher mockeado, este listener ni siquiera se invoca.
    private String currentClientIp() {
        try {
            var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest().getRemoteAddr();
        } catch (IllegalStateException e) {
            return null;
        }
    }

}
