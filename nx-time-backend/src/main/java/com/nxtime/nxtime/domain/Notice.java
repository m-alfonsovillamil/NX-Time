package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aviso dentro de la aplicación (Fase A). Tabla "avisos".
 *
 * Es el hermano persistente del correo: el mismo evento que dispara un
 * email en {@link com.nxtime.nxtime.notification.NotificationListener}
 * escribe una fila aquí. El correo es una cortesía que se puede perder
 * -- {@link com.nxtime.nxtime.notification.EmailSender} se traga los
 * fallos de SMTP a propósito -- y sin esta tabla no quedaba ni rastro
 * dentro de la aplicación de que se hubiera intentado avisar.
 *
 * {@code rutaDestino} guarda un destino LÓGICO ("ausencias" ,
 * "ausencias-equipo/pendientes"), no la ruta de navegación de un
 * cliente concreto: el cliente la traduce a su propio grafo y, si no
 * conoce el símbolo (backend más nuevo que la app instalada), deja el
 * aviso legible pero no navegable. Ver el comentario largo de
 * {@code V6__avisos.sql}.
 *
 * <b>Sin {@code @Version} a propósito</b>, igual que {@link
 * VacationBalance}: un aviso se escribe una vez y después solo se marca
 * como leído. No hay escenario de edición concurrente que justifique
 * una columna más.
 */
@Entity(name = "avisos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "destinatario_id")
    private User destinatario;

    @Enumerated(EnumType.STRING)
    private NoticeType tipo;

    private String titulo;

    private String cuerpo;

    private String rutaDestino;

    @Builder.Default
    private boolean leido = false;

    private Instant creadoEn;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notice other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
