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
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una oferta interna (Fase H). Tabla "ofertas_internas".
 *
 * Es la vacante tal como la ve la plantilla: quien la publica, a qué
 * departamento pertenece y hasta cuándo se puede optar.
 */
@Entity(name = "ofertas_internas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosting {

    /**
     * El día es el ESPAÑOL, como en las jornadas (fase D) y en los
     * plazos de las denuncias (fase G). Una oferta que cierra el 30 de
     * junio admite candidaturas hasta las 23:59 de aquí, no hasta las
     * 02:00 del día siguiente que sería el corte en UTC.
     */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    private String titulo;

    private String descripcion;

    private String puesto;

    /** Opcional: no toda empresa tiene departamentos dados de alta. */
    @ManyToOne
    @JoinColumn(name = "departamento_id")
    private Department departamento;

    @ManyToOne
    @JoinColumn(name = "publicada_por")
    private User publicadaPor;

    /** Null mientras está en BORRADOR. */
    private Instant fechaPublicacion;

    /** Opcional: hay vacantes que se cierran cuando aparece la persona. */
    private LocalDate fechaCierre;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private JobPostingStatus estado = JobPostingStatus.BORRADOR;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Version
    private long version;

    /**
     * Si hoy se puede optar a ella.
     *
     * Son <b>dos</b> condiciones y no una: publicada Y en plazo. Vive
     * aquí y no repartida por el servicio para que no haya dos sitios
     * donde decidirlo — es la comprobación que hace la pantalla para
     * habilitar el botón y la que hace el servidor antes de aceptar la
     * candidatura, y si se desincronizaran la app ofrecería algo que
     * acaba en error.
     */
    public boolean admiteCandidaturas() {
        return estado.estaPublicada() && !plazoVencido();
    }

    /** Si tenía fecha de cierre y ya pasó. Sin fecha, nunca vence. */
    public boolean plazoVencido() {
        return fechaCierre != null && LocalDate.now(MADRID).isAfter(fechaCierre);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobPosting other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
