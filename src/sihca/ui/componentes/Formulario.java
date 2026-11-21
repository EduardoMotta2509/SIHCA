package sihca.ui.componentes;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
 * Ventana de formulario reutilizable para registrar o modificar datos.
 * <p>
 * Si el procedimiento almacenado o un trigger rechaza los datos, el mensaje
 * de la base de datos se muestra dentro del mismo formulario (sin cerrarlo),
 * para que el usuario corrija y vuelva a intentar.
 */
public class Formulario extends JDialog {

    /** Acción a ejecutar al presionar Guardar. Puede lanzar la excepción de la base de datos. */
    public interface Accion {
        void ejecutar() throws Exception;
    }

    private final JPanel campos = new JPanel(new GridBagLayout());
    private final JLabel error = new JLabel();
    private final Boton guardar;
    private int fila;
    private boolean aceptado;
    private Accion accion;

    public Formulario(Component padre, String titulo, String descripcion, String textoGuardar) {
        super(padre instanceof Window ? (Window) padre : SwingUtilities.getWindowAncestor(padre),
                titulo, ModalityType.APPLICATION_MODAL);
        JPanel raiz = new JPanel(new BorderLayout(0, 14));
        raiz.setBackground(Tema.SUPERFICIE);
        raiz.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));

        JPanel cab = new JPanel(new BorderLayout(0, 4));
        cab.setOpaque(false);
        cab.add(UI.titulo(titulo, 17f), BorderLayout.NORTH);
        if (descripcion != null) {
            JLabel d = new JLabel("<html><div style='width:400px'>" + Mensajes.escapar(descripcion) + "</div></html>");
            d.setFont(Tema.normal(13f));
            d.setForeground(Tema.TEXTO_SUAVE);
            cab.add(d, BorderLayout.CENTER);
        }
        raiz.add(cab, BorderLayout.NORTH);

        campos.setOpaque(false);
        raiz.add(campos, BorderLayout.CENTER);

        JPanel pie = new JPanel(new BorderLayout(0, 12));
        pie.setOpaque(false);
        error.setFont(Tema.medio(13f));
        error.setForeground(Tema.PELIGRO);
        error.setIcon(Iconos.de(Iconos.Tipo.ALERTA, 16, Tema.PELIGRO));
        error.setVisible(false);
        pie.add(error, BorderLayout.NORTH);
        guardar = Boton.primario(textoGuardar);
        Boton cancelar = Boton.secundario("Cancelar");
        cancelar.addActionListener(e -> dispose());
        guardar.addActionListener(e -> guardar());
        pie.add(UI.filaDerecha(8, cancelar, guardar), BorderLayout.SOUTH);
        raiz.add(pie, BorderLayout.SOUTH);

        setContentPane(raiz);
        getRootPane().setDefaultButton(guardar);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Agrega un campo con su etiqueta (y un texto de ayuda opcional debajo). */
    public Formulario campo(String etiqueta, JComponent control, String ayuda) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = fila++;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(8, 0, 4, 0);
        campos.add(UI.etiqueta(etiqueta), c);
        c.gridy = fila++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        Dimension d = control.getPreferredSize();
        control.setPreferredSize(new Dimension(Math.max(d.width, 400), Math.max(d.height, 34)));
        campos.add(control, c);
        if (ayuda != null) {
            c.gridy = fila++;
            c.insets = new Insets(3, 2, 0, 0);
            JLabel a = UI.texto(ayuda, 12f, Tema.TEXTO_TENUE);
            campos.add(a, c);
        }
        return this;
    }

    public Formulario campo(String etiqueta, JComponent control) {
        return campo(etiqueta, control, null);
    }

    /** Muestra el formulario; devuelve true si la acción se ejecutó sin errores. */
    public boolean mostrar(Accion a) {
        this.accion = a;
        pack();
        setResizable(false);
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return aceptado;
    }

    private void guardar() {
        try {
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
            accion.ejecutar();
            aceptado = true;
            dispose();
        } catch (Exception ex) {
            error.setText("<html><div style='width:390px'>" + Mensajes.escapar(Errores.mensaje(ex)) + "</div></html>");
            error.setVisible(true);
            pack();
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
        }
    }
}
