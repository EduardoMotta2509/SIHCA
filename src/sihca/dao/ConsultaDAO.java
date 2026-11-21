package sihca.dao;

import java.sql.SQLException;
import sihca.datos.BD;
import sihca.datos.Resultado;

/** Acceso a reportes, bitácora de auditoría e indicadores del panel de inicio. */
public final class ConsultaDAO {

    private ConsultaDAO() {
    }

    /** Ejecuta uno de los procedimientos de reporte (sp_rep_*) con sus parámetros. */
    public static Resultado reporte(String procedimiento, Object... parametros) throws SQLException {
        if (!procedimiento.startsWith("sp_rep_")) {
            throw new SQLException("Procedimiento de reporte no válido: " + procedimiento);
        }
        return BD.consultar(procedimiento, parametros);
    }

    public static Resultado bitacora(String desde, String hasta, String tabla, String usuario) throws SQLException {
        return BD.consultar("sp_bitacora_listar", desde, hasta, tabla, usuario);
    }

    public static Resultado resumenInicio(int periodo) throws SQLException {
        return BD.consultar("sp_resumen_inicio", periodo);
    }
}
