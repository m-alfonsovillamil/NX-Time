package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Los códigos de recuperación, en claro (ADR 051). Es la única vez que se ven:
 * en la base solo queda su hash.
 */
@Schema(description = "Diez códigos de recuperación de un solo uso. Solo se enseñan esta vez")
public record CodigosDeRecuperacionResponse(List<String> codigos) {
}
