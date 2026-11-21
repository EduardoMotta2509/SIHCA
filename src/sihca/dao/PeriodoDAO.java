package sihca.dao;

import java.sql.SQLException;
import java.sql.Types;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos de los periodos académicos (tabla Periodo_Academico). */
public final class PeriodoDAO {

    private PeriodoDAO() {
    }

    public static Resultado listar() throws SQLException {
        return BD.consultar("sp_periodo_listar");
    }

    public static void insertar(int anio, String estado) throws SQLException {
        BD.ejecutar("sp_periodo_insertar", anio, estado);
    }

    public static void actualizar(int anio, String estado) throws SQLException {
        BD.ejecutar("sp_periodo_actualizar", anio, estado);
    }

    public static void eliminar(int anio) throws SQLException {
        BD.ejecutar("sp_periodo_eliminar", anio);
    }

    /** Copia el horario de un año a otro. Devuelve {bloques copiados, bloques sin docente}. */
    public static int[] replicarHorario(int origen, int destino) throws SQLException {
        Object[] r = BD.ejecutarConSalida("sp_periodo_replicar_horario",
                new int[] {Types.INTEGER, Types.INTEGER}, origen, destino);
        return new int[] {((Number) r[0]).intValue(), ((Number) r[1]).intValue()};
    }
}
