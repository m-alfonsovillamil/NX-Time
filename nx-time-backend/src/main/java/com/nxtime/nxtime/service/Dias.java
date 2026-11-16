package com.nxtime.nxtime.service;

import java.math.BigDecimal;

/**
 * Cómo se escribe una cantidad de días desde que hay medios días (ADR 063):
 * «3», «2,5», «0,5». Con coma, que es como se lee en español, y sin el «,0»
 * de los enteros.
 */
public final class Dias {

    private Dias() {
    }

    public static String texto(double dias) {
        // toPlainString: stripTrailingZeros deja 20 como 2E+1.
        return BigDecimal.valueOf(dias).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
