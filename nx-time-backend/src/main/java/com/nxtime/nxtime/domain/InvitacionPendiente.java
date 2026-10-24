package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una invitación que falta por mandar (V48, ADR 052): la cuenta ya existe, y
 * el correo con el código para elegir contraseña sale en cuanto haya cupo
 * (50 altas al día por empresa, y el tope diario del proveedor de correo).
 * Al mandarla, la fila se borra.
 */
@Entity(name = "invitaciones_pendientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitacionPendiente {

    @Id
    @Column(name = "usuario_id")
    private long usuarioId;

    @Column(name = "empresa_id", nullable = false)
    private long empresaId;

    @Builder.Default
    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private int intentos = 0;

    @Column(name = "ultimo_intento")
    private Instant ultimoIntento;

    @Column(name = "ultimo_error", length = 200)
    private String ultimoError;
}
