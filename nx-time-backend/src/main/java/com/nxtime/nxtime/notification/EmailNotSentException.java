package com.nxtime.nxtime.notification;

/**
 * El correo no ha salido, y quien lo pidió tiene que saberlo. La lanza
 * {@link EmailSender#enviarObligatorio}; la otra forma de enviar,
 * {@link EmailSender#enviar}, se la traga.
 */
public class EmailNotSentException extends RuntimeException {

    public EmailNotSentException(String message, Throwable cause) {
        super(message, cause);
    }
}
