package com.nxtime.nxtime.dto;

/**
 * Un proveedor con el que se puede entrar en este servidor.
 *
 * @param id     {@code google} o {@code microsoft}
 * @param nombre lo que va en el botón («Google»)
 * @param inicio la URL adonde hay que mandar el navegador para empezar. Absoluta, y
 *               siempre la del dominio público de la API: la app Android habla con el
 *               backend por otro nombre, y la cookie de la ida solo vuelve al mismo host
 */
public record SsoProviderDTO(String id, String nombre, String inicio) {
}
