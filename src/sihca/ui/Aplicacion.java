package sihca.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import sihca.datos.ConfigConexion;
import sihca.datos.Conexion;
import sihca.datos.Sesion;

/**
 * Controla el flujo general: verificar conexión, iniciar sesión, abrir la
 * ventana principal y volver al login al cerrar sesión.
 */
public final class Aplicacion {

    private Aplicacion() {
    }

    public static void iniciar() {
        ConfigConexion cfg = ConfigConexion.cargar();
        Conexion.configurar(cfg);
        String problema = null;
        try {
            Conexion.obtener();
        } catch (SQLException ex) {
            problema = sihca.datos.Errores.mensaje(ex);
        }
        if (problema != null) {
            // Primera ejecución o servidor distinto: se pide la configuración
            boolean ok = DialogoConexion.mostrar(null, problema);
            if (!ok) {
                System.exit(0);
            }
        }
        mostrarLogin();
    }

    public static void mostrarLogin() {
        new VentanaLogin().setVisible(true);
    }

    public static void abrirPrincipal() {
        VentanaPrincipal v = new VentanaPrincipal();
        v.setVisible(true);
    }

    public static void cerrarSesion(VentanaPrincipal v) {
        try {
            sihca.dao.UsuarioDAO.logout();
        } catch (SQLException ex) {
            Conexion.limpiarSesion();
        }
        Sesion.cerrar();
        v.dispose();
        mostrarLogin();
    }

    /** Ícono de la ventana (dibujado: cuadrícula de horario sobre fondo azul). */
    public static List<Image> iconosVentana() {
        List<Image> l = new ArrayList<>();
        for (int t : new int[] {16, 24, 32, 48, 64}) {
            l.add(logo(t));
        }
        return l;
    }

    public static BufferedImage logo(int t) {
        BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Tema.PRIMARIO);
        g.fillRoundRect(0, 0, t, t, t / 3, t / 3);
        float m = t * 0.22f;
        float c = (t - 2 * m) / 3f;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                g.setColor((i == 0 && j == 0) || (i == 2 && j == 1) ? Tema.DORADO : new java.awt.Color(255, 255, 255, 230));
                g.fill(new java.awt.geom.RoundRectangle2D.Float(m + i * c + c * 0.12f, m + j * c + c * 0.12f,
                        c * 0.76f, c * 0.76f, c * 0.3f, c * 0.3f));
            }
        }
        g.dispose();
        return img;
    }
}
