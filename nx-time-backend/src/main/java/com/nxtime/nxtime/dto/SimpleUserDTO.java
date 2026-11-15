package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.User;

/**
 * Quién es alguien en un listado: su nombre y sus apellidos.
 *
 * Hasta noviembre de 2026 llevaba solo el nombre de pila, y en la bandeja de
 * un gestor dos «Javier» no se distinguían: con ~120 personas en una empresa
 * real pasa seguro. El campo se sigue llamando {@code nombre} para no cambiar
 * el contrato, y así lo ven también las versiones de la app ya instaladas.
 * Las iniciales de la web ya se sacan de un nombre completo.
 */
public record SimpleUserDTO(String nombre) {

    /** {@code null} si no hay persona (un aprobador que aún no existe). */
    public static SimpleUserDTO de(User usuario) {
        return usuario == null ? null : new SimpleUserDTO(usuario.nombreCompleto());
    }
}
