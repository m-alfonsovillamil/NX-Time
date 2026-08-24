package com.nxtime.nxtime.seguridad

import org.springframework.security.core.userdetails.UserDetails

/*
 * Define las 3 cosas principales que nuestro servicio de JWT debe saber hacer.
 */
interface JwtServicio {

    /*
     * Define una función para leer un token y extraer el email.
     */
    fun extractUsername(token: String): String

    /*
     * Define una función para crear un token nuevo para un usuario.
     */
    fun generateToken(userDetails: UserDetails): String

    /*
     * Define una función para validar un token
     */
    fun isTokenValid(token: String, userDetails: UserDetails): Boolean
}