package com.nxtime.nxtime.security.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El cifrado de los secretos TOTP en la base (ADR 051). */
class SecretosTotpTest {

    private static final String OTRA_CLAVE = Base64.getEncoder().encodeToString(new byte[32]);

    private final SecretosTotp secretos = new SecretosTotp(SecretosTotp.CLAVE_DE_DESARROLLO, false);

    @Test
    @DisplayName("Lo que se cifra se descifra igual, y en la base no aparece en claro")
    void idaYVuelta() {
        byte[] secreto = Totp.nuevoSecreto();
        String guardado = secretos.cifrar(7, secreto);
        assertThat(guardado).startsWith("v1:").doesNotContain(Totp.base32(secreto));
        assertThat(secretos.descifrar(7, guardado)).isEqualTo(secreto);
    }

    @Test
    @DisplayName("Cifrar dos veces lo mismo da textos distintos (nonce aleatorio)")
    void nonceAleatorio() {
        byte[] secreto = Totp.nuevoSecreto();
        assertThat(secretos.cifrar(7, secreto)).isNotEqualTo(secretos.cifrar(7, secreto));
    }

    @Test
    @DisplayName("El secreto de una persona copiado a la fila de otra no descifra")
    void atadoALaPersona() {
        String deSiete = secretos.cifrar(7, Totp.nuevoSecreto());
        assertThatThrownBy(() -> secretos.descifrar(8, deSiete)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Con otra clave no descifra")
    void otraClave() {
        String guardado = secretos.cifrar(7, Totp.nuevoSecreto());
        assertThatThrownBy(() -> new SecretosTotp(OTRA_CLAVE, false).descifrar(7, guardado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un texto manipulado no descifra")
    void manipulado() {
        String guardado = secretos.cifrar(7, Totp.nuevoSecreto());
        byte[] bytes = Base64.getDecoder().decode(guardado.substring(3));
        bytes[bytes.length - 1] ^= 1;
        String tocado = "v1:" + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> secretos.descifrar(7, tocado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("En producción no arranca con la clave de desarrollo, que está en el repositorio")
    void produccionSinClaveDeDesarrollo() {
        assertThatThrownBy(() -> new SecretosTotp(SecretosTotp.CLAVE_DE_DESARROLLO, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP_CLAVE");
        assertThat(new SecretosTotp(OTRA_CLAVE, true)).isNotNull();
    }

    @Test
    @DisplayName("Una clave que no es de 32 bytes no se acepta")
    void claveDe32Bytes() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new SecretosTotp(corta, false)).isInstanceOf(IllegalStateException.class);
    }
}
