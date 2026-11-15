package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.CategoriaDeAviso;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.CambioDePreferencia;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.PreferenciaDeAviso;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.service.PreferenciasDeAvisosService;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Con JdbcTemplate y sin entidad: es una tabla de cuatro columnas que se lee
 * por su clave, y la escritura es un UPSERT, que JPA no sabe hacer.
 *
 * Sin fila, todo llega: así, quien nunca ha entrado en Ajustes recibe lo mismo
 * que hasta ahora, y la tabla solo crece con quien cambia algo.
 */
@Service
@Transactional(readOnly = true)
public class PreferenciasDeAvisosServiceImpl implements PreferenciasDeAvisosService {

    private final JdbcTemplate jdbc;

    public PreferenciasDeAvisosServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PreferenciaDeAviso> mias(User usuario) {
        Map<CategoriaDeAviso, boolean[]> guardadas = new EnumMap<>(CategoriaDeAviso.class);
        jdbc.query("SELECT categoria, correo, push FROM preferencias_avisos WHERE usuario_id = ?",
                fila -> {
                    guardadas.put(CategoriaDeAviso.valueOf(fila.getString("categoria")),
                            new boolean[] {fila.getBoolean("correo"), fila.getBoolean("push")});
                },
                usuario.getId());
        return Arrays.stream(CategoriaDeAviso.values())
                .map(categoria -> {
                    boolean[] elegido = guardadas.getOrDefault(categoria, new boolean[] {true, true});
                    return new PreferenciaDeAviso(categoria, categoria.getEtiqueta(), categoria.getDescripcion(),
                            !categoria.configurable() || elegido[0],
                            !categoria.configurable() || elegido[1],
                            categoria.configurable());
                })
                .toList();
    }

    @Override
    @Transactional
    public List<PreferenciaDeAviso> guardar(User usuario, List<CambioDePreferencia> cambios) {
        for (CambioDePreferencia cambio : cambios) {
            if (!cambio.categoria().configurable()) {
                throw new BusinessException("Los avisos de «" + cambio.categoria().getEtiqueta()
                        + "» no se pueden apagar.", HttpStatus.BAD_REQUEST);
            }
        }
        for (CambioDePreferencia cambio : cambios) {
            jdbc.update("""
                    INSERT INTO preferencias_avisos (usuario_id, categoria, correo, push) VALUES (?, ?, ?, ?)
                    ON CONFLICT (usuario_id, categoria) DO UPDATE SET correo = EXCLUDED.correo, push = EXCLUDED.push
                    """, usuario.getId(), cambio.categoria().name(), cambio.correo(), cambio.push());
        }
        return mias(usuario);
    }

    @Override
    public boolean quiereCorreo(String email, NoticeType tipo) {
        CategoriaDeAviso categoria = CategoriaDeAviso.de(tipo);
        if (!categoria.configurable() || email == null) {
            return true;
        }
        // Por el índice de lower(email) (V17). Sin fila, sí.
        List<Boolean> elegido = jdbc.queryForList("""
                SELECT p.correo FROM preferencias_avisos p JOIN usuarios u ON u.id = p.usuario_id
                WHERE lower(u.email) = lower(?) AND p.categoria = ?
                """, Boolean.class, email, categoria.name());
        return elegido.isEmpty() || Boolean.TRUE.equals(elegido.get(0));
    }

    @Override
    public boolean quierePush(long usuarioId, NoticeType tipo) {
        CategoriaDeAviso categoria = CategoriaDeAviso.de(tipo);
        if (!categoria.configurable()) {
            return true;
        }
        List<Boolean> elegido = jdbc.queryForList(
                "SELECT push FROM preferencias_avisos WHERE usuario_id = ? AND categoria = ?",
                Boolean.class, usuarioId, categoria.name());
        return elegido.isEmpty() || Boolean.TRUE.equals(elegido.get(0));
    }
}
