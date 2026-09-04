package com.nxtime.nxtime.dto;

/**
 * Cuántos avisos sin leer tiene quien pregunta. Lo consume el contador
 * de la campana, que se pide mucho más a menudo que la lista entera.
 *
 * Es un objeto y no un número desnudo: un cuerpo JSON escalar
 * ({@code 3}) es incómodo de consumir desde Gson/Retrofit, no se puede
 * ampliar sin romper el contrato y queda pobre en la documentación.
 */
public record UnreadNoticeCountResponse(long noLeidos) {
}
