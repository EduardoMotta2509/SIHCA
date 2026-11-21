package sihca.datos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Parámetros de conexión al servidor MySQL.
 * <p>
 * Se guardan en el archivo {@code config/conexion.properties} ubicado junto al
 * aplicativo, de modo que la carpeta del sistema es portable: se puede copiar a
 * cualquier computadora y solo hace falta ajustar la contraseña de MySQL.
 */
public final class ConfigConexion {

    private static final String ARCHIVO = "conexion.properties";

    private String host = "localhost";
    private int puerto = 3306;
    private String usuario = "root";
    private String clave = "";
    private String baseDatos = "bd_horarios_esclavas";

    /** Carga la configuración guardada; si no existe, devuelve los valores por defecto. */
    public static ConfigConexion cargar() {
        ConfigConexion c = new ConfigConexion();
        File f = archivo();
        if (f.isFile()) {
            Properties p = new Properties();
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                p.load(r);
                c.host = p.getProperty("host", c.host).trim();
                c.puerto = Integer.parseInt(p.getProperty("puerto", String.valueOf(c.puerto)).trim());
                c.usuario = p.getProperty("usuario", c.usuario).trim();
                c.clave = p.getProperty("clave", c.clave);
                c.baseDatos = p.getProperty("baseDatos", c.baseDatos).trim();
            } catch (IOException | NumberFormatException ex) {
                // Archivo dañado: se usan los valores por defecto
            }
        }
        return c;
    }

    /** Guarda la configuración en config/conexion.properties. */
    public void guardar() throws IOException {
        File f = archivo();
        f.getParentFile().mkdirs();
        Properties p = new Properties();
        p.setProperty("host", host);
        p.setProperty("puerto", String.valueOf(puerto));
        p.setProperty("usuario", usuario);
        p.setProperty("clave", clave);
        p.setProperty("baseDatos", baseDatos);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            p.store(w, "SIHCA - Parametros de conexion a MySQL (edite 'clave' con la contrasena de su servidor)");
        }
    }

    /** ¿El usuario ya guardó alguna vez la configuración? */
    public static boolean existeArchivo() {
        return archivo().isFile();
    }

    private static File archivo() {
        return new File(RutasApp.carpetaConfig(), ARCHIVO);
    }

    /** URL JDBC. Si {@code conBaseDatos} es falso se conecta solo al servidor (para instalar). */
    public String url(boolean conBaseDatos) {
        return "jdbc:mysql://" + host + ":" + puerto + "/" + (conBaseDatos ? baseDatos : "")
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"   // necesario con caching_sha2_password sin SSL
                + "&characterEncoding=UTF-8"
                + "&connectTimeout=5000"
                + "&useInformationSchema=false";
    }

    public String descripcion() {
        return usuario + "@" + host + ":" + puerto + " / " + baseDatos;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPuerto() { return puerto; }
    public void setPuerto(int puerto) { this.puerto = puerto; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public String getBaseDatos() { return baseDatos; }
    public void setBaseDatos(String baseDatos) { this.baseDatos = baseDatos; }
}
