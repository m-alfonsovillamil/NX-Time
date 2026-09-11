package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;

/**
 * Códigos de un solo uso para elegir contraseña (ver ADR 014): el de alta,
 * que llega al crear la cuenta, y el de "He olvidado mi contraseña".
 */
public interface AccessCodeService {

    /**
     * Emite el código de alta y lo manda por correo en el momento.
     *
     * Va DENTRO de la transacción del alta: si el correo no sale, lanza y
     * el alta se deshace entera. Una cuenta creada cuyo dueño no ha
     * recibido el código es una cuenta en la que nadie puede entrar.
     *
     * @throws com.nxtime.nxtime.exception.BusinessException 503 si el correo no ha podido salir
     */
    void emitirCodigoDeAlta(User usuario, String nombreEmpresa);

    /**
     * "He olvidado mi contraseña", o "es mi primera vez y se me pasó el
     * código de alta".
     *
     * <b>No dice nunca si el correo tiene cuenta</b>: no lanza ni cuando no
     * la tiene, ni cuando se pasa del límite de códigos por hora, ni cuando
     * el correo falla. Todo eso queda en el log.
     */
    void solicitarRecuperacion(String email);

    /**
     * Fija la contraseña con un código vigente, lo marca como usado y
     * cierra todas las sesiones abiertas de la cuenta.
     *
     * @throws com.nxtime.nxtime.exception.BusinessException 400, con el
     *         mismo mensaje para un correo sin cuenta y para un código
     *         incorrecto, caducado, usado o agotado
     */
    void confirmar(String email, String codigo, String contrasenaNueva);
}
