package com.nxtime.nxtime.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.service.MonthlySignatureService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * La corrección invalida la firma, en la misma transacción (Fase B3, ADR 025).
 *
 * Escucha el MISMO evento que la auditoría ({@link TimeEntryAuditEvent}) y no
 * uno por cada camino que cambia un fichaje (corregir, añadir una pausa,
 * anular...). Todo cambio de un fichaje ya pasa por la auditoría, porque la
 * auditoría es obligatoria; colgarse de ahí es la forma de que un camino nuevo
 * no se olvide de invalidar la firma.
 *
 * <b>BEFORE_COMMIT y no AFTER_COMMIT</b>: si la corrección se confirma, la
 * firma queda invalidada en la misma transacción; si no, ninguna de las dos
 * cosas. Es una garantía legal, no una cortesía: un mes corregido no puede
 * quedarse con una firma que dice lo contrario ni un instante.
 *
 * Qué se invalida lo decide {@link MonthlySignatureService#revisarTrasCambio},
 * que recalcula la huella del mes en vez de invalidar a ciegas.
 */
@Component
public class SignatureInvalidationListener {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Las acciones que se anotan en la traza SIN cambiar el fichaje, o que
     * cambian algo que no se firma (el proyecto). No hace falta ni mirar. Lo
     * que no está aquí se mira: una acción nueva se revisa por defecto.
     */
    private static final Set<AuditAction> NO_CAMBIAN_LO_FIRMADO = EnumSet.of(
            AuditAction.SOLICITUD_CORRECCION,
            AuditAction.RECHAZO_CORRECCION,
            AuditAction.DISPUTA,
            AuditAction.PROYECTO_CAMBIADO,
            AuditAction.REPARTO_PROYECTOS);

    private final MonthlySignatureService signatureService;
    private final ObjectMapper objectMapper;

    public SignatureInvalidationListener(MonthlySignatureService signatureService, ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTimeEntryAudit(TimeEntryAuditEvent event) {
        TimeEntryAudit fila = event.auditRow();
        if (NO_CAMBIAN_LO_FIRMADO.contains(fila.getAccion())) {
            return;
        }
        TimeEntry registro = fila.getRegistro();
        signatureService.revisarTrasCambio(registro, entradaAnterior(fila.getValorAnterior()), motivo(fila));
    }

    /** "Se corrigió el fichaje del 14/08/2026." Sin el texto libre de quien corrigió: se guarda para siempre. */
    static String motivo(TimeEntryAudit fila) {
        String dia = FECHA.format(fila.getRegistro().getHoraEntrada().atZone(MADRID));
        return switch (fila.getAccion()) {
            case CORRECCION, ANULACION -> "Se corrigió el fichaje del " + dia + ".";
            case PAUSA_ANADIDA -> "Se añadió una pausa al fichaje del " + dia + ".";
            case PAUSA_ANULADA -> "Se deshizo una pausa del fichaje del " + dia + ".";
            default -> "Cambió el fichaje del " + dia + ".";
        };
    }

    /**
     * La hora de entrada ANTES del cambio, de la instantánea de la auditoría.
     * Una corrección que mueve un fichaje de un mes a otro cambia los dos, y
     * el registro solo trae ya la hora nueva. Si no se puede leer, null: se
     * mira solo el mes de la hora nueva.
     */
    Instant entradaAnterior(String valorAnterior) {
        if (valorAnterior == null || valorAnterior.isBlank()) {
            return null;
        }
        try {
            JsonNode entrada = objectMapper.readTree(valorAnterior).get("horaEntrada");
            if (entrada == null || entrada.isNull()) {
                return null;
            }
            if (entrada.isNumber()) {
                // Segundos con decimales, si el mapper escribe las fechas como número.
                BigDecimal segundos = entrada.decimalValue();
                return Instant.ofEpochSecond(segundos.longValue(),
                        segundos.remainder(BigDecimal.ONE).movePointRight(9).longValue());
            }
            return Instant.parse(entrada.asText());
        } catch (Exception ilegible) {
            return null;
        }
    }
}
