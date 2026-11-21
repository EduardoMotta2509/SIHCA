package sihca.datos;

import java.sql.SQLException;

/**
 * Traduce los errores de MySQL a mensajes comprensibles para el usuario final.
 * Los errores de reglas de negocio llegan desde los triggers y procedimientos
 * con SQLSTATE 45000 y su mensaje ya está redactado en español.
 */
public final class Errores {

    private Errores() {
    }

    public static String mensaje(Throwable t) {
        if (!(t instanceof SQLException)) {
            return t.getMessage() == null ? t.toString() : t.getMessage();
        }
        SQLException e = (SQLException) t;
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        int codigo = e.getErrorCode();
        String msg = e.getMessage() == null ? "" : e.getMessage();

        if ("45000".equals(estado)) {
            return msg; // regla de negocio validada por un trigger o procedimiento
        }
        switch (codigo) {
            case 1045:
                return "MySQL rechazó el usuario o la contraseña de conexión. Revise la configuración de conexión.";
            case 1049:
                return "La base de datos no existe en el servidor. Use \"Instalar base de datos\" para crearla.";
            case 1305:
                return "La base de datos no tiene los procedimientos del aplicativo. Reinstálela desde \"Configurar conexión\".";
            case 1062:
                return "Ya existe un registro con esos datos (clave duplicada).";
            case 1451:
                return "No se puede eliminar: el registro está siendo usado por otros datos.";
            case 1452:
                return "Uno de los datos seleccionados no existe en la base de datos.";
            case 3819:
                return "Un valor no cumple las restricciones de la base de datos (" + restriccion(msg) + ").";
            case 1406:
                return "Uno de los textos ingresados es demasiado largo.";
            case 1292:
            case 1366:
                return "Uno de los valores ingresados no tiene el formato correcto.";
            default:
                break;
        }
        if (estado.startsWith("08") || msg.contains("Communications link failure")) {
            return "No se pudo conectar con el servidor MySQL. Verifique que el servicio MySQL esté iniciado "
                    + "y que el servidor y el puerto sean correctos.";
        }
        return msg;
    }

    private static String restriccion(String msg) {
        int i = msg.indexOf('\'');
        int j = i >= 0 ? msg.indexOf('\'', i + 1) : -1;
        return (i >= 0 && j > i) ? msg.substring(i + 1, j) : "CHECK";
    }
}
