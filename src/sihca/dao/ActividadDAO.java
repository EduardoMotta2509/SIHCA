package sihca.dao;

import java.sql.SQLException;
import java.sql.Types;
import sihca.datos.BD;
import sihca.datos.Resultado;

/**
 * Acceso a datos de los bloques horarios y sus participantes
 * (tablas Actividad, Seccion_Periodo y Docente_Actividad).
 */
public final class ActividadDAO {

    private ActividadDAO() {
    }

    /** Los filtros en null no se aplican. */
    public static Resultado listar(int periodo, String dia, String naturaleza, Integer idSeccion, String dni)
            throws SQLException {
        return BD.consultar("sp_actividad_listar", periodo, dia, naturaleza, idSeccion, dni);
    }

    public static int insertar(String inicio, String fin, String dia, String naturaleza, String materia,
            int periodo, String espacio) throws SQLException {
        Object[] r = BD.ejecutarConSalida("sp_actividad_insertar", new int[] {Types.INTEGER},
                inicio, fin, dia, naturaleza, materia, periodo, espacio);
        return ((Number) r[0]).intValue();
    }

    public static void actualizar(int id, String inicio, String fin, String dia, String naturaleza,
            String materia, int periodo, String espacio) throws SQLException {
        BD.ejecutar("sp_actividad_actualizar", id, inicio, fin, dia, naturaleza, materia, periodo, espacio);
    }

    public static void eliminar(int id) throws SQLException {
        BD.ejecutar("sp_actividad_eliminar", id);
    }

    /** Registra en una sola transacción el bloque de clase, la sección y el docente. */
    public static int programarClase(int periodo, String dia, String inicio, String fin, String materia,
            String espacio, int idSeccion, String dni) throws SQLException {
        Object[] r = BD.ejecutarConSalida("sp_programar_clase", new int[] {Types.INTEGER},
                periodo, dia, inicio, fin, materia, espacio, idSeccion, dni);
        return ((Number) r[0]).intValue();
    }

    public static Resultado secciones(int id) throws SQLException {
        return BD.consultar("sp_actividad_secciones", id);
    }

    public static Resultado docentes(int id) throws SQLException {
        return BD.consultar("sp_actividad_docentes", id);
    }

    public static void agregarSeccion(int id, int idSeccion) throws SQLException {
        BD.ejecutar("sp_actividad_agregar_seccion", id, idSeccion);
    }

    public static void quitarSeccion(int id, int idSeccion) throws SQLException {
        BD.ejecutar("sp_actividad_quitar_seccion", id, idSeccion);
    }

    public static void asignarDocente(int id, String dni, String rol) throws SQLException {
        BD.ejecutar("sp_actividad_asignar_docente", id, dni, rol);
    }

    public static void quitarDocente(int id, String dni) throws SQLException {
        BD.ejecutar("sp_actividad_quitar_docente", id, dni);
    }
}
