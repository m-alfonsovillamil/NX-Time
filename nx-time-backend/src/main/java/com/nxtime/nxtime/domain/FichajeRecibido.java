package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un fichaje sin conexión ya recibido (V50, ADR 054), para no aplicarlo dos
 * veces: si la respuesta se pierde, la app lo vuelve a mandar con el mismo
 * {@code idCliente} y se le contesta lo mismo que la primera vez. Se borra a
 * los 30 días ({@code ConservacionDeDatos}).
 */
@Entity(name = "fichajes_recibidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FichajeRecibido {

    /** Lo que se hizo con él. */
    public enum Resultado {
        /** Aplicado con su hora. */
        APLICADO,
        /** Aplicado con su hora, pero llegó pasadas 72 h: lo tiene que dar por bueno un gestor. */
        A_REVISAR,
        /** No se pudo aplicar; el motivo dice por qué. */
        RECHAZADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "usuario_id", nullable = false)
    private long usuarioId;

    @Column(name = "id_cliente", nullable = false)
    private UUID idCliente;

    /** El fichaje que tocó, o null si se rechazó. */
    @Column(name = "registro_id")
    private Long registroId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Resultado resultado;

    @Column(length = 500)
    private String motivo;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;
}
