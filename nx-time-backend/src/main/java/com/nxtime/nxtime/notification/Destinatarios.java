package com.nxtime.nxtime.notification;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.UserRepository;
import java.util.List;
import java.util.Set;

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

    /**
     * Los que hay que avisar, preguntándoselo a la base (Fase A5).
     *
     * Lo que cambia respecto a antes es de dónde salen los candidatos:
     * {@code findByEmpresa(empresa)} traía la plantilla entera para quedarse
     * con dos o tres personas. La regla es la misma, y sigue siendo por
     * authority y no por rol: {@link RoleAuthorities#rolesCon} hace la
     * traducción una sola vez, a partir de la misma tabla que alimenta los
     * {@code @PreAuthorize}.
     *
     * Las versiones que recibían la lista ya cargada se han ido con ella: no
     * quedaba ninguna ruta que las usara, y un método que solo llaman sus
     * propios tests es código muerto con coartada. La regla se sigue probando
     * sin base de datos, pero donde ahora vive: en {@code rolesCon}.
     *
     * @param excluido normalmente quien acaba de hacer la acción; puede ser null
     */
    public static List<User> conAuthority(
            UserRepository userRepository, Company empresa, String authority, User excluido) {
        Set<Role> roles = RoleAuthorities.rolesCon(authority);
        if (roles.isEmpty()) {
            // Una authority que no tiene ningún rol: o está mal escrita, o se
            // quedó huérfana. La consulta con un IN vacío fallaría en algunos
            // dialectos, y devolver a nadie es la respuesta correcta.
            return List.of();
        }
        return userRepository.findDestinatarios(empresa, roles).stream()
                .filter(candidato -> excluido == null || candidato.getId() != excluido.getId())
                .toList();
    }

    /**
     * Toda la plantilla activa menos {@code excluido}, sin mirar authorities.
     *
     * Esta sí sigue recibiendo la lista: la usa el aviso de oferta interna, que
     * va a todo el mundo, y ahí quien consulta ya pide solo los activos
     * ({@code findByEmpresaAndActivoTrue}). El filtro de {@code activo} se
     * queda igualmente, porque la regla no puede depender de cómo se
     * consultara.
     */
    public static List<User> activosMenos(List<User> candidatos, User excluido) {
        return candidatos.stream()
                .filter(User::isActivo)
                .filter(candidato -> candidato.getId() != excluido.getId())
                .toList();
    }
}
