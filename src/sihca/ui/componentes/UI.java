package sihca.ui.componentes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxUI;
import sihca.ui.Tema;

/** Fábrica de controles con el estilo del aplicativo (campos, combos, etiquetas, paneles). */
public final class UI {

    private UI() {
    }

    // ------------------------------------------------------------------ etiquetas
    public static JLabel etiqueta(String t) {
        JLabel l = new JLabel(t);
        l.setFont(Tema.medio(12.5f));
        l.setForeground(Tema.TEXTO_SUAVE);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        return l;
    }

    public static JLabel texto(String t, float tam, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(Tema.normal(tam));
        l.setForeground(c);
        return l;
    }

    public static JLabel titulo(String t, float tam) {
        JLabel l = new JLabel(t);
        l.setFont(Tema.semi(tam));
        l.setForeground(Tema.TEXTO);
        return l;
    }

    // ------------------------------------------------------------------ campos
    public static JTextField campo(int columnas) {
        JTextField f = new JTextField(columnas);
        estilizar(f);
        return f;
    }

    public static JPasswordField campoClave(int columnas) {
        JPasswordField f = new JPasswordField(columnas);
        estilizar(f);
        return f;
    }

    private static void estilizar(JTextField f) {
        f.setFont(Tema.normal(13.5f));
        f.setForeground(Tema.TEXTO);
        f.setBackground(Tema.SUPERFICIE);
        f.setCaretColor(Tema.PRIMARIO);
        f.setSelectionColor(Tema.SELECCION);
        f.setSelectedTextColor(Tema.TEXTO);
        f.setBorder(bordeCampo(false));
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { f.setBorder(bordeCampo(true)); }
            @Override public void focusLost(FocusEvent e) { f.setBorder(bordeCampo(false)); }
        });
    }

    public static Border bordeCampo(boolean foco) {
        return BorderFactory.createCompoundBorder(new BordeRedondeado(foco ? Tema.PRIMARIO : Tema.BORDE_FUERTE, foco),
                BorderFactory.createEmptyBorder(6, 9, 6, 9));
    }

    public static JSpinner numero(int valor, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(valor, min, max, 1));
        s.setEditor(new JSpinner.NumberEditor(s, "#"));
        s.setFont(Tema.normal(13.5f));
        s.setBorder(new BordeRedondeado(Tema.BORDE_FUERTE, false));
        JComponent ed = s.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 4));
            tf.setFont(Tema.normal(13.5f));
            tf.setBackground(Tema.SUPERFICIE);
        }
        s.setPreferredSize(new Dimension(110, 34));
        return s;
    }

    public static JCheckBox casilla(String t) {
        JCheckBox c = new JCheckBox(t);
        c.setFont(Tema.normal(13f));
        c.setForeground(Tema.TEXTO);
        c.setOpaque(false);
        c.setFocusPainted(false);
        return c;
    }

    // ------------------------------------------------------------------ combos
    public static <T> JComboBox<T> combo() {
        JComboBox<T> c = new JComboBox<>();
        c.setUI(new ComboPlanoUI());
        c.setFont(Tema.normal(13.5f));
        c.setForeground(Tema.TEXTO);
        c.setBackground(Tema.SUPERFICIE);
        c.setBorder(new BordeRedondeado(Tema.BORDE_FUERTE, false));
        c.setMaximumRowCount(16);
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
                if (index < 0) { // valor mostrado en el combo cerrado
                    l.setBackground(Tema.SUPERFICIE);
                    l.setForeground(Tema.TEXTO);
                }
                l.setFont(Tema.normal(13.5f));
                return l;
            }
        });
        Dimension d = c.getPreferredSize();
        c.setPreferredSize(new Dimension(Math.max(d.width, 120), 34));
        return c;
    }

    @SafeVarargs
    public static <T> JComboBox<T> combo(T... items) {
        JComboBox<T> c = combo();
        for (T t : items) {
            c.addItem(t);
        }
        return c;
    }

    /** Selecciona en el combo la opción cuyo valor (o texto) coincide. */
    public static void seleccionar(JComboBox<?> c, Object valor) {
        for (int i = 0; i < c.getItemCount(); i++) {
            Object it = c.getItemAt(i);
            Object v = it instanceof Opcion ? ((Opcion) it).valor() : it;
            if (v != null && valor != null && v.toString().equals(valor.toString())) {
                c.setSelectedIndex(i);
                return;
            }
        }
    }

    /** Valor de la opción seleccionada (o null). */
    public static Object valor(JComboBox<?> c) {
        Object it = c.getSelectedItem();
        return it instanceof Opcion ? ((Opcion) it).valor() : it;
    }

    // ------------------------------------------------------------------ paneles
    public static JPanel fila(int hgap, Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, hgap, 0));
        p.setOpaque(false);
        for (Component c : comps) {
            p.add(c);
        }
        return p;
    }

    public static JPanel filaDerecha(int hgap, Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, hgap, 0));
        p.setOpaque(false);
        for (Component c : comps) {
            p.add(c);
        }
        return p;
    }

    /** Borde de línea con esquinas redondeadas. */
    public static final class BordeRedondeado extends AbstractBorder {
        private final Color color;
        private final boolean grueso;

        public BordeRedondeado(Color color, boolean grueso) {
            this.color = color;
            this.grueso = grueso;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(grueso ? 1.6f : 1f));
            g2.drawRoundRect(x, y, w - 1, h - 1, 9, 9);
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(2, 2, 2, 2);
            return i;
        }
    }

    /** Combo plano con flecha dibujada (reemplaza el estilo antiguo de Metal). */
    private static final class ComboPlanoUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Tema.TEXTO_SUAVE);
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    g2.drawPolyline(new int[] {cx - 4, cx, cx + 4}, new int[] {cy - 2, cy + 2, cy - 2}, 3);
                    g2.dispose();
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setPreferredSize(new Dimension(28, 28));
            return b;
        }

        @Override
        public void paintCurrentValue(Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false); // sin resaltado azul al tener el foco
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
            g.setColor(comboBox.isEnabled() ? Tema.SUPERFICIE : Tema.FONDO);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            g.setColor(comboBox.isEnabled() ? Tema.SUPERFICIE : Tema.FONDO);
            g.fillRect(2, 2, c.getWidth() - 4, c.getHeight() - 4);
            super.paint(g, c);
        }
    }
}
