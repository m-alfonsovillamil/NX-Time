package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Petición de cambiar las horas de un fichaje ya cerrado (Fase E). Tabla
 * "solicitudes_correccion".
 *
 * <b>Ninguna corrección se aplica sola.</b> Antes de esta fase, un RRHH
 * cambiaba las horas de cualquier fichaje en el acto y su dueño se
 * enteraba, como mucho, mirando la auditoría. Eso vacía de contenido lo
 * que la auditoría inmutable (V5) pretende garantizar: de poco sirve que
 * la traza no se pueda alterar si el dato que traza se puede cambiar sin
 * que el interesado lo sepa. Ver ADR 010.
 *
 * <b>Quién resuelve depende de quién pide</b>, y esa es la regla que
 * ordena toda la fase (la calcula {@code CorrectionServiceImpl}):
 * <ul>
 *   <li>Si la pide el dueño del fichaje → la aprueba alguien con
 *       {@code correccion:aprobar}.</li>
 *   <li>Si la pide otra persona → la aprueba <b>el dueño</b>, que es a
 *       quien le cambian sus horas. Y si no está de acuerdo, no la
 *       rechaza: la <b>disputa</b>, y decide RRHH.</li>
 *   <li>Si la pide el dueño Y tiene {@code correccion:aprobar}, se
 *       auto-aprueba. No es un atajo: sin eso, un ADMIN sin superior no
 *       podría corregir nunca su propio fichaje. Queda anotado como tal
 *       en la traza, que es lo que lo hace aceptable.</li>
 * </ul>
 *
 * Las horas son {@link Instant} como en {@link TimeEntry}: un fichaje es
 * un instante concreto (ADR 002).
 */
@Entity(name = "solicitudes_correccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** Denormalizado para el filtro de tenant, como en el resto (ADR 006). */
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    /** El fichaje que se quiere corregir. No se toca hasta la aprobación. */
    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    @ManyToOne
    @JoinColumn(name = "solicitante_id")
    private User solicitante;

    private Instant horaEntradaPropuesta;

    private Instant horaSalidaPropuesta;

    private String motivo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CorrectionStatus estado = CorrectionStatus.PENDIENTE;

    @ManyToOne
    @JoinColumn(name = "aprobador_id")
    private User aprobador;

    private Instant fechaResolucion;

    private String comentarioResolucion;

    /** Por qué el dueño no acepta la corrección. Solo con EN_DISPUTA. */
    private String motivoDisputa;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Version
    private long version;

    /** Quién es el dueño del fichaje, que no tiene por qué ser el solicitante. */
    public User getDuenoDelFichaje() {
        return registro.getUsuario();
    }

    /** Si la pidió el propio dueño del fichaje. */
    public boolean laPidioElDueno() {
        return solicitante.getId() == getDuenoDelFichaje().getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CorrectionRequest other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
