package sihca.dao;

import java.sql.SQLException;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos de grados escolares y secciones (tablas Grado_Escolar y Seccion). */
public final class GradoSeccionDAO {

    private GradoSeccionDAO() {
    }

    public static Resultado listarGrados() throws SQLException {
        return BD.consultar("sp_grado_listar");
    }

    public static void insertarGrado(int id, String nombre, String nivel) throws SQLException {
        BD.ejecutar("sp_grado_insertar", id, nombre, nivel);
    }

    public static void actualizarGrado(int id, String nombre, String nivel) throws SQLException {
        BD.ejecutar("sp_grado_actualizar", id, nombre, nivel);
    }

    public static void eliminarGrado(int id) throws SQLException {
        BD.ejecutar("sp_grado_eliminar", id);
    }

    /** @param idGrado null para listar todas las secciones */
    public static Resultado listarSecciones(Integer idGrado) throws SQLException {
        return BD.consultar("sp_seccion_listar", idGrado);
    }

    public static void insertarSeccion(int id, String letra, int idGrado) throws SQLException {
        BD.ejecutar("sp_seccion_insertar", id, letra, idGrado);
    }

    public static void actualizarSeccion(int id, String letra, int idGrado) throws SQLException {
        BD.ejecutar("sp_seccion_actualizar", id, letra, idGrado);
    }

    public static void eliminarSeccion(int id) throws SQLException {
        BD.ejecutar("sp_seccion_eliminar", id);
    }
}
