package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Los METADATOS de un fichero subido: quién, qué, cuánto y cuándo
 * (Fase B2). Tabla "adjuntos".
 *
 * <b>Esta entidad no tiene los bytes, y es a propósito.</b> Viven en
 * {@link AttachmentData}, en su propia tabla, porque un {@code bytea} en
 * esta misma fila se cargaría en cada {@code findById} aunque nadie
 * pidiera el contenido: listar los adjuntos de una empresa se traería
 * todos los currículums a memoria. La anotación que parece resolverlo
 * ({@code @Basic(fetch = LAZY)}) solo funciona con <i>bytecode
 * enhancement</i> de Hibernate, que aquí no está activado, y sin él se
 * ignora sin dar error. Ver ADR 007.
 *
 * Así que si alguna vez alguien piensa en "simplificar" juntando las dos
 * tablas: ese es el motivo de que estén separadas.
 */
@Entity(name = "adjuntos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {

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
    private AttachmentType tipo;

    /** El nombre con el que se subió, para devolverlo al descargar. */
    private String nombreOriginal;

    /**
     * El MIME REAL, deducido de los primeros bytes al subir -- no el que
     * declaró el cliente. En una foto es siempre {@code image/jpeg},
     * porque el servidor la reescala a JPEG sea cual sea el original.
     */
    private String mime;

    /** Tamaño de lo que se guardó, que en una foto no es lo que llegó. */
    private long tamanoBytes;

    private Instant subidoEn;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Attachment other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
