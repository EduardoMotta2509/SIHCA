package sihca;

import javax.swing.SwingUtilities;
import sihca.ui.Aplicacion;
import sihca.ui.Tema;

/**
 * SIHCA - Sistema de Horarios y Carga Académica
 * Colegio Particular Esclavas del Sagrado Corazón de Jesús (Arequipa).
 * <p>
 * Aplicativo cliente de escritorio que consume la base de datos MySQL
 * bd_horarios_esclavas exclusivamente mediante procedimientos almacenados
 * (JDBC nativo, sin frameworks).
 *
 * @author Grupo 09 - GDI: Carnero Moran, Lipa Lipa, Morote Espinoza, Motta Flores, Ventura Huamán
 * @version 1.0
 */
public final class Main {

    public static final String NOMBRE = "SIHCA";
    public static final String NOMBRE_LARGO = "Sistema de Horarios y Carga Académica";
    public static final String VERSION = "1.0.0";

    private Main() {
    }

    public static void main(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            System.setProperty("awt.useSystemAAFontSettings", "on");
        }
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            Tema.instalar();
            Aplicacion.iniciar();
        });
    }
}
