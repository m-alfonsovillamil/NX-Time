package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Duration;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una pausa que alguien añadió a mano después de hacerla. Tabla
 * "pausas_anadidas" (V19, ADR 015).
 *
 * <b>Es un libro, no la fuente del total.</b> El total de pausa de una
 * jornada sigue siendo {@link TimeEntry#getSegundosPausaAcumulados()}, que
 * es lo que leen los agregados. Esta fila explica una parte de ese número:
 * la que no se fichó en su momento, con su intervalo, su autor y su motivo.
 *
 * Las pausas fichadas con el botón no tienen fila aquí, y no porque falte:
 * nunca se guardó cuándo empezaron y acabaron, y rellenarlo ahora sería
 * inventarse datos en un registro de conservación obligatoria.
 */
@Entity(name = "pausas_anadidas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddedPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    /** El fichaje al que pertenece ahora: se muda si una corrección lo sustituye. */
    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    private Instant inicio;

    private Instant fin;

    private String motivo;

    @ManyToOne
    @JoinColumn(name = "creada_por_id")
    private User creadaPor;

    @Builder.Default
    private Instant creadaEn = Instant.now();

    /** Con valor si entró al aprobarse una corrección; null si fue directa. */
    @ManyToOne
    @JoinColumn(name = "solicitud_id")
    private CorrectionRequest solicitud;

    @Builder.Default
    private boolean anulada = false;

    @ManyToOne
    @JoinColumn(name = "anulada_por_id")
    private User anuladaPor;

    private Instant anuladaEn;

    public long getSegundos() {
        return Duration.between(inicio, fin).getSeconds();
    }

    /** Si se solapa con otro intervalo. Los extremos que se tocan no solapan. */
    public boolean solapaCon(Instant otroInicio, Instant otroFin) {
        return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AddedPause other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
