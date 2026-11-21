package sihca.datos;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Ubica la carpeta de instalación del aplicativo sin depender de rutas fijas.
 * <p>
 * Estructura esperada de la carpeta portable:
 * <pre>
 *   SIHCA/
 *     app/sihca.jar          (este programa)
 *     app/lib/...            (driver JDBC de MySQL)
 *     config/                (conexion.properties)
 *     sql/                   (script de la base de datos)
 *     runtime/               (Java incluido)
 * </pre>
 */
public final class RutasApp {

    private static File base;

    private RutasApp() {
    }

    /** Carpeta raíz del aplicativo (la que contiene app/, config/ y sql/). */
    public static synchronized File carpetaBase() {
        if (base == null) {
            try {
                File origen = new File(RutasApp.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                File dir = origen.isFile() ? origen.getParentFile() : origen;
                if (dir != null && dir.getName().equalsIgnoreCase("app") && dir.getParentFile() != null) {
                    dir = dir.getParentFile();
                }
                // En desarrollo (clases sueltas) se usa la carpeta de trabajo
                if (origen.isDirectory()) {
                    dir = new File(System.getProperty("user.dir"));
                }
                base = dir;
            } catch (URISyntaxException | SecurityException ex) {
                base = new File(System.getProperty("user.dir"));
            }
        }
        return base;
    }

    public static File carpetaConfig() {
        return new File(carpetaBase(), "config");
    }

    public static File carpetaSql() {
        return new File(carpetaBase(), "sql");
    }
}
