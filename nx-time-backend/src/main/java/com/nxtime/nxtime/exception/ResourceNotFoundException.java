package com.nxtime.nxtime.exception;

/**
 * El recurso solicitado (usuario, petición de ausencia...) no existe.
 * Se traduce siempre a 404 NOT_FOUND.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
