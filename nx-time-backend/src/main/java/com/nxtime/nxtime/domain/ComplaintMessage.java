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
 * Un mensaje dentro del expediente de una denuncia (Fase G). Tabla
 * "denuncia_mensajes".
 *
 * Es lo que hace que el canal sirva para algo más que recibir: quien
 * instruye puede pedir una aclaración sin saber a quién se la pide, y
 * quien denunció puede contestar sin dejar de ser anónimo.
 *
 * Los mensajes <b>no se editan ni se borran</b>. No hace falta un
 * disparador como el de la auditoría (V5) porque no hay ningún endpoint
 * que lo permita: la conversación de un expediente es prueba de cómo se
 * tramitó, y reescribirla después vaciaría de sentido las fechas.
 */
@Entity(name = "denuncia_mensajes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "denuncia_id")
    private Complaint denuncia;

    /** De qué lado viene. Ver {@link ComplaintAuthor}. */
    @Enumerated(EnumType.STRING)
    private ComplaintAuthor autorRol;

    /**
     * Quién lo escribió, o null si viene del denunciante de una denuncia
     * anónima.
     *
     * <b>Null aunque quien escribe esté autenticado</b>, que lo está: el
     * canal vive dentro de la aplicación y al servidor le llega un token.
     * Copiar aquí ese id "para trazar" desharía el anonimato de la
     * denuncia entera en cuanto el denunciante contestara una pregunta.
     */
    @ManyToOne
    @JoinColumn(name = "autor_id")
    private User autor;

    private String texto;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComplaintMessage other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
