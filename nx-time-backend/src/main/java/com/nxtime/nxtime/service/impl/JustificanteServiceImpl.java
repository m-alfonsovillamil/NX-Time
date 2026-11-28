package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.DatoConsultado;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.JustificanteResponse;
import com.nxtime.nxtime.dto.JustificantesResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.service.JustificanteService;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * El justificante de una ausencia (ADR 062).
 *
 * Con JdbcTemplate y sin entidad, a propósito: los bytes van en la misma fila
 * que el nombre, y una entidad JPA los cargaría en cada lectura (el motivo de
 * que {@code adjuntos} tenga dos tablas, ADR 007). Aquí solo la consulta de
 * descargar pide {@code contenido}.
 */
@Service
public class JustificanteServiceImpl implements JustificanteService {

    private static final Logger log = LoggerFactory.getLogger(JustificanteServiceImpl.class);

    /** RRHH y ADMIN: ver el justificante de otra persona. */
    static final String LEER_DE_OTROS = "ausencia:justificante:leer";

    private static final Set<String> TIPOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final int MAXIMO_NOMBRE = 255;
    /** Lo que cabe en una consulta de la lista: una página larga de ausencias. */
    private static final int MAXIMO_PETICIONES = 200;

    private static final RowMapper<JustificanteResponse> FILA = (rs, n) -> new JustificanteResponse(
            rs.getLong("peticion_id"), rs.getString("nombre_original"), rs.getString("mime"),
            rs.getLong("tamano_bytes"), rs.getTimestamp("subido_en").toInstant());

    private final JdbcTemplate jdbc;
    private final AbsenceRequestRepository peticiones;
    private final RegistroDeAccesos accesos;

    public JustificanteServiceImpl(JdbcTemplate jdbc, AbsenceRequestRepository peticiones, RegistroDeAccesos accesos) {
        this.jdbc = jdbc;
        this.peticiones = peticiones;
        this.accesos = accesos;
    }

    @Override
    public JustificantesResponse de(List<Long> ids, User actor) {
        boolean activo = activo(actor);
        if (ids == null || ids.isEmpty()) {
            return new JustificantesResponse(activo, List.of());
        }
        if (ids.size() > MAXIMO_PETICIONES) {
            throw new BusinessException("Como mucho " + MAXIMO_PETICIONES + " ausencias a la vez.",
                    HttpStatus.BAD_REQUEST);
        }
        boolean deOtros = RoleAuthorities.tiene(actor, LEER_DE_OTROS);
        String marcas = String.join(",", Collections.nCopies(ids.size(), "?"));
        Object[] argumentos = new Object[ids.size() + 3];
        argumentos[0] = actor.getEmpresa().getId();
        argumentos[1] = actor.getId();
        argumentos[2] = deOtros;
        for (int i = 0; i < ids.size(); i++) {
            argumentos[i + 3] = ids.get(i);
        }
        // La empresa y quién puede, dentro de la consulta: lo que no se puede
        // ver no sale, ni siquiera que existe.
        List<JustificanteResponse> lista = jdbc.query("""
                SELECT j.peticion_id, j.nombre_original, j.mime, j.tamano_bytes, j.subido_en
                  FROM justificantes_ausencia j
                  JOIN peticiones_ausencia p ON p.id = j.peticion_id
                 WHERE p.empresa_id = ?
                   AND (p.usuario_id = ? OR ?)
                   AND j.peticion_id IN (""" + marcas + ") ORDER BY j.peticion_id", FILA, argumentos);
        return new JustificantesResponse(activo, lista);
    }

    @Override
    @Transactional
    public JustificanteResponse subir(long peticionId, MultipartFile fichero, User actor) {
        AbsenceRequest peticion = propia(peticionId, actor);
        if (!activo(actor)) {
            throw new BusinessException("Tu empresa no tiene activados los justificantes en las ausencias.",
                    HttpStatus.CONFLICT);
        }
        // Una ausencia cancelada o rechazada no se va a disfrutar: no hay nada
        // que justificar. La app y la web ofrecían adjuntarlo igual, y se
        // guardaba (revisión del 28/11/2026). Quitar el que ya hubiera sí se
        // puede: es lo propio, y no deja nada a medias.
        if (peticion.getEstado() == AbsenceStatus.CANCELADA || peticion.getEstado() == AbsenceStatus.RECHAZADA) {
            throw new BusinessException("Esa ausencia está " + (peticion.getEstado() == AbsenceStatus.CANCELADA
                    ? "cancelada" : "rechazada") + ": ya no lleva justificante.", HttpStatus.CONFLICT);
        }
        if (fichero == null || fichero.isEmpty()) {
            throw new BusinessException("No has adjuntado ningún fichero.", HttpStatus.BAD_REQUEST);
        }
        byte[] bytes;
        try {
            bytes = fichero.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se ha podido leer el fichero.", HttpStatus.BAD_REQUEST);
        }
        // El tipo, por los primeros bytes: ni la extensión ni el Content-Type
        // los decide el servidor.
        String mime = ContentTypeDetector.detectar(bytes);
        if (mime == null || !TIPOS.contains(mime)) {
            throw new BusinessException("El justificante tiene que ser un PDF, un JPEG o un PNG. "
                    + "Se comprueba su contenido, no su extensión.", HttpStatus.BAD_REQUEST);
        }
        String nombre = nombreLimpio(fichero.getOriginalFilename(), mime);
        jdbc.update("""
                INSERT INTO justificantes_ausencia (peticion_id, nombre_original, mime, tamano_bytes, subido_en, contenido)
                VALUES (?, ?, ?, ?, now(), ?)
                ON CONFLICT (peticion_id) DO UPDATE
                   SET nombre_original = EXCLUDED.nombre_original, mime = EXCLUDED.mime,
                       tamano_bytes = EXCLUDED.tamano_bytes, subido_en = EXCLUDED.subido_en,
                       contenido = EXCLUDED.contenido
                """, peticion.getId(), nombre, mime, (long) bytes.length, bytes);
        log.info("{} ha subido el justificante de la ausencia {} ({} bytes)", actor.getId(), peticionId, bytes.length);
        return metadatos(peticionId);
    }

