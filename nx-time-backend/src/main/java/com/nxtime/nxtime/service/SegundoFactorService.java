package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ActivacionSegundoFactorResponse;
import com.nxtime.nxtime.dto.SegundoFactorResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El segundo factor de cada persona: un código TOTP de una aplicación de
 * autenticación, con códigos de recuperación para el día que se pierde el
 * móvil (ADR 051).
 */
public interface SegundoFactorService {

    SegundoFactorResponse estado(User usuario);

    /**
     * Empieza a activarlo: un secreto nuevo, que todavía no se pide al entrar.
     * Repetirlo antes de confirmar cambia el secreto (el QR anterior deja de
     * valer).
     */
    ActivacionSegundoFactorResponse iniciarActivacion(User usuario);

    /**
     * Lo deja activo, con la contraseña y un código del secreto nuevo. La
     * sesión desde la que se hace pasa a contar como sesión con segundo factor.
     *
     * @return los códigos de recuperación, en claro: la única vez
     */
    List<String> confirmarActivacion(User usuario, String contrasena, String codigo, Optional<UUID> sesion);

    /** Lo quita, con la contraseña y un código. No a quien se le exige. */
    void desactivar(User usuario, String contrasena, String codigo);

    /** Códigos de recuperación nuevos; los de antes dejan de valer. */
    List<String> regenerarCodigos(User usuario, String codigo);

    /**
     * Un ADMIN o RRHH se lo quita a alguien de su empresa que ha perdido el
     * móvil y los códigos. No a sí mismo ni a un rol por encima del suyo.
     */
    void quitar(long usuarioId, User quien);

    /**
     * El código del login (ADR 051): uno de la aplicación o uno de
     * recuperación. Con su propio límite de intentos por cuenta. Gasta el
     * código si vale: no vuelve a valer.
     */
    boolean verificarAlEntrar(User usuario, String codigo);
}
