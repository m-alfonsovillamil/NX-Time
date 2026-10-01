package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
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
 * Un kiosco de fichaje: una tablet de una empresa donde la plantilla ficha sin
 * su propia sesión (ADR 033). Tabla "kioscos".
 *
 * Es un dispositivo, no una persona: solo sabe fichar, y cada fichaje sigue
 * siendo de quien se identificó con su tarjeta o su PIN. Se autentica con un
 * token opaco del que solo se guarda el SHA-256 ({@link #tokenHash}), que es
 * null mientras la tablet no lo ha recogido.
 *
 * No se borra nunca, porque los fichajes lo citan: se revoca.
 */
@Entity(name = "kioscos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Kiosk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    /** Cómo lo llama la empresa: «Entrada almacén». */
    private String nombre;

    private String tokenHash;

    @ManyToOne
    @JoinColumn(name = "creado_por")
    private User creadoPor;

    @Builder.Default
    private Instant creadoEn = Instant.now();

    private Instant ultimoUso;

    private Instant revocadoEn;

    public boolean activo() {
        return revocadoEn == null && tokenHash != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Kiosk other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
