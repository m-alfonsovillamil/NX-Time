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
 * Solicitud de ausencia (vacaciones, baja médica...). Tabla
 * "peticiones_ausencia".
 *
 * fechaInicio/fechaFin se quedan como LocalDate a propósito, y no pasan
 * a Instant como TimeEntry: un día de vacaciones es una fecha de
 * calendario (sin hora ni zona), no un instante concreto -- "el 24 de
 * diciembre" significa lo mismo en cualquier zona horaria.
 *
 * IDs con GenerationType.IDENTITY desde la Fase 3 (ver Company.java).
 * Se añade "empresa" (denormalizado desde usuario.empresa) y "version"
 * (bloqueo optimista) -- ver el esquema V1__initial_schema.sql.
 */
@Entity(name = "peticiones_ausencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbsenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    private AbsenceType tipo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AbsenceStatus estado = AbsenceStatus.PENDIENTE;

    private String motivo;

    // Trazabilidad de la resolución (Fase 9): antes no quedaba
    // constancia de quién aprobó/rechazó ni cuándo -- la petición
    // simplemente cambiaba de estado (ver auditoría del plan). Los tres
    // campos van juntos: o los tres con valor (petición resuelta) o los
    // tres vacíos (PENDIENTE), y la BD lo comprueba
    // (ck_peticiones_resolucion_coherente).
    @ManyToOne
    @JoinColumn(name = "aprobado_por_id")
    private User aprobadoPor;

    private Instant fechaResolucion;

    private String comentarioResolucion;

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbsenceRequest other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
