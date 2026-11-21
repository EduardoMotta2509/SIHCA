package sihca.dao;

import java.sql.SQLException;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos de las cuentas del aplicativo (tabla Usuario_Sistema). */
public final class UsuarioDAO {

    private UsuarioDAO() {
    }

    /** Valida las credenciales; devuelve una fila (Usuario, Nombre, Rol) o lanza error. */
    public static Resultado login(String usuario, String clave) throws SQLException {
        return BD.consultar("sp_login", usuario, clave);
    }

    public static void logout() throws SQLException {
        BD.ejecutar("sp_logout");
    }

    public static Resultado listar() throws SQLException {
        return BD.consultar("sp_usuario_listar");
    }

    public static void insertar(String usuario, String clave, String nombre, String rol) throws SQLException {
        BD.ejecutar("sp_usuario_insertar", usuario, clave, nombre, rol);
    }

    public static void actualizar(String usuario, String nombre, String rol, boolean activo) throws SQLException {
        BD.ejecutar("sp_usuario_actualizar", usuario, nombre, rol, activo);
    }

    public static void cambiarClave(String usuario, String claveNueva) throws SQLException {
        BD.ejecutar("sp_usuario_cambiar_clave", usuario, claveNueva);
    }

    public static void desactivar(String usuario) throws SQLException {
        BD.ejecutar("sp_usuario_eliminar", usuario);
    }
}
