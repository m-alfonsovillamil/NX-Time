package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Registrar este dispositivo para recibir push (Fase B5). */
public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 4096) String token,
        @NotNull PushPlatform plataforma
) {
}
