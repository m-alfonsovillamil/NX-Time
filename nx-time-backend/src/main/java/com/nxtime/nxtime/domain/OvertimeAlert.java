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
 * Un exceso de jornada detectado (Fase F). Tabla "avisos_horas_extra".
 *
 * <b>Es un aviso, no una imputación.</b> El proceso nocturno detecta que
 * una jornada o una semana se pasó del umbral y lo deja anotado; lo que
 * decide si esas horas son extra de verdad es la revisión humana. Un
 * exceso puede ser una jornada intensiva pactada, un turno partido mal
 * fichado o una corrección todavía sin aprobar, y en esos casos no son
 * horas extra aunque el reloj diga que sí.
 *
 * Solo lo {@link OvertimeStatus#ACEPTADO} cuenta para la bolsa anual de
 * 80 h del art. 35.2 ET.
 */
@Entity(name = "avisos_horas_extra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    /**
     * La jornada que disparó el aviso. Null en los semanales: ahí el
     * exceso no es de un fichaje concreto sino de la suma de varios, y
     * señalar a uno cualquiera sería arbitrario.
     */
    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    /**
     * El día del exceso si es {@link OvertimeType#DIARIA}; el <b>lunes</b>
     * de la semana si es {@link OvertimeType#SEMANAL}. Así una sola
     * columna identifica el periodo en los dos casos.
     */
    private LocalDate fecha;

    private int minutosExtra;

    /**
     * El listón contra el que se comparó: 540 min en los diarios, y en
     * los semanales la jornada contratada prorrateada por los días
     * hábiles reales de esa semana.
     *
     * Se guarda en vez de recalcularse al leer porque es la explicación
     * del aviso, no un dato derivado cualquiera: "esa semana tenía 4
     * días hábiles, se esperaban 30 h". Recalcularlo daría otro número
     * en cuanto se añada un festivo de convenio o se apruebe una
     * ausencia con efecto retroactivo.
     */
    private int minutosEsperados;

    @Enumerated(EnumType.STRING)
    private OvertimeType tipo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OvertimeStatus estado = OvertimeStatus.ABIERTO;

    /** Por qué no son horas extra, cuando se justifica. */
    private String justificacion;

    @ManyToOne
    @JoinColumn(name = "revisado_por")
    private User revisadoPor;

    private Instant fechaRevision;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OvertimeAlert other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
