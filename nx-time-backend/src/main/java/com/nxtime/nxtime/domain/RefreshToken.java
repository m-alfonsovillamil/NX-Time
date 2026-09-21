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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token persistido, hasheado y rotatorio (Fase 4, reforzado en A11).
 *
 * Es una cadena opaca (UUID aleatorio), no un JWT: al vivir en base de datos,
 * revocarlo es un simple UPDATE, sin tener que esperar a que expire por su
 * cuenta -- justo lo que un JWT firmado no permite sin mantener una lista de
 * revocación aparte.
 *
 * <h2>Lo que cambió en septiembre de 2026, y por qué</h2>
 *
 * <b>El token ya no se guarda.</b> Solo su {@code sha256}. Antes la tabla
 * contenía el valor en claro, así que un volcado de la base --una copia de
 * seguridad, un acceso de lectura mal dado-- entregaba sesiones vivas de
 * treinta días listas para usar.
 *
 * <b>Rota.</b> Cada renovación emite uno nuevo y deja el anterior marcado. Un
 * token robado sirve, como mucho, hasta que su dueño renueve.
 *
 * <b>Reutilizar uno rotado revoca la familia entera.</b> Esta es la parte que
 * de verdad protege, y la razón de que exista {@link #familia}: si llegan dos
 * peticiones con el mismo token rotado, hay dos clientes usando la misma
 * cadena y solo uno puede ser el legítimo. No hay forma de saber cuál, así que
 * se cierran las dos y que se vuelva a entrar. Es ruidoso a propósito: un robo
 * silencioso dura un mes, y esto se nota el mismo día.
 *
 * Con la app Android todo esto era discutible pero acotado. Con un cliente web
 * deja de serlo --el token pasa a vivir en un navegador, donde cualquier XSS lo
 * alcanza-- y es lo que hace defendible ese paso.
 */
@Entity(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    /**
     * De dónde salió el token, y cuánto dura por eso.
     *
     * Un navegador no merece treinta días: el token vive en una máquina que
     * carga código de terceros y que a menudo es compartida. Un móvil con la
     * app instalada es otra cosa, y obligar a entrar cada doce horas ahí sería
     * castigar al usuario sin ganar nada.
     */
    public enum Origen {
        ANDROID(Duration.ofDays(30)),
        IOS(Duration.ofDays(30)),
        WEB(Duration.ofHours(12));

        private final Duration duracion;

        Origen(Duration duracion) {
            this.duracion = duracion;
        }

        public Duration duracion() {
            return duracion;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /**
     * El sha256 del token, en hexadecimal. El token en claro solo existe en el
     * cliente: ni se guarda ni se puede recuperar desde aquí.
     *
     * SHA-256 y no BCrypt a propósito. Esto no es una contraseña que alguien
     * elige --es un UUID aleatorio de 122 bits--, así que no hay diccionario
     * contra el que defenderse; lo que se evita es que la base contenga algo
     * reutilizable. BCrypt en cada renovación costaría decenas de milisegundos
     * por petición sin comprar nada a cambio.
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    /**
     * Todos los tokens que descienden de un mismo login.
     *
     * Se conserva al rotar, así que la cadena entera comparte familia. Es lo
     * que permite echar a un intruso sin tocar las demás sesiones de esa
     * persona: el móvil sigue dentro aunque se cierre la del navegador.
     */
    @Column(nullable = false)
    private UUID familia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Origen origen = Origen.ANDROID;

    private Instant expiraEn;

    @Builder.Default
    private boolean revocado = false;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    /** Cuándo se cambió por otro. Null si sigue siendo el vigente de su familia. */
    @Column(name = "rotado_en")
    private Instant rotadoEn;

    /** El que lo sustituyó. Va con {@link #rotadoEn}: o están los dos o ninguno. */
    @ManyToOne
    @JoinColumn(name = "sustituido_por_id")
    private RefreshToken sustituidoPor;

    private Instant creadoEn;

    public boolean estaVivo() {
        return !revocado && rotadoEn == null && expiraEn.isAfter(Instant.now());
    }

    /** Ya se usó para renovar: si vuelve a aparecer, alguien tiene una copia. */
    public boolean estaRotado() {
        return rotadoEn != null;
    }

    /** Marca la revocación en los dos campos a la vez, que la base exige coherentes. */
    public void revocar(Instant cuando) {
        this.revocado = true;
        this.revocadoEn = cuando;
    }
}
