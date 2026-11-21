package sihca.datos;

/**
 * Datos del usuario que inició sesión y del periodo académico de trabajo.
 * Los permisos se validan dos veces: aquí (para ocultar opciones en pantalla)
 * y en la base de datos (procedimiento sp_verificar_permiso).
 */
public final class Sesion {

    private static String usuario;
    private static String nombre;
    private static String rol;
    private static short periodo;
    private static boolean periodoCerrado;

    private Sesion() {
    }

    public static void iniciar(String u, String n, String r) {
        usuario = u;
        nombre = n;
        rol = r;
    }

    public static void cerrar() {
        usuario = null;
        nombre = null;
        rol = null;
        periodo = 0;
    }

    public static boolean activa() {
        return usuario != null;
    }

    public static String usuario() { return usuario; }
    public static String nombre() { return nombre; }
    public static String rol() { return rol; }

    /** Administrador y Coordinador pueden registrar, modificar y eliminar. */
    public static boolean puedeEscribir() {
        return "Administrador".equals(rol) || "Coordinador".equals(rol);
    }

    public static boolean esAdministrador() {
        return "Administrador".equals(rol);
    }

    public static short periodo() { return periodo; }
    public static boolean periodoCerrado() { return periodoCerrado; }

    public static void setPeriodo(short anio, boolean cerrado) {
        periodo = anio;
        periodoCerrado = cerrado;
    }
}
