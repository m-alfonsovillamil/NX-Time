package com.nxtime.nxtime.dto;

/**
 * Lo que responde el registro de una empresa desde la V37 (ADR 034): no hay
 * sesión todavía, hay que confirmar el correo con el código que acaba de
 * salir hacia él.
 *
 * <p>Es la misma respuesta haya o no ya una cuenta con ese correo: decir
 * «ese correo ya está registrado» serviría para averiguar quién usa NX Time.
 *
 * @param email el correo al que ha salido el código, para la pantalla
 *     siguiente
 * @param mensaje lo que se le dice a la persona
 */
public record RegistrationPendingResponse(String email, String mensaje) {
}
