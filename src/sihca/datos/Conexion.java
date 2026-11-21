package sihca.datos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Administra la única conexión JDBC que usa el aplicativo.
 * <p>
 * El aplicativo es un cliente de escritorio de un solo usuario, por lo que basta
 * una conexión abierta durante la sesión. Si el servidor la cierra (por ejemplo,
 * por inactividad), se reabre automáticamente y se restauran las variables de
 * sesión que usan los triggers de auditoría (@usuario_app y @rol_app).
 * <p>
 * No se usa ningún framework ni ORM: la comunicación es JDBC nativo
 * (java.sql) con el driver oficial MySQL Connector/J.
 */
public final class Conexion {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    private static ConfigConexion config;
    private static Connection conexion;

    private Conexion() {
    }

    public static void configurar(ConfigConexion c) {
        cerrar();
        config = c;
    }

    public static ConfigConexion config() {
        return config;
    }

    /** Devuelve la conexión abierta (la abre o la reabre si es necesario). */
    public static synchronized Connection obtener() throws SQLException {
        if (config == null) {
            throw new SQLException("No se ha configurado la conexión a MySQL.");
        }
        if (conexion == null || !conexion.isValid(2)) {
            cerrar();
            conexion = abrir(config, true);
            restaurarSesion();
        }
        return conexion;
    }

    /** Abre una conexión nueva con los parámetros indicados. */
    public static Connection abrir(ConfigConexion c, boolean conBaseDatos) throws SQLException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new SQLException("No se encontró el driver JDBC de MySQL (app/lib/mysql-connector-j).", ex);
        }
        return DriverManager.getConnection(c.url(conBaseDatos), c.getUsuario(), c.getClave());
    }

    /** Prueba una configuración sin modificar la conexión actual. Devuelve la versión del servidor. */
    public static String probar(ConfigConexion c, boolean conBaseDatos) throws SQLException {
        try (Connection cn = abrir(c, conBaseDatos)) {
            return cn.getMetaData().getDatabaseProductVersion();
        }
    }

    /** Versión de MySQL de la conexión actual (para la barra de estado). */
    public static String versionServidor() {
        try {
            return obtener().getMetaData().getDatabaseProductVersion();
        } catch (SQLException ex) {
            return "?";
        }
    }

    /** Vuelve a definir en el servidor las variables de sesión del usuario que inició sesión. */
    private static void restaurarSesion() throws SQLException {
        if (Sesion.activa()) {
            try (PreparedStatement ps = conexion.prepareStatement("SET @usuario_app = ?, @rol_app = ?")) {
                ps.setString(1, Sesion.usuario());
                ps.setString(2, Sesion.rol());
                ps.execute();
            }
        }
    }

    /** Limpia las variables de sesión en el servidor (cierre de sesión). */
    public static void limpiarSesion() {
        if (conexion != null) {
            try (Statement st = conexion.createStatement()) {
                st.execute("SET @usuario_app = NULL, @rol_app = NULL");
            } catch (SQLException ex) {
                // la conexión pudo cerrarse; no es un problema
            }
        }
    }

    public static synchronized void cerrar() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException ex) {
                // se ignora al cerrar
            }
            conexion = null;
        }
    }
}
