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
 * Una candidatura a una oferta interna (Fase H). Tabla "candidaturas".
 *
 * <b>Lo que define esta clase es {@link #cv}.</b> Apunta al adjunto
 * concreto que se presentó, no a "el CV de esta persona": si apuntara al
 * usuario, subir una versión nueva en marzo cambiaría lo que el gestor
 * leyó en enero, y el expediente dejaría de ser un expediente.
 *
 * De ahí sale la deuda que esta fase salda: hasta ahora subir un CV
 * borraba el anterior, así que a partir de aquí un CV referenciado
 * <b>deja de estar vigente pero no se destruye</b> (ver
 * {@code Attachment.vigente} y la V14).
 */
@Entity(name = "candidaturas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "oferta_id")
    private JobPosting oferta;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    /** El CV <b>tal como estaba</b> al presentarse. Nunca se sustituye. */
    @ManyToOne
    @JoinColumn(name = "adjunto_cv_id")
    private Attachment cv;

    /** Opcional: por qué se presenta. */
    private String carta;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicationStatus estado = ApplicationStatus.RECIBIDA;

    @ManyToOne
    @JoinColumn(name = "resuelta_por")
    private User resueltaPor;

    private Instant fechaResolucion;

    /** Lo que se le dice al candidato. Obligatorio al descartar. */
    private String comentario;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobApplication other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
