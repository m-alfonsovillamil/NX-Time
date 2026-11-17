package com.nxtime.app.ui.util

/**
 * Cómo se escribe una cantidad de días desde que hay medios días (ADR 063):
 * «3», «2,5», «0,5». Con coma y sin el «,0» de los enteros, igual que el
 * servidor en sus mensajes y la web.
 */
fun Double.comoDias(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString().replace('.', ',')
