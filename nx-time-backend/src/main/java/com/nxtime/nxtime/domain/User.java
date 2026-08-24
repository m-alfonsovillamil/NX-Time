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
import jakarta.persistence.TableGenerator;
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
 */
@Entity(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @TableGenerator(
            name = "usuario_gen",
            table = "id_generator",
            pkColumnName = "gen_name",
            valueColumnName = "gen_val",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "usuario_gen")
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
