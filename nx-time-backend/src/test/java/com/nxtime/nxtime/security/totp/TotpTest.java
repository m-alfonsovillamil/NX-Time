package com.nxtime.nxtime.security.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Los códigos TOTP (ADR 051), contra los vectores de la propia RFC. */
class TotpTest {

    /** El secreto de los vectores de prueba de SHA-1 de la RFC 6238 (apéndice B). */
    private static final byte[] SECRETO_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /*
     * La RFC da 8 cifras; con 6, son las 6 últimas (el truncado es «módulo
     * 10^dígitos»). Si el truncado dinámico o el contador estuvieran mal, no
     * coincidiría ninguno.
     */
    @ParameterizedTest(name = "t={0} -> {1}")
    @CsvSource({
            "59,          287082",
            "1111111109,  081804",
            "1111111111,  050471",
            "1234567890,  005924",
            "2000000000,  279037",
            "20000000000, 353130"
    })
    @DisplayName("Da los códigos de los vectores de la RFC 6238 (SHA-1)")
    void vectoresDeLaRfc(long segundos, String esperado) {
        assertThat(Totp.codigo(SECRETO_RFC, Totp.pasoDe(Instant.ofEpochSecond(segundos)))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("El secreto en Base32 es el de la RFC 4648, sin relleno")
    void base32() {
        assertThat(Totp.base32(SECRETO_RFC)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    }

    @Test
    @DisplayName("Vale el código del paso anterior y el del siguiente, y ninguno más allá")
    void ventana() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        long paso = Totp.pasoDe(ahora);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 1), ahora, null)).hasValue(paso - 1);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso), ahora, null)).hasValue(paso);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso + 1), ahora, null)).hasValue(paso + 1);
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 2), ahora, null)).isEmpty();
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso + 2), ahora, null)).isEmpty();
    }

    @Test
    @DisplayName("Un código ya usado, o uno anterior al último usado, no vuelve a valer")
    void sinReutilizar() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        long paso = Totp.pasoDe(ahora);
        String codigo = Totp.codigo(SECRETO_RFC, paso);
        assertThat(Totp.verificar(SECRETO_RFC, codigo, ahora, paso)).isEmpty();
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso - 1), ahora, paso)).isEmpty();
        assertThat(Totp.verificar(SECRETO_RFC, Totp.codigo(SECRETO_RFC, paso + 1), ahora, paso)).hasValue(paso + 1);
    }

    @Test
    @DisplayName("Lo que no son seis cifras no vale, ni se compara")
    void formato() {
        Instant ahora = Instant.ofEpochSecond(59);
        assertThat(Totp.verificar(SECRETO_RFC, "28708", ahora, null)).isEmpty();
        assertThat(Totp.verificar(SECRETO_RFC, "2870821", ahora, null)).isEmpty();
        assertThat(Totp.verificar(SECRETO_RFC, "28708a", ahora, null)).isEmpty();
        assertThat(Totp.verificar(SECRETO_RFC, null, ahora, null)).isEmpty();
    }

    @Test
    @DisplayName("Cada secreto nuevo es distinto y tiene 160 bits")
    void secretosNuevos() {
        byte[] uno = Totp.nuevoSecreto();
        byte[] otro = Totp.nuevoSecreto();
        assertThat(uno).hasSize(20).isNotEqualTo(otro);
    }

    @Test
    @DisplayName("La dirección del QR lleva el emisor, la cuenta y los parámetros que entienden todas las aplicaciones")
    void uri() {
        String uri = Totp.uri("NX Time", "ana@empresa.test", SECRETO_RFC);
        assertThat(uri).isEqualTo("otpauth://totp/NX%20Time:ana%40empresa.test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                + "&issuer=NX%20Time&algorithm=SHA1&digits=6&period=30");
    }
}
