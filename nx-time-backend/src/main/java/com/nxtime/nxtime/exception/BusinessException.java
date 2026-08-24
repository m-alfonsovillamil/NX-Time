package com.nxtime.nxtime.exception;

import org.springframework.http.HttpStatus;

/**
 * Violación de una regla de negocio (no de validación de formato de
 * entrada: para eso está Bean Validation). Por defecto se traduce a
 * 409 CONFLICT -- un estado del sistema que choca con lo que se pide
 * ("ya hay una jornada activa", "la empresa ya existe"...). Cuando el
 * caso es más un error de entrada del cliente que un conflicto de
 * estado ("la fecha de inicio no puede ser posterior a la de fin"), se
 * puede indicar explícitamente 400 BAD_REQUEST.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
