package com.nxtime.nxtime.service.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

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
     * Recorta al cuadrado central y escala a 256x256 JPEG.
     *
     * Se recorta antes de escalar en vez de deformar: una foto apaisada
     * comprimida a un cuadrado deja caras aplastadas, y en un avatar
     * circular lo que importa es el centro.
     *
     * @return los bytes del JPEG resultante.
     * @throws IOException si el contenido no se puede leer como imagen.
     */
    static byte[] aAvatar(byte[] original) throws IOException {
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(original));
        if (imagen == null) {
            // ImageIO devuelve null (no lanza) cuando ningún lector
            // reconoce el contenido: un fichero con cabecera de PNG pero
            // el cuerpo corrupto llega hasta aquí.
            throw new IOException("El contenido no se puede leer como imagen.");
        }

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

    private static BufferedImage recortarAlCentro(BufferedImage imagen) {
        int lado = Math.min(imagen.getWidth(), imagen.getHeight());
        int x = (imagen.getWidth() - lado) / 2;
        int y = (imagen.getHeight() - lado) / 2;
        return imagen.getSubimage(x, y, lado, lado);
    }
}
