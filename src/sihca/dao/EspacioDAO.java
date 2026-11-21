package sihca.dao;

import java.sql.SQLException;
import java.sql.Types;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a datos de la infraestructura física (tabla Espacio). */
public final class EspacioDAO {

    private EspacioDAO() {
    }

    public static Resultado listar(boolean incluirBajas) throws SQLException {
        return BD.consultar("sp_espacio_listar", incluirBajas);
    }

    public static void insertar(String denominacion, String categoria, int limite) throws SQLException {
        BD.ejecutar("sp_espacio_insertar", denominacion, categoria, limite);
    }

    public static void actualizar(String actual, String nueva, String categoria, int limite) throws SQLException {
        BD.ejecutar("sp_espacio_actualizar", actual, nueva, categoria, limite);
    }

    public static String eliminar(String denominacion) throws SQLException {
        return (String) BD.ejecutarConSalida("sp_espacio_eliminar", new int[] {Types.VARCHAR}, denominacion)[0];
    }

    public static void reactivar(String denominacion) throws SQLException {
        BD.ejecutar("sp_espacio_reactivar", denominacion);
    }
}
