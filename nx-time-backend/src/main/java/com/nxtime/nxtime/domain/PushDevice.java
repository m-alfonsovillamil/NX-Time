package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Una instalación de la app a la que se le pueden mandar push (Fase B5).
 * Tabla {@code dispositivos_push} (V34).
 *
 * Sin {@code @Version}: la fila no la edita nadie a mano, solo se registra,
 * cambia de dueño o se borra, y lo último que escribe gana, que es lo
 * correcto (el token lo tiene quien acaba de entrar en ese móvil).
 */
@Entity(name = "dispositivos_push")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Enumerated(EnumType.STRING)
    private PushPlatform plataforma;

    private String token;

    @Builder.Default
    private Instant registradoEn = Instant.now();

    @Builder.Default
    private Instant vistoEn = Instant.now();
}
