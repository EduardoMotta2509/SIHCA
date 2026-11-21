package sihca.datos;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Punto único de acceso a los procedimientos almacenados.
 * <p>
 * Toda la comunicación con MySQL pasa por aquí mediante sentencias
 * {@code {CALL procedimiento(?, ?, ...)}} con {@link CallableStatement}.
 * Los parámetros siempre se envían enlazados (nunca concatenados en el SQL),
 * lo que evita la inyección SQL.
 */
public final class BD {

    private BD() {
    }

    /** Ejecuta un procedimiento que devuelve un conjunto de resultados (listados y reportes). */
    public static Resultado consultar(String procedimiento, Object... parametros) throws SQLException {
        try (CallableStatement cs = preparar(procedimiento, parametros.length, 0)) {
            enlazar(cs, parametros);
            boolean hayResultado = cs.execute();
            if (hayResultado) {
                try (ResultSet rs = cs.getResultSet()) {
                    return Resultado.desde(rs);
                }
            }
            return Resultado.vacio();
        }
    }

    /** Ejecuta un procedimiento de inserción, modificación o eliminación sin valores de salida. */
    public static void ejecutar(String procedimiento, Object... parametros) throws SQLException {
        try (CallableStatement cs = preparar(procedimiento, parametros.length, 0)) {
            enlazar(cs, parametros);
            cs.execute();
        }
    }

    /**
     * Ejecuta un procedimiento con parámetros de salida (OUT) ubicados al final de la lista.
     *
     * @param tiposSalida tipos JDBC de los parámetros OUT (por ejemplo {@link Types#INTEGER})
     * @return los valores de salida en el mismo orden
     */
    public static Object[] ejecutarConSalida(String procedimiento, int[] tiposSalida, Object... parametros)
            throws SQLException {
        try (CallableStatement cs = preparar(procedimiento, parametros.length, tiposSalida.length)) {
            enlazar(cs, parametros);
            for (int i = 0; i < tiposSalida.length; i++) {
                cs.registerOutParameter(parametros.length + i + 1, tiposSalida[i]);
            }
            cs.execute();
            Object[] salida = new Object[tiposSalida.length];
            for (int i = 0; i < tiposSalida.length; i++) {
                salida[i] = cs.getObject(parametros.length + i + 1);
            }
            return salida;
        }
    }

    private static CallableStatement preparar(String procedimiento, int entradas, int salidas)
            throws SQLException {
        if (!procedimiento.matches("[a-zA-Z0-9_]+")) {
            throw new SQLException("Nombre de procedimiento no válido: " + procedimiento);
        }
        StringBuilder sql = new StringBuilder("{CALL ").append(procedimiento).append('(');
        for (int i = 0; i < entradas + salidas; i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")}");
        return Conexion.obtener().prepareCall(sql.toString());
    }

    private static void enlazar(CallableStatement cs, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object p = parametros[i];
            if (p == null) {
                cs.setNull(i + 1, Types.NULL);
            } else if (p instanceof Boolean) {
                cs.setInt(i + 1, ((Boolean) p) ? 1 : 0);
            } else {
                cs.setObject(i + 1, p);
            }
        }
    }
}
