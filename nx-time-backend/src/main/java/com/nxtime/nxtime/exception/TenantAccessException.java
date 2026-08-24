package com.nxtime.nxtime.exception;

/**
 * Un usuario intenta acceder o modificar un recurso de OTRA empresa
 * (violación del aislamiento multi-tenant). Se distingue a propósito de
 * un AccessDeniedException por falta de rol: aquí el usuario SÍ tiene el
 * rol adecuado, pero el recurso no es de su empresa. Se traduce siempre
 * a 403 FORBIDDEN.
 */
public class TenantAccessException extends RuntimeException {

    public TenantAccessException(String message) {
        super(message);
    }
}
