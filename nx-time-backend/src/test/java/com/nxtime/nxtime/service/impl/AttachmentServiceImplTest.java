package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentData;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AttachmentResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AttachmentDataRepository;
import com.nxtime.nxtime.repository.AttachmentRepository;
import com.nxtime.nxtime.service.AttachmentService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/**
 * El CV y la foto (Fase B2).
 *
 * Lo que de verdad hay que probar aquí es que <b>el tipo se decide por
 * el contenido y no por lo que diga el cliente</b>: la extensión y el
 * Content-Type los elige quien sube. Y que la foto sale reescalada, que
 * es lo que impide que 5 MB acaben viajando en cada avatar.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AttachmentDataRepository attachmentDataRepository;

    private AttachmentServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User companero;

    @BeforeEach
    void setUp() {
        service = new AttachmentServiceImpl(attachmentRepository, attachmentDataRepository);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra Empresa").build();
        empleado = User.builder().id(10L).email("ana@nxtime.test").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).build();
        companero = User.builder().id(11L).email("luis@nxtime.test").nombre("Luis")
                .rol(Role.EMPLEADO).empresa(empresa).build();
    }

    // ------------------------------------------------------------------
    // Ficheros de prueba
    // ------------------------------------------------------------------

    /** Un PDF de verdad en lo que importa: su cabecera. */
    private static byte[] pdf() {
        byte[] contenido = new byte[64];
        System.arraycopy("%PDF-1.7".getBytes(), 0, contenido, 0, 8);
        return contenido;
    }

    /** Un PNG apaisado de verdad, generado al vuelo. */
    private static byte[] pngApaisado(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private static MockMultipartFile fichero(String nombre, String mimeDeclarado, byte[] contenido) {
        return new MockMultipartFile("fichero", nombre, mimeDeclarado, contenido);
    }

    private void sinAdjuntoPrevio() {
        when(attachmentRepository.findByUsuarioAndTipo(any(), any())).thenReturn(Optional.empty());
        when(attachmentRepository.save(any())).thenAnswer(i -> {
            Attachment a = i.getArgument(0);
            a.setId(99L);
            return a;
        });
    }

    // ------------------------------------------------------------------
    // Validación por contenido
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un ejecutable renombrado a .pdf y declarado como PDF se rechaza")
    void subir_ejecutableDisfrazadoDePdf_lanza400() {
        // "MZ" es la cabecera de un .exe de Windows. El nombre y el
        // Content-Type dicen PDF, y los dos los ha elegido quien sube.
        byte[] exe = {0x4D, 0x5A, (byte) 0x90, 0x00};

        assertThatThrownBy(() -> service.subir(
                fichero("cv.pdf", "application/pdf", exe), AttachmentType.CV, empleado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("contenido");

        verify(attachmentRepository, never()).save(any());
        verify(attachmentDataRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una imagen no vale como CV aunque sea una imagen válida")
    void subir_imagenComoCv_lanza400() throws IOException {
        byte[] png = pngApaisado(10, 10);

        assertThatThrownBy(() -> service.subir(
                fichero("cv.pdf", "application/pdf", png), AttachmentType.CV, empleado))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un PDF no vale como foto")
    void subir_pdfComoFoto_lanza400() {
        assertThatThrownBy(() -> service.subir(
                fichero("foto.jpg", "image/jpeg", pdf()), AttachmentType.FOTO, empleado))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un fichero vacío se rechaza antes de mirarle el contenido")
    void subir_ficheroVacio_lanza400() {
        assertThatThrownBy(() -> service.subir(
                fichero("cv.pdf", "application/pdf", new byte[0]), AttachmentType.CV, empleado))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Una cabecera de PNG con el cuerpo roto da 400, no un 500")
    void subir_imagenCorrupta_lanza400() {
        // Pasa la detección por cabecera pero ImageIO no puede leerla:
        // es el caso que deja el reescalado a medias si no se trata.
        byte[] falsoPng = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03
        };

        assertThatThrownBy(() -> service.subir(
                fichero("foto.png", "image/png", falsoPng), AttachmentType.FOTO, empleado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("imagen");
    }

    @Test
    @DisplayName("Un PDF de verdad se guarda tal cual, con su MIME real")
    void subir_pdfValido_seGuardaIntacto() {
        sinAdjuntoPrevio();
        byte[] contenido = pdf();

        AttachmentResponse respuesta = service.subir(
                fichero("mi cv.pdf", "application/octet-stream", contenido),
                AttachmentType.CV, empleado);

        assertThat(respuesta.mime()).isEqualTo("application/pdf");
        assertThat(respuesta.tamanoBytes()).isEqualTo(contenido.length);

        ArgumentCaptor<AttachmentData> datos = ArgumentCaptor.captor();
        verify(attachmentDataRepository).save(datos.capture());
        assertThat(datos.getValue().getContenido()).isEqualTo(contenido);
    }

    // ------------------------------------------------------------------
    // Reescalado de la foto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una foto grande sale a 256x256 JPEG, y mucho más pequeña")
    void subir_foto_seReescala() throws IOException {
        sinAdjuntoPrevio();
        byte[] original = pngApaisado(1200, 800);

        AttachmentResponse respuesta = service.subir(
                fichero("foto.png", "image/png", original), AttachmentType.FOTO, empleado);

        // El MIME guardado es JPEG aunque llegara un PNG: lo que se
        // guarda no es lo que se envió.
        assertThat(respuesta.mime()).isEqualTo("image/jpeg");
        assertThat(respuesta.tamanoBytes()).isLessThan(original.length);

        ArgumentCaptor<AttachmentData> datos = ArgumentCaptor.captor();
        verify(attachmentDataRepository).save(datos.capture());

        BufferedImage guardada = ImageIO.read(
                new java.io.ByteArrayInputStream(datos.getValue().getContenido()));
        assertThat(guardada.getWidth()).isEqualTo(256);
        assertThat(guardada.getHeight()).isEqualTo(256);
    }

    @Test
    @DisplayName("Una foto apaisada se recorta al centro, no se deforma")
    void subir_fotoApaisada_seRecortaCuadrada() throws IOException {
        sinAdjuntoPrevio();

        service.subir(fichero("foto.png", "image/png", pngApaisado(1000, 250)),
                AttachmentType.FOTO, empleado);

        ArgumentCaptor<AttachmentData> datos = ArgumentCaptor.captor();
        verify(attachmentDataRepository).save(datos.capture());
        BufferedImage guardada = ImageIO.read(
                new java.io.ByteArrayInputStream(datos.getValue().getContenido()));
        // Cuadrada: si se hubiera estirado seguiría siendo 4:1.
        assertThat(guardada.getWidth()).isEqualTo(guardada.getHeight());
    }

    // ------------------------------------------------------------------
    // Reemplazo, propiedad y descarga
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Subir otro CV reemplaza el anterior en vez de acumular versiones")
    void subir_conUnoPrevio_loReemplaza() {
        Attachment viejo = Attachment.builder()
                .id(5L).empresa(empresa).usuario(empleado).tipo(AttachmentType.CV)
                .nombreOriginal("viejo.pdf").mime("application/pdf")
                .tamanoBytes(10).subidoEn(Instant.now()).build();
        when(attachmentRepository.findByUsuarioAndTipo(empleado, AttachmentType.CV))
                .thenReturn(Optional.of(viejo));
        when(attachmentRepository.save(any())).thenAnswer(i -> {
            Attachment a = i.getArgument(0);
            a.setId(99L);
            return a;
        });

        service.subir(fichero("nuevo.pdf", "application/pdf", pdf()), AttachmentType.CV, empleado);

        verify(attachmentRepository).delete(viejo);
    }

    @Test
    @DisplayName("El nombre se limpia de rutas: un navegador manda C:\\fakepath\\cv.pdf")
    void subir_nombreConRuta_seQuedaSoloElFichero() {
        sinAdjuntoPrevio();

        AttachmentResponse respuesta = service.subir(
                fichero("C:\\fakepath\\mi cv.pdf", "application/pdf", pdf()),
                AttachmentType.CV, empleado);

        assertThat(respuesta.nombreOriginal()).isEqualTo("mi cv.pdf");
    }

    @Test
    @DisplayName("Una foto subida como .png se guarda con nombre .jpg, que es lo que de verdad es")
    void subir_foto_elNombreAcabaEnJpg() throws IOException {
        sinAdjuntoPrevio();

        AttachmentResponse respuesta = service.subir(
                fichero("mi retrato.png", "image/png", pngApaisado(400, 400)),
                AttachmentType.FOTO, empleado);

        // Si conservara el .png, quien la descargue se llevaría un
        // fichero cuya extensión miente sobre su contenido.
        assertThat(respuesta.nombreOriginal()).isEqualTo("mi retrato.jpg");
        assertThat(respuesta.mime()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Un adjunto sin nombre recibe uno por defecto según su tipo")
    void subir_sinNombre_usaUnoPorDefecto() {
        sinAdjuntoPrevio();

        AttachmentResponse respuesta = service.subir(
                fichero(null, "application/pdf", pdf()), AttachmentType.CV, empleado);

        assertThat(respuesta.nombreOriginal()).isEqualTo("cv.pdf");
    }

    @Test
    @DisplayName("Un compañero de la misma empresa SÍ puede descargar tu CV")
    void descargar_companeroDeEmpresa_puede() {
        Attachment adjunto = Attachment.builder()
                .id(5L).empresa(empresa).usuario(empleado).tipo(AttachmentType.CV)
                .nombreOriginal("cv.pdf").mime("application/pdf").build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(adjunto));
        when(attachmentDataRepository.findById(5L)).thenReturn(Optional.of(
                AttachmentData.builder().adjuntoId(5L).contenido(pdf()).build()));

        // Un gestor necesita leer el CV de su equipo: descargar es de
        // empresa, no de persona.
        AttachmentService.ContenidoDeAdjunto contenido = service.descargar(5L, companero);

        assertThat(contenido.nombreOriginal()).isEqualTo("cv.pdf");
        assertThat(contenido.tipo()).isEqualTo(AttachmentType.CV);
    }

    @Test
    @DisplayName("Alguien de OTRA empresa no puede descargarlo")
    void descargar_otraEmpresa_lanzaTenantAccess() {
        User ajeno = User.builder().id(99L).email("ajeno@otra.test").nombre("Ajeno")
                .rol(Role.GESTOR).empresa(otraEmpresa).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(Attachment.builder()
                .id(5L).empresa(empresa).usuario(empleado).tipo(AttachmentType.CV).build()));

        assertThatThrownBy(() -> service.descargar(5L, ajeno))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("otra empresa");
    }

    @Test
    @DisplayName("Un compañero NO puede borrarte el CV, aunque pueda leerlo")
    void borrar_deOtraPersona_lanzaTenantAccess() {
        Attachment adjunto = Attachment.builder()
                .id(5L).empresa(empresa).usuario(empleado).tipo(AttachmentType.CV).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(adjunto));

        // La asimetría del servicio: leer es de empresa, borrar es de
        // persona.
        assertThatThrownBy(() -> service.borrar(5L, companero))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("tus propios");

        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Borrar el propio sí funciona")
    void borrar_propio_loBorra() {
        Attachment adjunto = Attachment.builder()
                .id(5L).empresa(empresa).usuario(empleado).tipo(AttachmentType.CV).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(adjunto));

        service.borrar(5L, empleado);

        verify(attachmentRepository).delete(adjunto);
    }

    @Test
    @DisplayName("Un adjunto que no existe da 404")
    void descargar_inexistente_lanzaResourceNotFound() {
        when(attachmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.descargar(404L, empleado))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Listar no toca los bytes: por eso están en otra tabla")
    void listar_noLeeElContenido() {
        when(attachmentRepository.findByUsuario(empleado)).thenReturn(java.util.List.of(
                Attachment.builder().id(5L).empresa(empresa).usuario(empleado)
                        .tipo(AttachmentType.CV).nombreOriginal("cv.pdf")
                        .mime("application/pdf").tamanoBytes(1024).subidoEn(Instant.now()).build()));

        assertThat(service.listar(empleado)).hasSize(1);

        verify(attachmentDataRepository, never()).findById(any());
    }
}
