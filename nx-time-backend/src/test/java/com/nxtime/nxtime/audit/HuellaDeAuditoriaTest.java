package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El hash de una fila de auditoría no puede cambiar nunca.
 *
 * Los valores de aquí se calcularon con el código de antes de extraer
 * {@link Canonico} (Fase B3) y se copiaron tal cual. Si una refactorización
 * los cambia, TODA la auditoría histórica pasa a «no comprobable»: la cadena
 * se escribió con la definición de antes, y el verificador recalcularía con la
 * de ahora. Por eso son literales y no se recalculan en el test.
 */
class HuellaDeAuditoriaTest {

    private final HuellaDeAuditoria huella = new HuellaDeAuditoria();

    private static TimeEntryAudit fila(User modificadoPor, String anterior, String nuevo) {
        return TimeEntryAudit.builder()
                .registro(TimeEntry.builder().id(42L).build())
                .usuario(User.builder().id(7L).build())
                .modificadoPor(modificadoPor)
                .accion(AuditAction.CORRECCION)
                .valorAnterior(anterior)
                .valorNuevo(nuevo)
                .motivo("Olvidé fichar la salida")
                .fechaHora(Instant.parse("2026-08-14T15:30:12.123456789Z"))
                .build();
    }

    @Test
    @DisplayName("Una corrección con autor y JSON desordenado da exactamente el hash de siempre")
    void conAutor() {
        TimeEntryAudit fila = fila(User.builder().id(3L).build(),
                "{\"horaSalida\": null, \"horaEntrada\": \"2026-08-14T07:00:00Z\", \"id\": 42}",
                "{\"id\":42,\"horaEntrada\":\"2026-08-14T07:00:00Z\",\"horaSalida\":\"2026-08-14T15:00:00Z\"}");

        assertThat(huella.calcular(fila, "a".repeat(64))).isEqualTo(DORADO_CON_AUTOR);
    }

    @Test
    @DisplayName("Una acción del sistema, sin JSON y la primera de la cadena, también")
    void delSistema() {
        TimeEntryAudit fila = fila(null, null, "   ");

        assertThat(huella.calcular(fila, null)).isEqualTo(DORADO_DEL_SISTEMA);
    }

    private static final String DORADO_CON_AUTOR = "95705daa2ea2ea742681d2c5014464ebe43492b26d7fdc08ade1ccb14383ddc6";
    private static final String DORADO_DEL_SISTEMA = "9fb078471e72af56edb660d7cbc76eafd57a3c4b848a184994beb043b980dcb4";
}
