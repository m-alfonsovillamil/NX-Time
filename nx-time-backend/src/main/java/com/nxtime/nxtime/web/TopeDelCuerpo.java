package com.nxtime.nxtime.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un tope para lo que se puede mandar en el cuerpo de una petición que no es
 * una subida de ficheros (revisión de seguridad del 17/10/2026, plan J).
 *
 * Las subidas ya tenían el suyo (6 MB, {@code spring.servlet.multipart} en
 * application.yml). El JSON no tenía ninguno: el login, el registro o
 * recuperar la contraseña son públicos, y cualquiera podía mandar cientos de
 * megas que Jackson leía enteros en memoria antes de que nada los rechazara.
 * Nada legítimo de esta API pasa de unos pocos KB; un mega deja sitio de sobra.
 *
 * <ul>
 *   <li>Con {@code Content-Length}, se mira ese número y se corta sin leer
 *       nada: Tomcat no deja leer más allá de lo que dice.</li>
 *   <li>Sin él (el cuerpo llega por trozos), se lee aquí, hasta el tope, y lo
 *       que sigue recibe el cuerpo ya leído. Es lo único que no depende de
 *       cómo envuelva cada lector una excepción a mitad de lectura.</li>
 * </ul>
 *
 * Va el primero de los filtros, detrás del de correlación, para que ningún
 * otro lea antes el cuerpo sin tope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TopeDelCuerpo extends OncePerRequestFilter {

    public static final int MAXIMO_BYTES = 1024 * 1024;

    static final String DEMASIADO_GRANDE =
            "{\"title\":\"Content Too Large\",\"status\":413,\"detail\":\"La petición es demasiado grande.\"}";

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String tipo = request.getContentType();
        return tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long declarado = request.getContentLengthLong();
        if (declarado > MAXIMO_BYTES) {
            rechazar(response);
            return;
        }
        if (declarado >= 0 || !porTrozos(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] cuerpo = request.getInputStream().readNBytes(MAXIMO_BYTES + 1);
        if (cuerpo.length > MAXIMO_BYTES) {
            rechazar(response);
            return;
        }
        filterChain.doFilter(new YaLeida(request, cuerpo), response);
    }

    private static boolean porTrozos(HttpServletRequest request) {
        String transferencia = request.getHeader("Transfer-Encoding");
        return transferencia != null && transferencia.toLowerCase(Locale.ROOT).contains("chunked");
    }

    private static void rechazar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(DEMASIADO_GRANDE);
    }

    /** La petición, con el cuerpo que ya se ha leído aquí. */
    private static final class YaLeida extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        YaLeida(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream datos = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return datos.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return datos.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return datos.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no admitida aquí.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String codificacion = getCharacterEncoding();
            Charset juego = codificacion == null ? StandardCharsets.UTF_8 : Charset.forName(codificacion);
            return new BufferedReader(new InputStreamReader(getInputStream(), juego));
        }
    }
}
