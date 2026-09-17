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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una petición de borrar los datos personales de alguien (RGPD, art. 17).
 * Tabla "solicitudes_borrado" (V20, ADR 016).
 *
 * <b>No se borra nunca</b>, ni siquiera cuando ya se ha anonimizado a la
 * persona: es la prueba de que el derecho se ejerció y se atendió.
 */
@Entity(name = "solicitudes_borrado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeletionStatus estado = DeletionStatus.PENDIENTE;

    /**
     * Si la registró RRHH/ADMIN en nombre de la persona (V21), quién. Null si
     * la pidió ella desde la aplicación. En ese caso {@link #motivo} dice cómo
     * llegó la petición.
     */
    @ManyToOne
    @JoinColumn(name = "registrada_por_id")
    private User registradaPor;

    private String motivo;

    @Builder.Default
    private Instant creadaEn = Instant.now();

    @ManyToOne
    @JoinColumn(name = "resuelta_por_id")
    private User resueltaPor;

    private Instant resueltaEn;

    private String comentarioResolucion;

    private LocalDate anonimizarDesde;

    private Instant anonimizadaEn;

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeletionRequest other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
