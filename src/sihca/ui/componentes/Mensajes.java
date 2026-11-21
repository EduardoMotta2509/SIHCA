package sihca.ui.componentes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import sihca.datos.Errores;
import sihca.ui.Tema;

/**
 * Cuadros de mensaje del sistema: confirmación, advertencia, error e información.
 * Cada tipo tiene un color e ícono propio para que el usuario lo reconozca de un vistazo.
 */
public final class Mensajes {

    private enum Tipo { INFO, EXITO, ERROR, ADVERTENCIA, PREGUNTA }

    private Mensajes() {
    }

    public static void info(Component padre, String titulo, String mensaje) {
        mostrar(padre, Tipo.INFO, titulo, mensaje, "Aceptar", null);
    }

    public static void exito(Component padre, String titulo, String mensaje) {
        mostrar(padre, Tipo.EXITO, titulo, mensaje, "Aceptar", null);
    }

    public static void error(Component padre, String titulo, String mensaje) {
        mostrar(padre, Tipo.ERROR, titulo, mensaje, "Entendido", null);
    }

    /** Muestra un error de base de datos traducido a lenguaje del usuario. */
    public static void error(Component padre, Throwable ex) {
        mostrar(padre, Tipo.ERROR, "No se pudo completar la operación", Errores.mensaje(ex), "Entendido", null);
    }

    public static void advertencia(Component padre, String titulo, String mensaje) {
        mostrar(padre, Tipo.ADVERTENCIA, titulo, mensaje, "Entendido", null);
    }

    /** Pregunta de confirmación. Devuelve true si el usuario acepta. */
    public static boolean confirmar(Component padre, String titulo, String mensaje, String textoAceptar) {
        return mostrar(padre, Tipo.PREGUNTA, titulo, mensaje, textoAceptar, "Cancelar");
    }

    private static boolean mostrar(Component padre, Tipo tipo, String titulo, String mensaje,
            String aceptar, String cancelar) {
        Window w = padre == null ? null : (padre instanceof Window ? (Window) padre : SwingUtilities.getWindowAncestor(padre));
        JDialog d = new JDialog(w, tituloVentana(tipo), JDialog.ModalityType.APPLICATION_MODAL);
        final boolean[] ok = {false};

        Color color;
        Iconos.Tipo icono;
        switch (tipo) {
            case EXITO: color = Tema.EXITO; icono = Iconos.Tipo.OK; break;
            case ERROR: color = Tema.PELIGRO; icono = Iconos.Tipo.ALERTA; break;
            case ADVERTENCIA: color = Tema.ADVERTENCIA; icono = Iconos.Tipo.ALERTA; break;
            case PREGUNTA: color = Tema.PRIMARIO; icono = Iconos.Tipo.ALERTA; break;
            default: color = Tema.PRIMARIO; icono = Iconos.Tipo.OK; break;
        }

        JPanel raiz = new JPanel(new BorderLayout(16, 0));
        raiz.setBackground(Tema.SUPERFICIE);
        raiz.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(4, 0, 0, 0, color),
                BorderFactory.createEmptyBorder(22, 24, 18, 24)));
        JLabel ic = new JLabel(Iconos.de(icono, 34, color));
        ic.setVerticalAlignment(JLabel.TOP);
        raiz.add(ic, BorderLayout.WEST);

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setOpaque(false);
        JLabel t = UI.titulo(titulo, 16f);
        centro.add(t, BorderLayout.NORTH);
        JLabel m = new JLabel("<html><div style='width:360px'>" + escapar(mensaje).replace("\n", "<br>") + "</div></html>");
        m.setFont(Tema.normal(13.5f));
        m.setForeground(Tema.TEXTO_SUAVE);
        centro.add(m, BorderLayout.CENTER);
        raiz.add(centro, BorderLayout.CENTER);

        Boton bAceptar = tipo == Tipo.PREGUNTA && aceptar.toLowerCase().contains("elimin")
                ? Boton.peligro(aceptar) : Boton.primario(aceptar);
        bAceptar.addActionListener(e -> {
            ok[0] = true;
            d.dispose();
        });
        JPanel botones = UI.filaDerecha(8);
        if (cancelar != null) {
            Boton bc = Boton.secundario(cancelar);
            bc.addActionListener(e -> d.dispose());
            botones.add(bc);
        }
        botones.add(bAceptar);
        botones.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        centro.add(botones, BorderLayout.SOUTH);

        d.setContentPane(raiz);
        d.getRootPane().setDefaultButton(bAceptar);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setMinimumSize(new Dimension(440, d.getHeight()));
        d.setResizable(false);
        d.setLocationRelativeTo(w);
        d.setVisible(true);
        return ok[0];
    }

    private static String tituloVentana(Tipo t) {
        switch (t) {
            case ERROR: return "Error";
            case ADVERTENCIA: return "Advertencia";
            case PREGUNTA: return "Confirmación";
            case EXITO: return "Operación exitosa";
            default: return "Información";
        }
    }

    static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
