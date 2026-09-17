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
 * Un tramo de una jornada trabajado en un proyecto (V23, ADR 017).
 *
 * Se abre al iniciar la jornada con proyecto y cada vez que se cambia de
 * proyecto; se cierra al cambiar otra vez o al terminar. Es el registro de lo
 * que pasó. Lo que cuenta en los informes es {@link ProjectAllocation}, que se
 * calcula a partir de los tramos o se reparte a mano.
 */
@Entity(name = "tramos_proyecto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    @ManyToOne
    @JoinColumn(name = "proyecto_id")
    private Project proyecto;

    private Instant inicio;

    /** Null mientras es el tramo en curso. */
    private Instant fin;

    /** Las pausas fichadas con el botón durante este tramo. Las añadidas a mano, no: ver V23. */
    @Builder.Default
    private long segundosPausa = 0;

    public boolean isAbierto() {
        return fin == null;
    }

    /** Duración bruta hasta {@code finSiAbierto} si el tramo sigue abierto. */
    public long segundosBrutos(Instant finSiAbierto) {
        Instant hasta = fin != null ? fin : finSiAbierto;
        return Math.max(0, Duration.between(inicio, hasta).getSeconds());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectSegment other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
