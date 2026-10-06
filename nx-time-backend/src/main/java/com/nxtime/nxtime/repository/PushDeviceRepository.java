package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.PushDevice;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByToken(String token);

    /**
     * Apunta el token a nombre de esa persona, exista ya o no, en una sola
     * sentencia.
     *
     * Antes era «buscar y, si no está, insertar», y entre las dos cosas cabe
     * otra petición: la app registra el token dos veces casi a la vez al
     * encender los push (una por encenderlos y otra porque Google entrega el
     * token en ese momento), las dos no encontraban nada, las dos insertaban y
     * la segunda chocaba con {@code uq_dispositivos_push_token}. Con
     * {@code ON CONFLICT} la que llega segunda espera a la primera y actualiza
     * la fila: lo último que escribe gana, que es lo que ya se quería (ver
     * {@link PushDevice}).
     *
     * {@code clearAutomatically}: la sentencia cambia la fila por debajo de
     * Hibernate. Si el dispositivo ya estaba cargado en la sesión, la consulta
     * siguiente devolvería ese objeto con el {@code vistoEn} viejo, y el tope
     * por persona lo daría de baja por antiguo justo cuando se acaba de usar.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO dispositivos_push (usuario_id, plataforma, token, registrado_en, visto_en)
            VALUES (:usuarioId, :plataforma, :token, :ahora, :ahora)
            ON CONFLICT (token) DO UPDATE
               SET usuario_id = EXCLUDED.usuario_id,
                   plataforma = EXCLUDED.plataforma,
                   visto_en   = EXCLUDED.visto_en
            """, nativeQuery = true)
    void registrar(@Param("usuarioId") long usuarioId, @Param("plataforma") String plataforma,
            @Param("token") String token, @Param("ahora") Instant ahora);

    /**
     * Los tokens a los que mandar un aviso: los de esa persona, si sigue
     * activa. A quien han dado de baja no se le manda nada aunque su móvil
     * siga registrado: no puede entrar a leerlo.
     */
    @Query("SELECT d.token FROM dispositivos_push d "
            + "WHERE d.usuario.id = :usuarioId AND d.usuario.activo = true")
    List<String> findTokensDeUsuarioActivo(@Param("usuarioId") long usuarioId);

    /** Para la exportación de datos personales. */
    List<PushDevice> findByUsuario_IdOrderByRegistradoEnDesc(long usuarioId);

    /**
     * Borra los tokens que FCM ha dicho que ya no valen.
     *
     * Transaccional por su cuenta: se llama DESPUÉS de enviar, fuera de
     * cualquier transacción, porque tener una conexión de la base abierta
     * mientras se espera a Google es justo lo que la Fase A9 sacó del correo.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM dispositivos_push d WHERE d.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Transactional
    @Query("DELETE FROM dispositivos_push d WHERE d.token = :token AND d.usuario.id = :usuarioId")
    int deleteByTokenAndUsuario(@Param("token") String token, @Param("usuarioId") long usuarioId);
}
