package com.nxtime.nxtime.audit;

import com.nxtime.nxtime.domain.TimeEntryAudit;

/**
 * Evento de aplicación publicado por {@link
 * com.nxtime.nxtime.service.impl.TimeEntryServiceImpl} cada vez que se
 * crea, modifica o corrige un fichaje. No lleva el hash ni lo persiste
 * él mismo -- eso es tarea de {@link TimeEntryAuditListener}, que es
 * quien conoce el hash de la fila anterior y por tanto el único punto
 * del código que puede calcular el encadenamiento sin condiciones de
 * carrera entre dos publicaciones simultáneas del mismo evento (bueno,
 * dentro de lo razonable para una única instancia -- ver el propio
 * listener).
 *
 * "auditRow" llega sin id, sin hash y sin hashAnterior: son los tres
 * campos que el listener rellena antes de guardar.
 */
public record TimeEntryAuditEvent(TimeEntryAudit auditRow) {
}
