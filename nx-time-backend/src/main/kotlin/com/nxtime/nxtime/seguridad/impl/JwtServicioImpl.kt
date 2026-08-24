package com.nxtime.nxtime.seguridad.impl

import com.nxtime.nxtime.seguridad.JwtServicio
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.security.Key
import java.util.*

/*
 * Se encarga de CREAR y VALIDAR los tokens JWT.
 */
@Service
class JwtServicioImpl : JwtServicio {

    /*
     * Lee la clave secreta desde el archivo de propiedades. Es la "contraseña" para firmar y verificar los tokens.
     */
    @Value("\${application.security.jwt.secret-key}")
    private lateinit var secretKey: String

    /*
     * Inyecta el tiempo de expiración del token.
     */
    @Value("\${application.security.jwt.expiration}")
    private val jwtExpiration: Long = 86400000


    /*
     * Función principal para "abrir" un token y leer el email del usuario.
     */
    override fun extractUsername(token: String): String {
        return extractClaim(token, Claims::getSubject)
    }

    /*
     * Función principal para crear un token para un usuario que acaba de loguearse.
     */
    override fun generateToken(userDetails: UserDetails): String {
        return generateToken(HashMap(), userDetails)
    }

    /*
     * Función principal para validar un token. Comprueba que el email coincida y que no haya expirado.
     */
    override fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return (username == userDetails.username) && !isTokenExpired(token)
    }



    /*
     * Añade el email, la fecha de creación,la fecha de expiración y lo firma con la clave secreta.
     */
    private fun generateToken(extraClaims: Map<String, Any>, userDetails: UserDetails): String {
        return Jwts.builder()
            .setClaims(extraClaims)
            .setSubject(userDetails.username)
            .setIssuedAt(Date(System.currentTimeMillis()))
            .setExpiration(Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(getSignInKey(), SignatureAlgorithm.HS256)
            .compact()
    }

    /*
     * Comprueba si la fecha de expiración del token es anterior a la fecha actual.
     */
    private fun isTokenExpired(token: String): Boolean {
        return extractExpiration(token).before(Date())
    }

    private fun extractExpiration(token: String): Date {
        return extractClaim(token, Claims::getExpiration)
    }

    /*
     * Helper genérico para extraer cualquier dato de un token.
     */
    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    /*
     * Usa la clave secreta para "parsear" el token y obtener todos los datos que contiene.
     */
    private fun extractAllClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(getSignInKey())
            .build()
            .parseClaimsJws(token)
            .body
    }

    /*
     * Convierte la clave secreta en un objeto 'Key' que la librería 'jjwt' pueda usar para firmar.
     */
    private fun getSignInKey(): Key {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}