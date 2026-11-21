package sihca.dao;

import java.sql.SQLException;
import java.sql.Types;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos de asignaturas (tabla Asignatura). */
public final class AsignaturaDAO {

    private AsignaturaDAO() {
    }

    public static Resultado listar(boolean incluirBajas) throws SQLException {
        return BD.consultar("sp_asignatura_listar", incluirBajas);
    }

    public static void insertar(String nombre, String exigencia) throws SQLException {
        BD.ejecutar("sp_asignatura_insertar", nombre, exigencia);
    }

    public static void actualizar(String nombreActual, String nombreNuevo, String exigencia) throws SQLException {
        BD.ejecutar("sp_asignatura_actualizar", nombreActual, nombreNuevo, exigencia);
    }

    public static String eliminar(String nombre) throws SQLException {
        return (String) BD.ejecutarConSalida("sp_asignatura_eliminar", new int[] {Types.VARCHAR}, nombre)[0];
    }

    public static void reactivar(String nombre) throws SQLException {
        BD.ejecutar("sp_asignatura_reactivar", nombre);
    }
}
