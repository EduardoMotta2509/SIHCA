package sihca.ui.componentes;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import sihca.datos.Resultado;
import sihca.ui.Tema;

/**
 * Tabla de datos con filas alternadas, encabezado estilizado, ordenamiento por
 * columna y resaltado de estados (Vigente / De baja / Faltan / Excede...).
 */
public class Tabla extends JTable {

    private Resultado resultado = Resultado.vacio();
    private String mensajeVacio = "No hay registros para mostrar.";

    public Tabla() {
        setFont(Tema.normal(13f));
        setForeground(Tema.TEXTO);
        setRowHeight(30);
        setShowVerticalLines(false);
        setShowHorizontalLines(true);
        setGridColor(new Color(0xEEF1F6));
        setIntercellSpacing(new Dimension(0, 1));
        setSelectionBackground(Tema.SELECCION);
        setSelectionForeground(Tema.TEXTO);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFillsViewportHeight(true);
        setAutoCreateRowSorter(false);
        setAutoResizeMode(AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        JTableHeader h = getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, 34));
        h.setDefaultRenderer(new RenderEncabezado());
        Render r = new Render();
        setDefaultRenderer(Object.class, r);
        setDefaultRenderer(String.class, r);
        setDefaultRenderer(Number.class, r);
        setDefaultRenderer(Long.class, r);
        setDefaultRenderer(Double.class, r);
    }

    /** Muestra un resultado de procedimiento almacenado. */
    public void mostrar(Resultado r) {
        this.resultado = r;
        TableModel m = r.modelo();
        setModel(m);
        setRowSorter(new TableRowSorter<>(m));
        ajustarColumnas();
    }

    public Resultado resultado() {
        return resultado;
    }

    public void setMensajeVacio(String m) {
        mensajeVacio = m;
    }

    /** Índice de fila del modelo seleccionada, o -1. */
    public int filaSeleccionada() {
        int v = getSelectedRow();
        return v < 0 ? -1 : convertRowIndexToModel(v);
    }

    public Object valorSeleccionado(int col) {
        int f = filaSeleccionada();
        return f < 0 ? null : resultado.valor(f, col);
    }

    /** Oculta una columna (se conserva en el modelo para leer su valor). */
    public void ocultarColumna(String nombre) {
        for (int i = 0; i < getColumnCount(); i++) {
            if (getColumnName(i).equals(nombre)) {
                getColumnModel().removeColumn(getColumnModel().getColumn(i));
                return;
            }
        }
    }

    /** Ancho de columnas según el contenido (máx. 360 px). */
    public void ajustarColumnas() {
        FontMetrics fmH = getFontMetrics(Tema.semi(12.5f));
        FontMetrics fm = getFontMetrics(getFont());
        for (int c = 0; c < getColumnCount(); c++) {
            TableColumn col = getColumnModel().getColumn(c);
            int w = fmH.stringWidth(getColumnName(c)) + 28;
            int filas = Math.min(getRowCount(), 300);
            for (int r = 0; r < filas; r++) {
                Object v = getValueAt(r, c);
                if (v != null) {
                    w = Math.max(w, fm.stringWidth(v.toString()) + 22);
                }
            }
            col.setPreferredWidth(Math.min(w, 280));
            // las columnas cortas (códigos, horas, cantidades) no se comprimen
            col.setMinWidth(w <= 150 ? w : 110);
        }
    }

    /** Si las columnas no caben, se muestra barra de desplazamiento horizontal en lugar de comprimirlas. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        if (getParent() instanceof javax.swing.JViewport) {
            int total = 0;
            for (int c = 0; c < getColumnCount(); c++) {
                total += getColumnModel().getColumn(c).getPreferredWidth();
            }
            return total <= getParent().getWidth();
        }
        return super.getScrollableTracksViewportWidth();
    }

    public JScrollPane enScroll() {
        JScrollPane sp = new JScrollPane(this);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Tema.SUPERFICIE);
        sp.setBackground(Tema.SUPERFICIE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() == 0) {
            g.setFont(Tema.normal(13.5f));
            g.setColor(Tema.TEXTO_TENUE);
            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(mensajeVacio)) / 2;
            g.drawString(mensajeVacio, Math.max(12, x), 40);
        }
    }

    // ---------------------------------------------------------------- renderers
    private final class Render extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int fila, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, false, fila, col);
            l.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            l.setFont(Tema.normal(13f));
            l.setForeground(Tema.TEXTO);
            if (!sel) {
                l.setBackground(fila % 2 == 0 ? Tema.SUPERFICIE : Tema.FILA_ALTERNA);
            }
            l.setHorizontalAlignment(v instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (v instanceof Double) {
                l.setText(String.format("%,.1f", (Double) v));
            }
            if (v instanceof String) {
                String s = (String) v;
                if (s.equals("De baja") || s.equals("Desactivada") || s.equals("EXCEDE AFORO")
                        || s.equals("Excede el plan") || s.equals("Reubicar") || s.startsWith("Sí (revisar")) {
                    l.setForeground(Tema.PELIGRO);
                    l.setFont(Tema.medio(13f));
                } else if (s.startsWith("Faltan") || s.equals("Mejorable") || s.equals("(sin docente)")
                        || s.equals("Cerrado")) {
                    l.setForeground(Tema.ADVERTENCIA);
                    l.setFont(Tema.medio(13f));
                } else if (s.equals("Completo") || s.equals("Sin colisión") || s.equals("Adecuado")
                        || s.equals("Vigente") || s.equals("Disponible") || s.equals("Activa")
                        || (s.equals("Activo") && getColumnName(col).equals("Estado"))) {
                    l.setForeground(Tema.EXITO);
                    l.setFont(Tema.medio(13f));
                }
            }
            l.setToolTipText(v != null && v.toString().length() > 40 ? v.toString() : null);
            return l;
        }
    }

    private static final class RenderEncabezado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco, int fila, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, fila, col);
            l.setFont(Tema.semi(12.5f));
            l.setForeground(Tema.TEXTO_SUAVE);
            l.setBackground(new Color(0xF0F3F8));
            l.setOpaque(true);
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                    BorderFactory.createEmptyBorder(0, 10, 0, 10)));
            Class<?> cls = t.getModel().getColumnCount() > 0 ? t.getColumnClass(col) : Object.class;
            l.setHorizontalAlignment(Number.class.isAssignableFrom(cls) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            return l;
        }
    }
}
