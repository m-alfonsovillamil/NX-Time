package com.nxtime.nxtime.security.totp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Cifra y descifra los secretos TOTP que se guardan en la base (ADR 051).
 *
 * Con el secreto en claro, una copia de la base --un volcado que se escapa,
 * una réplica mal protegida-- bastaría para generar los códigos de cualquiera,
 * y el segundo factor dejaría de ser otra cosa distinta de la contraseña. La
 * clave está fuera de la base, en {@code TOTP_CLAVE}.
 *
 * AES-256-GCM, con un nonce aleatorio por secreto. El id de la persona va como
 * dato asociado: el secreto cifrado de una no se puede copiar a la fila de
 * otra, porque no descifraría.
 *
 * El texto guardado empieza por la versión ({@code v1:}), para poder cambiar
 * de clave algún día sin adivinar con cuál se cifró cada fila.
 */
@Component
public class SecretosTotp {

    static final String VERSION = "v1:";
    private static final int BYTES_DEL_NONCE = 12;
    private static final int BITS_DE_LA_ETIQUETA = 128;

    /** La de application.yml: pública, solo para desarrollo. Ver el constructor. */
    static final String CLAVE_DE_DESARROLLO = "5W3oJApORCrZFouAZOrbZmA9bUHESg4Z/ggpox4SN4U=";

    private static final SecureRandom AZAR = new SecureRandom();

    private final SecretKey clave;

    @Autowired
    public SecretosTotp(@Value("${application.security.totp.clave}") String clave, Environment entorno) {
        this(clave, entorno.acceptsProfiles(Profiles.of("prod")));
    }

    SecretosTotp(String claveEnBase64, boolean produccion) {
        if (produccion && CLAVE_DE_DESARROLLO.equals(claveEnBase64.strip())) {
            throw new IllegalStateException("TOTP_CLAVE es la clave de desarrollo, que está publicada en el "
                    + "repositorio. Genera otra (openssl rand -base64 32).");
        }
        byte[] bytes = Base64.getDecoder().decode(claveEnBase64.strip());
        if (bytes.length != 32) {
            throw new IllegalStateException("TOTP_CLAVE tiene que ser de 32 bytes en base64 (openssl rand -base64 32); "
                    + "tiene " + bytes.length + ".");
        }
        this.clave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(long usuarioId, byte[] secreto) {
        byte[] nonce = new byte[BYTES_DEL_NONCE];
        AZAR.nextBytes(nonce);
        try {
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_DE_LA_ETIQUETA, nonce));
            cifrador.updateAAD(datoAsociado(usuarioId));
            byte[] cifrado = cifrador.doFinal(secreto);
            java.io.ByteArrayOutputStream junto = new java.io.ByteArrayOutputStream();
            junto.writeBytes(nonce);
            junto.writeBytes(cifrado);
            return VERSION + Base64.getEncoder().encodeToString(junto.toByteArray());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se ha podido cifrar el secreto del segundo factor", e);
        }
    }

    /**
     * El secreto en claro. Lanza si no descifra: otra clave, otra persona o un
     * texto manipulado. No hay forma razonable de seguir en ninguno de esos
     * casos, y decir «código incorrecto» escondería el problema.
     */
    public byte[] descifrar(long usuarioId, String guardado) {
        if (guardado == null || !guardado.startsWith(VERSION)) {
            throw new IllegalStateException("Secreto del segundo factor en un formato desconocido");
        }
        byte[] junto = Base64.getDecoder().decode(guardado.substring(VERSION.length()));
        try {
            Cipher descifrador = Cipher.getInstance("AES/GCM/NoPadding");
            descifrador.init(Cipher.DECRYPT_MODE, clave,
                    new GCMParameterSpec(BITS_DE_LA_ETIQUETA, junto, 0, BYTES_DEL_NONCE));
            descifrador.updateAAD(datoAsociado(usuarioId));
            return descifrador.doFinal(junto, BYTES_DEL_NONCE, junto.length - BYTES_DEL_NONCE);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("El secreto del segundo factor no descifra con TOTP_CLAVE: "
                    + "¿se ha cambiado la clave?", e);
        }
    }

    private static byte[] datoAsociado(long usuarioId) {
        return ("nxtime-totp:" + usuarioId).getBytes(StandardCharsets.US_ASCII);
    }
}
