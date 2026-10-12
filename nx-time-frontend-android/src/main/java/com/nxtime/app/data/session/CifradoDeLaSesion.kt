package com.nxtime.app.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra lo que la sesión deja escrito en el disco: los dos tokens.
 *
 * Es una interfaz por los tests: el almacén de claves de Android no existe en
 * la JVM donde corren.
 */
interface CifradoDeLaSesion {
    /** El texto cifrado, o null si este dispositivo no puede cifrar. */
    fun cifrar(texto: String): String?

    /** El texto original, o null si no se puede leer: otra clave, o un valor estropeado. */
    fun descifrar(cifrado: String): String?
}

/**
 * AES-256-GCM con una clave que vive en el almacén de claves de Android y no
 * sale de él (octubre de 2026).
 *
 * Hasta la 1.15 los tokens se guardaban tal cual en las preferencias de la
 * app. Están en su carpeta privada y fuera de las copias de seguridad
 * (`allowBackup="false"`), así que otra app no los lee; pero quien saque los
 * ficheros del teléfono, con acceso de root o desde una imagen del
 * almacenamiento, se llevaba una sesión que vale un mes. Cifrados con una
 * clave que no se puede sacar del dispositivo, esos ficheros solos no sirven.
 *
 * No es `EncryptedSharedPreferences`: esa librería de AndroidX quedó sin
 * mantenimiento en 2025, y lo que hacía falta de ella son estas líneas.
 *
 * La clave no pide huella ni desbloqueo: renovar el token y registrar el push
 * pasan con la app en segundo plano y la pantalla apagada.
 */
class CifradoConElAlmacenDeClaves : CifradoDeLaSesion {

    override fun cifrar(texto: String): String? = try {
        val cifra = Cipher.getInstance(TRANSFORMACION)
        cifra.init(Cipher.ENCRYPT_MODE, clave())
        val cifrado = cifra.doFinal(texto.toByteArray(Charsets.UTF_8))
        // El vector de inicialización lo elige el almacén, uno por cifrado,
        // y hace falta para descifrar: va delante.
        (cifra.iv + cifrado).toByteString().base64()
    } catch (e: Exception) {
        Log.w(ETIQUETA, "No se ha podido cifrar la sesión", e)
        null
    }

    override fun descifrar(cifrado: String): String? = try {
        val bytes = cifrado.decodeBase64()?.toByteArray()
        if (bytes == null || bytes.size <= BYTES_DEL_VECTOR) {
            null
        } else {
            val cifra = Cipher.getInstance(TRANSFORMACION)
            cifra.init(
                Cipher.DECRYPT_MODE,
                clave(),
                GCMParameterSpec(BITS_DE_LA_MARCA, bytes, 0, BYTES_DEL_VECTOR)
            )
            String(cifra.doFinal(bytes, BYTES_DEL_VECTOR, bytes.size - BYTES_DEL_VECTOR), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        Log.w(ETIQUETA, "No se ha podido descifrar la sesión", e)
        null
    }

    /** La clave de este dispositivo: la que hay, o una nueva la primera vez. */
    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(ALMACEN).apply { load(null) }
        (almacen.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    private companion object {
        const val ETIQUETA = "NxTimeSesion"
        const val ALMACEN = "AndroidKeyStore"
        const val ALIAS = "nxtime_sesion"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BYTES_DEL_VECTOR = 12
        const val BITS_DE_LA_MARCA = 128
    }
}
