package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario (empleado o gestor). Tabla "usuarios".
 *
 * Ya NO implementa UserDetails (ver auditoría, defectos de diseño):
 * ese acoplamiento a Spring Security se traslada al adaptador
 * {@link com.nxtime.nxtime.security.SecurityUser}, que envuelve esta
 * entidad. Esto es, entre otras cosas, lo que permite que ningún
 * controlador pueda ya devolver por accidente la contraseña cifrada al
 * serializar un User -- la propia clase ya no expone getPassword().
 *
 * IDs con GenerationType.IDENTITY desde la Fase 3 (ver Company.java).
 *
 * "activo"/"fechaBaja" desde la Fase 4: antes los flags de cuenta de
 * UserDetails estaban cableados a true sin excepción (ver auditoría,
 * defectos de diseño) -- no se podía dar de baja a nadie. Un usuario
 * dado de baja no puede autenticarse ({@link
 * com.nxtime.nxtime.security.SecurityUser#isEnabled()}), pero sus
 * fichajes y ausencias pasadas se conservan tal cual (requisito legal
 * de trazabilidad, no se borran ni se anonimizan).
 */
@Entity(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true)
    private String email;

    private String nombre;

    private String contrasena;

    @Enumerated(EnumType.STRING)
    private Role rol;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @Builder.Default
    private boolean activo = true;

    private Instant fechaBaja;

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
