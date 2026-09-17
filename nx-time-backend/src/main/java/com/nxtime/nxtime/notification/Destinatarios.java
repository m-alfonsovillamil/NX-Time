package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import java.util.List;

/**
 * A quién mandar un aviso, dentro de una empresa.
 *
 * Esta cuenta estaba copiada en cinco servicios (ausencias, correcciones,
 * horas extra, denuncias y ofertas), cada copia con sus mismas dos reglas
 * escritas a mano:
 *
 * <ul>
 *   <li><b>Solo cuentas activas.</b> Una cuenta de baja no tiene que recibir
 *       avisos de algo que ya no puede resolver ni ver.</li>
 *   <li><b>Por authority, nunca por {@code Role}.</b> "ausencia:aprobar" la
 *       tienen GESTOR, RRHH y ADMIN; filtrar por {@code Role.GESTOR} dejaría
 *       sin avisar a una empresa cuyo único responsable es el ADMIN que la
 *       fundó. Ese error ya se corrigió una vez en este proyecto.</li>
 * </ul>
 *
 * Con cinco copias, la sexta que alguien escribiera tenía todas las
 * papeletas de olvidar una de las dos.
 *
 * <p>Recibe la lista de candidatos y no el repositorio a propósito: así cada
 * servicio sigue resolviéndolos <b>dentro de su transacción</b>, que es donde
 * tienen que resolverse (el listener corre {@code @Async} con la sesión de
 * JPA ya cerrada), y esto se prueba sin base de datos.
 *
 * <p>Excluir a quien hace la acción es una cortesía que <b>no siempre se
 * puede tener</b>, y por eso hay un método con y otro sin: en el canal de
 * denuncias, excluir al denunciante de la lista de avisados revelaría quién
 * ha denunciado. Ver {@code ComplaintServiceImpl.quienInstruye}.
 */
public final class Destinatarios {

    private Destinatarios() {
    }

    /** Los activos que tienen la authority. Sin excluir a nadie. */
    public static List<User> conAuthority(List<User> candidatos, String authority) {
        return conAuthorityMenos(candidatos, authority, null);
    }

    /**
     * Los activos que tienen la authority, menos {@code excluido} (normalmente
     * quien acaba de hacer la acción: avisarte de lo que acabas de hacer es
     * ruido). {@code excluido} puede ser null.
     */
    public static List<User> conAuthorityMenos(List<User> candidatos, String authority, User excluido) {
        return candidatos.stream()
                .filter(User::isActivo)
                .filter(candidato -> excluido == null || candidato.getId() != excluido.getId())
                .filter(candidato -> RoleAuthorities.tiene(candidato, authority))
                .toList();
    }

    /** Toda la plantilla activa menos {@code excluido}, sin mirar authorities. */
    public static List<User> activosMenos(List<User> candidatos, User excluido) {
        return candidatos.stream()
                .filter(User::isActivo)
                .filter(candidato -> candidato.getId() != excluido.getId())
                .toList();
    }
}
