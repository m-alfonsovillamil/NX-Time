package com.nxtime.nxtime.service.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Reduce una foto de perfil a un cuadrado de 256x256 JPEG (Fase B2).
 *
 * <b>Se hace en el servidor y no en la app</b>: si el reescalado viviera
 * solo en el cliente, cualquiera que llamara a la API directamente
 * podría dejar 5 MB en la base, y esa foto viajaría entera cada vez que
 * se pinta un avatar. Aquí es una garantía; allí sería una cortesía.
 *
 * Usa {@code javax.imageio}, que ya viene en el JDK. Se descartó una
 * librería de imágenes por una foto de 30 KB: lo que aportaría de más
 * -- mejor remuestreo, respetar la orientación EXIF -- no se nota en un
 * círculo de 256 píxeles. <b>El precio, y conviene saberlo</b>: una foto
 * hecha con el móvil en vertical y guardada con la rotación en los
 * metadatos EXIF saldrá tumbada, porque ImageIO no la aplica. Si algún
 * día molesta, la solución es leer el EXIF (o meter Thumbnailator), no
 * volver a mover esto al cliente.
 */
final class AvatarScaler {

    private AvatarScaler() {
    }

    /** Suficiente para el avatar más grande de la app (72 dp) en pantalla densa. */
    static final int LADO = 256;

    /**
     * Lo más grande que se acepta: 40 megapíxeles, más que cualquier móvil.
     *
     * <p>Revisión de seguridad del 1/10/2026: el límite de 5 MB es del
     * fichero, no de la imagen. Un PNG de 5 MB de un solo color puede decir
     * que mide 50.000 × 50.000, y {@code ImageIO.read} reservaba esos 10.000
     * millones de píxeles antes de mirar nada: una sola subida tumbaba el
     * servicio (512 MB en Render). Ahora se leen las dimensiones de la
     * cabecera, sin decodificar, y se rechaza antes.
     */
    static final long PIXELES_MAXIMOS = 40_000_000L;

    /** Lado corto con el que se decodifica: el doble del avatar, para que el reescalado no pierda calidad. */
    private static final int LADO_DECODIFICADO = 2 * LADO;

    /** La imagen dice medir más de {@link #PIXELES_MAXIMOS}. */
    static final class ImagenDemasiadoGrande extends IOException {
        ImagenDemasiadoGrande(long pixeles) {
            super("La imagen mide " + pixeles + " píxeles; el máximo es " + PIXELES_MAXIMOS + ".");
        }
    }

    /**
     * Recorta al cuadrado central y escala a 256x256 JPEG.
     *
     * Se recorta antes de escalar en vez de deformar: una foto apaisada
     * comprimida a un cuadrado deja caras aplastadas, y en un avatar
     * circular lo que importa es el centro.
     *
     * @return los bytes del JPEG resultante.
     * @throws ImagenDemasiadoGrande si mide más de {@link #PIXELES_MAXIMOS}.
     * @throws IOException si el contenido no se puede leer como imagen.
     */
    static byte[] aAvatar(byte[] original) throws IOException {
        BufferedImage imagen = leerReducida(original);
        BufferedImage cuadrada = recortarAlCentro(imagen);

        BufferedImage destino = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = destino.createGraphics();
        try {
            // Fondo blanco: un PNG con transparencia se guarda como JPEG,
            // que no la tiene, y sin esto las zonas transparentes salen
            // negras.
            lienzo.setColor(Color.WHITE);
            lienzo.fillRect(0, 0, LADO, LADO);
            lienzo.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            lienzo.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            lienzo.drawImage(cuadrada, 0, 0, LADO, LADO, null);
        } finally {
            lienzo.dispose();
        }

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(destino, "jpg", salida);
        return salida.toByteArray();
    }

    /**
     * Lee la imagen ya reducida: primero las dimensiones de la cabecera (sin
     * decodificar nada) y después la imagen saltándose píxeles
     * ({@code setSourceSubsampling}) hasta que el lado corto quede cerca de
     * {@link #LADO_DECODIFICADO}. Una foto de 12 MP del móvil ocupa así unos
     * 300 KB en memoria y no 48 MB, y el resultado es el mismo avatar.
     */
    private static BufferedImage leerReducida(byte[] original) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (lectores == null || !lectores.hasNext()) {
                throw new IOException("El contenido no se puede leer como imagen.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                long pixeles = (long) ancho * alto;
                if (ancho <= 0 || alto <= 0) {
                    throw new IOException("La imagen no tiene dimensiones válidas.");
                }
                if (pixeles > PIXELES_MAXIMOS) {
                    throw new ImagenDemasiadoGrande(pixeles);
                }
                ImageReadParam parametros = lector.getDefaultReadParam();
                int salto = Math.max(1, Math.min(ancho, alto) / LADO_DECODIFICADO);
                parametros.setSourceSubsampling(salto, salto, 0, 0);
                return lector.read(0, parametros);
            } catch (RuntimeException e) {
                // Un fichero con cabecera de PNG pero el cuerpo corrupto puede
                // reventar dentro del lector con cualquier excepción.
                throw new IOException("El contenido no se puede leer como imagen.", e);
            } finally {
                lector.dispose();
            }
        }
    }

    private static BufferedImage recortarAlCentro(BufferedImage imagen) {
        int lado = Math.min(imagen.getWidth(), imagen.getHeight());
        int x = (imagen.getWidth() - lado) / 2;
        int y = (imagen.getHeight() - lado) / 2;
        return imagen.getSubimage(x, y, lado, lado);
    }
}
