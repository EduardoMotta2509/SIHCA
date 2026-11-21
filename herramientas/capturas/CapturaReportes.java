import java.awt.*;
import javax.swing.*;

/** Herramienta de desarrollo: captura los 13 reportes con ventana ancha (para el informe). */
public class CapturaReportes {
    public static void main(String[] a) throws Exception {
        Capturas.salida = new java.io.File(a[0]);
        Capturas.salida.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            sihca.ui.Tema.instalar();
            sihca.datos.Conexion.configurar(sihca.datos.ConfigConexion.cargar());
            try {
                sihca.datos.Resultado r = sihca.dao.UsuarioDAO.login("admin", "admin123");
                sihca.datos.Sesion.iniciar(r.texto(0, 0), r.texto(0, 1), r.texto(0, 2));
            } catch (Exception e) { throw new RuntimeException(e); }
            sihca.ui.VentanaPrincipal v = new sihca.ui.VentanaPrincipal();
            v.setSize(1995, 1100);
            v.setVisible(true);
        });
        Thread.sleep(2500);
        final Window p = Capturas.ventana("Sistema de Horarios");
        Capturas.modulo(p, "PanelReportes");
        Thread.sleep(1500);
        for (int i = 0; i < 13; i++) {
            final int k = i;
            SwingUtilities.invokeAndWait(() -> ((JList<?>) Capturas.componentes(p, JList.class)[0]).setSelectedIndex(k));
            Thread.sleep(1500);
            Capturas.capturar(p, String.format("W%02d", i + 1));
        }
        System.exit(0);
    }
}
