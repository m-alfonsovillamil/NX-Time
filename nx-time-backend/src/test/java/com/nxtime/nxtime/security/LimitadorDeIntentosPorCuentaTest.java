package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * El límite por cuenta, que es el que no depende de los proxies.
 *
 * El de IP se puede esquivar cambiando de sitio (o de cabecera, que fue el
 * defecto). Probar contraseñas de UNA persona desde mil sitios distintos solo
 * lo frena contar por la cuenta a la que se intenta entrar.
 */
class LimitadorDeIntentosPorCuentaTest {

    private static final int LIMITE = 10;

    @Test
    @DisplayName("Al pasarse de intentos contra la misma cuenta, corta con 429")
    void demasiadosIntentos_corta() {
        LimitadorDeIntentosPorCuenta limitador = new LimitadorDeIntentosPorCuenta();

        for (int i = 0; i < LIMITE; i++) {
            limitador.comprobar("alguien@nxtime.test");
        }

        assertThatThrownBy(() -> limitador.comprobar("alguien@nxtime.test"))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(throwable(BusinessException.class))
                .extracting(BusinessException::getStatus)
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Gastar el cupo de una cuenta no deja fuera a las demás")
    void cadaCuentaTieneSuCupo() {
        LimitadorDeIntentosPorCuenta limitador = new LimitadorDeIntentosPorCuenta();

        for (int i = 0; i < LIMITE + 1; i++) {
            try {
                limitador.comprobar("victima@nxtime.test");
            } catch (BusinessException esperada) {
                // el cupo de esa cuenta, que es justo lo que se está gastando
            }
        }

        assertThatCode(() -> limitador.comprobar("otra.persona@nxtime.test")).doesNotThrowAnyException();
    }

    /*
     * Si el correo distinguiera mayúsculas, probar "Alguien@" y "alguien@"
     * daría dos cupos por la misma cuenta. El login ya no distingue (V17).
     */
    @Test
    @DisplayName("Da igual cómo se escriba el correo: es la misma cuenta y el mismo cupo")
    void mayusculasYEspacios_sonLaMismaCuenta() {
        LimitadorDeIntentosPorCuenta limitador = new LimitadorDeIntentosPorCuenta();

        for (int i = 0; i < LIMITE; i++) {
            limitador.comprobar("alguien@nxtime.test");
        }

        assertThatThrownBy(() -> limitador.comprobar("  Alguien@NXTime.test  "))
                .isInstanceOf(BusinessException.class);
    }

    // ---- la espera creciente tras fallos seguidos (ADR 034) ----

    /** Un reloj que se adelanta a mano. */
    private static final class Reloj extends java.time.Clock {
        private java.time.Instant ahora = java.time.Instant.parse("2026-10-03T10:00:00Z");

        void avanzar(java.time.Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return ahora;
        }
    }

    @Test
    @DisplayName("La espera se dobla a partir del quinto fallo seguido, con un techo de 15 minutos")
    void laEsperaSeDobla() {
        org.assertj.core.api.Assertions.assertThat(LimitadorDeIntentosPorCuenta.esperaTras(4))
                .isEqualTo(java.time.Duration.ZERO);
        org.assertj.core.api.Assertions.assertThat(LimitadorDeIntentosPorCuenta.esperaTras(5))
                .isEqualTo(java.time.Duration.ofSeconds(1));
        org.assertj.core.api.Assertions.assertThat(LimitadorDeIntentosPorCuenta.esperaTras(8))
                .isEqualTo(java.time.Duration.ofSeconds(8));
        org.assertj.core.api.Assertions.assertThat(LimitadorDeIntentosPorCuenta.esperaTras(40))
                .isEqualTo(LimitadorDeIntentosPorCuenta.ESPERA_MAXIMA);
    }

    @Test
    @DisplayName("Tras cinco fallos hay que esperar; pasada la espera se puede probar, y acertar la pone a cero")
    void trasCincoFallosHayQueEsperar() {
        Reloj reloj = new Reloj();
        LimitadorDeIntentosPorCuenta limitador = new LimitadorDeIntentosPorCuenta(reloj);
        for (int i = 0; i < 5; i++) {
            limitador.comprobar("ana@nxtime.test");
            limitador.fallo("ana@nxtime.test");
        }

        assertThatThrownBy(() -> limitador.comprobar("ANA@nxtime.test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Espera");
        // Otra cuenta no paga por esta.
        assertThatCode(() -> limitador.comprobar("luis@nxtime.test")).doesNotThrowAnyException();

        reloj.avanzar(java.time.Duration.ofSeconds(2));
        assertThatCode(() -> limitador.comprobar("ana@nxtime.test")).doesNotThrowAnyException();

        limitador.acierto("ana@nxtime.test");
        limitador.fallo("ana@nxtime.test");
        assertThatCode(() -> limitador.comprobar("ana@nxtime.test")).doesNotThrowAnyException();
    }
}
