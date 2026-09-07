package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentData;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AttachmentResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AttachmentDataRepository;
import com.nxtime.nxtime.repository.AttachmentRepository;
import com.nxtime.nxtime.service.AttachmentService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);

    /**
     * El nombre original se guarda para devolverlo al descargar, así que
     * lo escribe quien sube: se recorta y se limpia antes de aceptarlo.
     */
    private static final int MAXIMO_NOMBRE = 255;

    private final AttachmentRepository attachmentRepository;
    private final AttachmentDataRepository attachmentDataRepository;

    public AttachmentServiceImpl(AttachmentRepository attachmentRepository,
                                 AttachmentDataRepository attachmentDataRepository) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentDataRepository = attachmentDataRepository;
    }

    @Override
    @Transactional
    public AttachmentResponse subir(MultipartFile fichero, AttachmentType tipo, User actor) {
        if (fichero == null || fichero.isEmpty()) {
            throw new BusinessException("No has adjuntado ningún fichero.", HttpStatus.BAD_REQUEST);
        }

        byte[] original = leer(fichero);

        // El MIME sale de los primeros bytes, NO del Content-Type que
        // manda el cliente ni de la extensión: los dos los elige quien
        // sube. Un .exe renombrado a .pdf muere aquí.
        String mimeReal = ContentTypeDetector.detectar(original);
        if (mimeReal == null || !tipo.acepta(mimeReal)) {
            throw new BusinessException(
                    "El fichero no es " + tipo.descripcionDeLoAceptado()
                            + ". Se comprueba su contenido, no su extensión.",
                    HttpStatus.BAD_REQUEST);
        }

        byte[] aGuardar = original;
        String mimeFinal = mimeReal;
        if (tipo == AttachmentType.FOTO) {
            // Se reescala en el servidor y no en la app: allí sería una
            // cortesía, aquí es una garantía (ver ADR 007).
            try {
                aGuardar = AvatarScaler.aAvatar(original);
            } catch (IOException e) {
                // Cabecera válida pero cuerpo ilegible: el fichero
                // empieza como un PNG y no lo es.
                throw new BusinessException(
                        "La imagen no se ha podido procesar. Prueba con otra.", HttpStatus.BAD_REQUEST);
            }
            mimeFinal = "image/jpeg";
        }

        // Un CV y una foto VIGENTES por persona: subir otro reemplaza al
        // anterior. Se borra primero para respetar el UNIQUE, y los
        // bytes se van solos con el ON DELETE CASCADE.
        Optional<Attachment> anterior = attachmentRepository.findByUsuarioAndTipo(actor, tipo);
        anterior.ifPresent(viejo -> {
            attachmentRepository.delete(viejo);
            attachmentRepository.flush();
        });

        Attachment adjunto = attachmentRepository.save(Attachment.builder()
                .empresa(actor.getEmpresa())
                .usuario(actor)
                .tipo(tipo)
                .nombreOriginal(nombreLimpio(fichero.getOriginalFilename(), tipo))
                .mime(mimeFinal)
                .tamanoBytes(aGuardar.length)
                .subidoEn(Instant.now())
                .build());

        attachmentDataRepository.save(AttachmentData.builder()
                .adjuntoId(adjunto.getId())
                .contenido(aGuardar)
                .build());

        log.info("{} ha subido su {} ({} bytes guardados de {} recibidos)",
                actor.getEmail(), tipo, aGuardar.length, original.length);
        return toResponse(adjunto);
    }

    @Override
    public List<AttachmentResponse> listar(User usuario) {
        return attachmentRepository.findByUsuario(usuario).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ContenidoDeAdjunto descargar(long adjuntoId, User actor) {
        Attachment adjunto = deLaMismaEmpresa(adjuntoId, actor);

        // La única lectura de los bytes en todo el servicio, y explícita:
        // por eso están en su propia tabla (ver ADR 007).
        AttachmentData datos = attachmentDataRepository.findById(adjuntoId)
                .orElseThrow(() -> new ResourceNotFoundException("El adjunto no tiene contenido."));

        return new ContenidoDeAdjunto(
                datos.getContenido(), adjunto.getNombreOriginal(), adjunto.getMime(), adjunto.getTipo());
    }

    @Override
    @Transactional
    public void borrar(long adjuntoId, User actor) {
        Attachment adjunto = attachmentRepository.findById(adjuntoId)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado."));

        // Más estricto que el aislamiento entre empresas: el CV es de
        // una PERSONA. Un gestor puede leerlo (lo necesita para valorar
        // una candidatura) pero no borrárselo a nadie.
        if (adjunto.getUsuario().getId() != actor.getId()) {
            throw new TenantAccessException("Solo puedes borrar tus propios adjuntos.");
        }

        attachmentRepository.delete(adjunto);
        log.info("{} ha borrado su {}", actor.getEmail(), adjunto.getTipo());
    }

    /**
     * Leer un adjunto ajeno sí es cosa de empresa: un gestor necesita
     * ver el CV de su equipo.
     */
    private Attachment deLaMismaEmpresa(long adjuntoId, User actor) {
        Attachment adjunto = attachmentRepository.findById(adjuntoId)
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado."));
        if (adjunto.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes ver adjuntos de otra empresa.");
        }
        return adjunto;
    }

    private byte[] leer(MultipartFile fichero) {
        try {
            return fichero.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se ha podido leer el fichero.", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * El nombre lo elige quien sube, así que no se guarda tal cual: se
     * quita cualquier ruta (un navegador puede mandar "C:\\fakepath\\cv.pdf",
     * y un cliente hecho a mano lo que quiera) y se recorta a lo que cabe
     * en la columna.
     */
    private String nombreLimpio(String original, AttachmentType tipo) {
        String porDefecto = tipo == AttachmentType.CV ? "cv.pdf" : "foto.jpg";
        if (original == null || original.isBlank()) {
            return porDefecto;
        }
        String soloNombre = original.replace('\\', '/');
        soloNombre = soloNombre.substring(soloNombre.lastIndexOf('/') + 1).trim();
        if (soloNombre.isEmpty()) {
            return porDefecto;
        }
        if (soloNombre.length() > MAXIMO_NOMBRE) {
            soloNombre = soloNombre.substring(0, MAXIMO_NOMBRE);
        }
        // Una FOTO se guarda SIEMPRE como JPEG, así que su nombre no
        // puede seguir diciendo ".png": quien la descargue se llevaría
        // un fichero cuya extensión miente sobre su contenido.
        return tipo == AttachmentType.FOTO ? conExtensionJpg(soloNombre) : soloNombre;
    }

    private String conExtensionJpg(String nombre) {
        int punto = nombre.lastIndexOf('.');
        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
        return base + ".jpg";
    }

    private AttachmentResponse toResponse(Attachment adjunto) {
        return new AttachmentResponse(
                adjunto.getId(),
                adjunto.getTipo(),
                adjunto.getNombreOriginal(),
                adjunto.getMime(),
                adjunto.getTamanoBytes(),
                adjunto.getSubidoEn());
    }
}
