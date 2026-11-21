package sihca.dao;

import java.sql.SQLException;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos del plan de estudios (tabla Grado_Materia). */
public final class PlanEstudiosDAO {

    private PlanEstudiosDAO() {
    }

    public static Resultado listar(int idGrado) throws SQLException {
        return BD.consultar("sp_plan_listar", idGrado);
    }

    public static void insertar(String materia, int idGrado, int horas) throws SQLException {
        BD.ejecutar("sp_plan_insertar", materia, idGrado, horas);
    }

    public static void actualizar(String materia, int idGrado, int horas) throws SQLException {
        BD.ejecutar("sp_plan_actualizar", materia, idGrado, horas);
    }

    public static void eliminar(String materia, int idGrado) throws SQLException {
        BD.ejecutar("sp_plan_eliminar", materia, idGrado);
    }
}
