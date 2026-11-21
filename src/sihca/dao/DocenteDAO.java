package sihca.dao;

import java.sql.SQLException;
import java.sql.Types;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos de docentes y sus especialidades (tablas Docente y Especializado_En). */
public final class DocenteDAO {

    private DocenteDAO() {
    }

    public static Resultado listar(String filtro, boolean incluirBajas) throws SQLException {
        return BD.consultar("sp_docente_listar", filtro, incluirBajas);
    }

    public static void insertar(String dni, String nombre, String modalidad, String condicion) throws SQLException {
        BD.ejecutar("sp_docente_insertar", dni, nombre, modalidad, condicion);
    }

    public static void actualizar(String dni, String nombre, String modalidad, String condicion) throws SQLException {
        BD.ejecutar("sp_docente_actualizar", dni, nombre, modalidad, condicion);
    }

    /** Elimina físicamente si no tiene historial; si lo tiene, lo da de baja. Devuelve el resultado. */
    public static String eliminar(String dni) throws SQLException {
        return (String) BD.ejecutarConSalida("sp_docente_eliminar", new int[] {Types.VARCHAR}, dni)[0];
    }

    public static void reactivar(String dni) throws SQLException {
        BD.ejecutar("sp_docente_reactivar", dni);
    }

    public static Resultado especialidades(String dni) throws SQLException {
        return BD.consultar("sp_especialidad_listar", dni);
    }

    public static void asignarEspecialidad(String dni, String materia) throws SQLException {
        BD.ejecutar("sp_especialidad_asignar", dni, materia);
    }

    public static void quitarEspecialidad(String dni, String materia) throws SQLException {
        BD.ejecutar("sp_especialidad_quitar", dni, materia);
    }

    public static Resultado especialistasDe(String materia) throws SQLException {
        return BD.consultar("sp_docentes_por_materia", materia);
    }
}
