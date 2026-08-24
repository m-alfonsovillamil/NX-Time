package com.nxtime.nxtime.dominio

import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/*
 * llamada a "usuarios".
 */
@Entity(name = "usuarios")
data class Usuario(

    @Id
    /*
     * Usa la tabla "id_generator" para crear IDs únicos.
     */
    @TableGenerator(
        name = "usuario_gen",
        table = "id_generator",
        pkColumnName = "gen_name",
        valueColumnName = "gen_val",
        allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "usuario_gen")
    val id: Long = 0,

    @Column(unique = true)
    val email: String,

    val nombre: String,

    /*
     * Es variable para que podamos cambiar la contraseña
     */
    var contrasena: String,

    @Enumerated(EnumType.STRING)
    val rol: Rol,

    /*
     * Muchos usuarios pertenecen a Una empresa.
     */
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    val empresa: Empresa

) : UserDetails {

    /*
     * Estos métodos le dicen a Spring Security cómo tratar a este usuario.
     */

    /*
     * Le da a Spring el "Rol" del usuario
     */
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_" + rol.name))
    }

    override fun getPassword(): String = contrasena
    override fun getUsername(): String = email


    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}