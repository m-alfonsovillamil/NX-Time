package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
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
 * Una denuncia del canal interno de información (Fase G). Tabla
 * "denuncias".
 *
 * El canal lo obliga la <b>Ley 2/2023</b> para empresas de 50 o más
 * empleados, y de ella salen las tres reglas que gobiernan esta clase:
 * anonimato opcional y efectivo, acuse de recibo en 7 días y respuesta
 * en 3 meses.
 *
 * <b>Lo que no se guarda aquí es tan importante como lo que sí.</b> Si
 * {@link #denunciante} es null la denuncia es anónima y no hay ninguna
 * otra columna de la que salga la identidad: ni en los mensajes, ni en
 * la auditoría, ni en el aviso que recibe quien instruye. La única
 * llave que queda es el código de seguimiento, y de él solo se guarda
 * el hash (ver {@link #codigoHash}).
 */
@Entity(name = "denuncias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    /**
     * SHA-256 en hexadecimal del código de seguimiento.
     *
     * El código en claro existe UNA vez, en la respuesta al crear la
     * denuncia, y no se guarda en ningún sitio. Poder recuperarlo sería
     * poder suplantar al denunciante, y en una denuncia anónima eso es
     * lo mismo que poder identificarle.
     *
     * La columna es {@code VARCHAR(64)} y no {@code CHAR(64)} pese a
     * medir siempre lo mismo: un CHAR llega a Hibernate como
     * {@code bpchar}, su validación de esquema lo rechaza frente a un
     * String de JPA, y entonces la aplicación <b>no arranca</b> — falla
     * al construir el SessionFactory, antes de servir una sola petición.
     * Queda anotado también en la V13.
     */
    @Column(length = 64)
    private String codigoHash;

    /** Null = anónima. */
    @ManyToOne
    @JoinColumn(name = "denunciante_id")
    private User denunciante;

    @Enumerated(EnumType.STRING)
    private ComplaintCategory categoria;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplaintStatus estado = ComplaintStatus.RECIBIDA;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    /**
     * Cuándo quien instruye tocó el expediente por primera vez. Es el
     * acuse de recibo del art. 9.2, y el plazo de 7 días se mide contra
     * {@link #creadoEn}, no contra esto.
     */
    private Instant acuseReciboEn;

    /** Cuándo se cerró, esté {@code RESUELTA} o {@code ARCHIVADA}. */
    private Instant resueltaEn;

    /** En qué quedó. Obligatoria al cerrar. */
    private String conclusion;

    @Version
    private long version;

    /** Si nadie sabe -- ni puede saber -- quién la presentó. */
    public boolean esAnonima() {
        return denunciante == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Complaint other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
