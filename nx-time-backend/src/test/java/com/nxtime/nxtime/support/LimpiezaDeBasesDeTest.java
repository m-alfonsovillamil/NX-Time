package com.nxtime.nxtime.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * Borra las bases de datos que deja la suite, al terminar de ejecutarla.
 *
 * Cada clase de test que necesita Postgres se crea la suya
 * ({@code CREATE DATABASE contract_test_<nanoTime>}) en un
 * {@code @DynamicPropertySource}, y **ninguna la borraba**: ~20 bases de 10 MB
 * por cada pase completo, que se quedaban ahí para siempre. El 19/09/2026 había
 * 619 en el Postgres de desarrollo, unos 6 GB, y el contenedor tardaba tres
 * minutos en arrancar sincronizando ese directorio. En el CI no se notaba
 * porque cada ejecución estrena contenedor; en el PC de quien desarrolla, sí.
 *
 * No se borran al acabar CADA clase a propósito: Spring cachea los contextos y
 * mantiene sus pools abiertos hasta que termina la JVM, así que una base que se
 * intentara borrar antes seguiría en uso.
 *
 * Eso tiene una consecuencia que conviene saber: cuando esto corre, las bases
 * de la ejecución que acaba de terminar TODAVÍA tienen conexiones vivas (los
 * contextos se cierran en un hook de apagado, después), así que cada pase
 * limpia las del pase anterior y deja las suyas. El resultado no es cero, es
 * una veintena constante en vez de veinte más cada vez. Comprobado: de 82 a 19
 * tras un pase, y 19 otra vez tras el siguiente.
 *
 * Y se borra **solo lo que nadie está usando**: sin {@code WITH (FORCE)} y
 * comprobando que no haya conexiones vivas. Si alguien tiene otra suite
 * corriendo en el mismo Postgres, sus bases no se tocan -- prefiero dejar
 * basura a cargarme la ejecución de al lado.
 *
 * Se engancha por {@code META-INF/services}, no con una anotación: así vale
 * para toda la suite sin tener que acordarse de ponerla en cada clase nueva.
 */
public class LimpiezaDeBasesDeTest implements TestExecutionListener {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5433/nxtime";
    private static final String USUARIO = "nxtime";
    private static final String CONTRASENA = "nxtime";

    /**
     * El nombre que se llevan todas: un prefijo y el nanoTime de cuando se
     * creó. Nueve dígitos o más no se los puede encontrar por casualidad una
     * base de verdad (la de desarrollo se llama "nxtime" a secas).
     */
    private static final String PATRON = "^[a-z0-9_]+_[0-9]{9,}$";

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USUARIO, CONTRASENA);
             Statement statement = admin.createStatement()) {

            List<String> huerfanas = new ArrayList<>();
            String consulta = "SELECT d.datname FROM pg_database d "
                    + "WHERE d.datistemplate = false AND d.datname ~ '" + PATRON + "' "
                    + "AND NOT EXISTS (SELECT 1 FROM pg_stat_activity a WHERE a.datname = d.datname)";
            try (ResultSet filas = statement.executeQuery(consulta)) {
                while (filas.next()) {
                    huerfanas.add(filas.getString(1));
                }
            }

            int borradas = 0;
            for (String base : huerfanas) {
                try {
                    statement.execute("DROP DATABASE IF EXISTS \"" + base + "\"");
                    borradas++;
                } catch (Exception seEstaUsando) {
                    // Otra ejecución la cogió entre la consulta y el borrado.
                    // No es un problema: se la llevará la limpieza siguiente.
                }
            }
            if (borradas > 0) {
                System.out.println("Limpieza: " + borradas + " bases de datos de test borradas.");
            }
        } catch (Exception sinPostgres) {
            // Sin Postgres delante no hay nada que limpiar, y desde luego no
            // es motivo para que la ejecución de los tests acabe en rojo.
            System.out.println("Limpieza: no se pudo conectar a Postgres (" + sinPostgres.getMessage() + ")");
        }
    }
}
