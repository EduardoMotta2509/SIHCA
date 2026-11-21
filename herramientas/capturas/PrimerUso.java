import java.awt.*;
import javax.swing.*;

/** Herramienta de desarrollo: prueba el primer uso (sin BD) e instala desde el diálogo. */
public class PrimerUso {
    public static void main(String[] a) throws Exception {
        Capturas.salida = new java.io.File(a[0]);
        Capturas.salida.mkdirs();
        new Thread(() -> sihca.Main.main(new String[0])).start();
        Thread.sleep(4000);
        Window d = Capturas.dialogo();
        Capturas.capturar(d, "00a_primer_uso_sin_clave");
        Capturas.escribir(d, 2, "root"); // contraseña (campo 3 de texto: host, usuario, clave, base)
        SwingUtilities.invokeAndWait(() -> {
            for (Component c : Capturas.todos(((RootPaneContainer) d).getContentPane())) {
                if (c instanceof JPasswordField) ((JPasswordField) c).setText("root");
            }
        });
        Capturas.clic(d, "Probar conexión");
        Thread.sleep(1500);
        Capturas.capturar(d, "00b_sin_base_datos");
        Capturas.clic(d, "Instalar base de datos");
        Thread.sleep(1200);
        Window conf = Capturas.dialogo();
        Capturas.capturar(conf, "00c_confirmar_instalacion");
        Capturas.clic(conf, "Instalar");
        long t0 = System.currentTimeMillis();
        Thread.sleep(2500);
        Capturas.capturar(d, "00d_instalando");
        while (true) {
            Thread.sleep(1000);
            String[] t = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                for (Component c : Capturas.todos(((RootPaneContainer) d).getContentPane()))
                    if (c instanceof JProgressBar) t[0] = ((JProgressBar) c).getString();
            });
            if (t[0] != null && t[0].startsWith("Instalación completa")) break;
            if (System.currentTimeMillis() - t0 > 240000) throw new RuntimeException("timeout instalacion " + t[0]);
        }
        System.out.println("instalacion_seg=" + (System.currentTimeMillis() - t0) / 1000);
        Capturas.capturar(d, "00e_instalado");
        Capturas.clic(d, "Guardar y continuar");
        Thread.sleep(2500);
        Capturas.capturar(Capturas.ventana("Iniciar sesión"), "00f_login_tras_instalar");
        System.out.println(String.join("\n", Capturas.log));
        System.exit(0);
    }
}
