package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ExternalIdentity;
import com.nxtime.nxtime.domain.SsoProvider;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {

    /**
     * De quién es esta cuenta del proveedor. Con la persona ya cargada: quien
     * lo pide va a abrirle la sesión.
     */
    @Query("SELECT i FROM identidades_externas i JOIN FETCH i.usuario "
            + "WHERE i.proveedor = :proveedor AND i.sujeto = :sujeto")
    Optional<ExternalIdentity> findByProveedorAndSujeto(
            @Param("proveedor") SsoProvider proveedor, @Param("sujeto") String sujeto);

    Optional<ExternalIdentity> findByUsuarioAndProveedor(User usuario, SsoProvider proveedor);

    List<ExternalIdentity> findByUsuarioOrderByProveedorAsc(User usuario);
}
