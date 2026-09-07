package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Los BYTES de un adjunto, en su propia tabla (Fase B2). Tabla
 * "adjunto_datos". Ver {@link Attachment} y el ADR 007 para el porqué.
 *
 * No tiene identidad propia: su clave primaria ES la clave ajena al
 * adjunto, porque esta fila no es una cosa aparte, es el cuerpo de
 * aquella. Por eso tampoco lleva {@code @ManyToOne} al adjunto: una
 * relación aquí solo serviría para volver a arrastrar los bytes desde
 * el otro lado, que es justo lo que se está evitando.
 *
 * Se lee únicamente al descargar, con una consulta explícita.
 */
@Entity(name = "adjunto_datos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentData {

    @Id
    private long adjuntoId;

    private byte[] contenido;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttachmentData other)) {
            return false;
        }
        return adjuntoId != 0 && adjuntoId == other.adjuntoId;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
