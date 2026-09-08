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
import com.nxtime.nxtime.repository.JobApplicationRepository;
import com.nxtime.nxtime.service.AttachmentService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
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

    /**
     * Solo para saber si un adjunto está congelado por una candidatura
     * (Fase H). Es la única dependencia que este servicio tiene de otra
     * fase, y va aquí y no al revés porque quien no puede borrar es
     * este: la regla la impone la candidatura.
     */
    private final JobApplicationRepository jobApplicationRepository;

    public AttachmentServiceImpl(AttachmentRepository attachmentRepository,
                                 AttachmentDataRepository attachmentDataRepository,
                                 JobApplicationRepository jobApplicationRepository) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentDataRepository = attachmentDataRepository;
        this.jobApplicationRepository = jobApplicationRepository;
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
        // anterior. Se retira primero para respetar el índice único
        // parcial (WHERE vigente).
        attachmentRepository.findByUsuarioAndTipoAndVigenteTrue(actor, tipo)
                .ifPresent(this::retirar);
        attachmentRepository.flush();

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
        // Solo los vigentes: los que congeló una candidatura siguen en
        // la tabla, pero en el perfil no pintan nada -- ahí se enseña lo
        // que la persona tiene ahora, no su historial de currículums.
        return attachmentRepository.findByUsuarioAndVigenteTrue(usuario).stream()
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

        retirar(adjunto);
        log.info("{} ha retirado su {}", actor.getEmail(), adjunto.getTipo());
    }

    /**
     * Quita un adjunto de en medio: lo borra si nadie lo referencia, y
     * si alguien lo referencia lo deja de vigente (Fase H).
     *
     * <b>Un CV que una candidatura congeló no se puede destruir.</b> Lo
     * impide la propia base ({@code fk_candidaturas_cv ... RESTRICT}),
     * y hacerlo sería borrar lo que un gestor leyó en enero: el
     * expediente de esa candidatura se quedaría sin el documento sobre
     * el que se decidió.
     *
     * El resultado de cara a quien lo pide es el mismo en los dos casos
     * — deja de estar en su perfil y no se puede volver a descargar
     * desde ahí —, y por eso no hay un 409 aquí: negarle borrar su
     * propio CV por algo que hizo el mes pasado sería incomprensible
     * desde la pantalla. Lo que no se puede es fingir que los bytes
     * desaparecen, y eso lo dice la documentación del endpoint.
     */
    private void retirar(Attachment adjunto) {
        if (jobApplicationRepository.existsByCv_Id(adjunto.getId())) {
            adjunto.setVigente(false);
            attachmentRepository.save(adjunto);
            return;
        }
        // Sin candidaturas detrás no hay nada que conservar: los bytes se
        // van solos con el ON DELETE CASCADE de adjunto_datos.
        attachmentRepository.delete(adjunto);
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