    @Override
    public Contenido descargar(long peticionId, User actor) {
        AbsenceRequest peticion = deLaEmpresa(peticionId, actor);
        long dueno = peticion.getUsuario().getId();
        if (dueno != actor.getId() && !RoleAuthorities.tiene(actor, LEER_DE_OTROS)) {
            log.warn("{} ha intentado bajar el justificante de la ausencia {}, que no es suya", actor.getId(), peticionId);
            throw new TenantAccessException("Solo pueden ver un justificante la persona y RRHH.");
        }
        List<Contenido> filas = jdbc.query(
                "SELECT contenido, nombre_original, mime FROM justificantes_ausencia WHERE peticion_id = ?",
                (rs, n) -> new Contenido(rs.getBytes("contenido"), rs.getString("nombre_original"), rs.getString("mime")),
                peticionId);
        if (filas.isEmpty()) {
            throw new ResourceNotFoundException("Esa ausencia no tiene justificante.");
        }
        // El de otra persona queda anotado (ADR 047); el propio, no.
        accesos.anotar(actor, DatoConsultado.JUSTIFICANTE, dueno, "Ausencia " + peticionId);
        return filas.get(0);
    }

    @Override
    @Transactional
    public void borrar(long peticionId, User actor) {
        propia(peticionId, actor);
        if (jdbc.update("DELETE FROM justificantes_ausencia WHERE peticion_id = ?", peticionId) == 0) {
            throw new ResourceNotFoundException("Esa ausencia no tiene justificante.");
        }
        log.info("{} ha quitado el justificante de la ausencia {}", actor.getId(), peticionId);
    }

    @Override
    @Transactional
    public JustificantesResponse activar(boolean activo, User actor) {
        jdbc.update("UPDATE empresas SET justificantes_en_ausencias = ? WHERE id = ?", activo, actor.getEmpresa().getId());
        log.info("La empresa {} {} los justificantes en las ausencias", actor.getEmpresa().getId(),
                activo ? "activa" : "desactiva");
        return new JustificantesResponse(activo, List.of());
    }

    private boolean activo(User actor) {
        Boolean activo = jdbc.queryForObject(
                "SELECT justificantes_en_ausencias FROM empresas WHERE id = ?", Boolean.class, actor.getEmpresa().getId());
        return Boolean.TRUE.equals(activo);
    }

    private AbsenceRequest deLaEmpresa(long peticionId, User actor) {
        AbsenceRequest peticion = peticiones.findById(peticionId)
                .orElseThrow(() -> new ResourceNotFoundException("Ausencia no encontrada."));
        if (peticion.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa ausencia es de otra empresa.");
        }
        return peticion;
    }

    /** Subir y quitar, solo en lo propio: RRHH lo puede ver, pero no cambiárselo a nadie. */
    private AbsenceRequest propia(long peticionId, User actor) {
        AbsenceRequest peticion = deLaEmpresa(peticionId, actor);
        if (peticion.getUsuario().getId() != actor.getId()) {
            throw new TenantAccessException("Solo puedes adjuntar justificantes a tus ausencias.");
        }
        return peticion;
    }

    private JustificanteResponse metadatos(long peticionId) {
        return jdbc.queryForObject("""
                SELECT peticion_id, nombre_original, mime, tamano_bytes, subido_en
                  FROM justificantes_ausencia WHERE peticion_id = ?
                """, FILA, peticionId);
    }

    /** Sin rutas (un navegador manda «C:\fakepath\…») y que quepa en la columna. */
    static String nombreLimpio(String original, String mime) {
        String porDefecto = "justificante." + switch (mime) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            default -> "jpg";
        };
        if (original == null || original.isBlank()) {
            return porDefecto;
        }
        String solo = original.replace('\\', '/');
        solo = solo.substring(solo.lastIndexOf('/') + 1).trim();
        if (solo.isEmpty()) {
            return porDefecto;
        }
        return solo.length() > MAXIMO_NOMBRE ? solo.substring(0, MAXIMO_NOMBRE) : solo;
    }
}
