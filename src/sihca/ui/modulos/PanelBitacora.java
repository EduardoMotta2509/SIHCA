package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import sihca.dao.ConsultaDAO;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Exportador;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/**
 * Módulo Bitácora: auditoría de todos los cambios. Los registros los generan
 * exclusivamente los triggers AFTER INSERT/UPDATE/DELETE de la base de datos.
 */
public class PanelBitacora extends Modulo {

    private static final String[] TABLAS = {"Todas", "Actividad", "Docente_Actividad", "Seccion_Periodo", "Docente",
        "Especializado_En", "Asignatura", "Espacio", "Grado_Escolar", "Seccion", "Grado_Materia", "Periodo_Academico",
        "Usuario_Sistema"};

    private final JTextField desde = UI.campo(9);
    private final JTextField hasta = UI.campo(9);
    private final JComboBox<String> tabla = UI.combo(TABLAS);
    private final JTextField usuario = UI.campo(10);
    private final Tabla eventos = new Tabla();
    private final JTextArea antes = area();
    private final JTextArea despues = area();

    public PanelBitacora(VentanaPrincipal v) {
        super(v);
        desde.setText(LocalDate.now().minusDays(30).toString());
        hasta.setText(LocalDate.now().toString());
        desde.setToolTipText("Formato AAAA-MM-DD");
        hasta.setToolTipText("Formato AAAA-MM-DD");
        tabla.setPreferredSize(new Dimension(180, 36));
        usuario.setPreferredSize(new Dimension(130, 36));
        desde.setPreferredSize(new Dimension(115, 36));
        hasta.setPreferredSize(new Dimension(115, 36));
        Boton buscar = boton("Consultar", Iconos.Tipo.BUSCAR, Boton.Estilo.PRIMARIO);
        Boton csv = boton("Exportar", Iconos.Tipo.EXPORTAR, Boton.Estilo.SECUNDARIO);
        buscar.addActionListener(e -> refrescar());
        csv.addActionListener(e -> Exportador.csv(this, eventos.resultado(), "Bitacora " + LocalDate.now()));
        add(barra(UI.fila(8, UI.etiqueta("Desde"), desde, UI.etiqueta("Hasta"), hasta, UI.etiqueta("Tabla"), tabla,
                UI.etiqueta("Usuario"), usuario), UI.filaDerecha(8, csv, buscar)), BorderLayout.NORTH);

        Tarjeta t = new Tarjeta(new BorderLayout());
        t.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(eventos.enScroll(), BorderLayout.CENTER);

        JPanel detalle = new JPanel(new GridLayout(1, 2, 14, 0));
        detalle.setOpaque(false);
        detalle.add(caja("Datos antes del cambio", antes));
        detalle.add(caja("Datos después del cambio", despues));
        detalle.setPreferredSize(new Dimension(10, 170));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, t, detalle);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setResizeWeight(0.72);
        split.setDividerSize(10);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);

        eventos.getSelectionModel().addListSelectionListener(e -> {
            int f = eventos.filaSeleccionada();
            antes.setText(f < 0 ? "" : json(eventos.resultado().texto(f, 6)));
            despues.setText(f < 0 ? "" : json(eventos.resultado().texto(f, 7)));
            antes.setCaretPosition(0);
            despues.setCaretPosition(0);
        });
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        a.setForeground(Tema.TEXTO);
        a.setBackground(Tema.FILA_ALTERNA);
        a.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return a;
    }

    private JPanel caja(String titulo, JTextArea a) {
        Tarjeta c = new Tarjeta(new BorderLayout(0, 6));
        c.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        c.add(UI.titulo(titulo, 13.5f), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createEmptyBorder());
        c.add(sp, BorderLayout.CENTER);
        return c;
    }

    /** Muestra el JSON de la bitácora con un campo por línea. */
    private static String json(String s) {
        if (s == null || s.isEmpty()) {
            return "(sin datos)";
        }
        String t = s.trim();
        if (t.startsWith("{")) {
            t = t.substring(1, t.length() - 1);
        }
        StringBuilder r = new StringBuilder();
        boolean comillas = false;
        for (char c : t.toCharArray()) {
            if (c == '"') {
                comillas = !comillas;
                continue;
            }
            if (c == ',' && !comillas) {
                r.append('\n');
                continue;
            }
            r.append(c);
        }
        String[] lineas = r.toString().split("\n");
        StringBuilder out = new StringBuilder();
        for (String l : lineas) {
            out.append(l.trim()).append('\n');
        }
        return out.toString().trim();
    }

    @Override public String titulo() { return "Bitácora de auditoría"; }
    @Override public String descripcion() { return "Registro automático (por triggers) de quién cambió qué y cuándo, con los valores anteriores y nuevos"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.BITACORA; }
    @Override public String nombreMenu() { return "Bitácora"; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            String tb = "Todas".equals(tabla.getSelectedItem()) ? null : (String) tabla.getSelectedItem();
            eventos.mostrar(ConsultaDAO.bitacora(vacio(desde), vacio(hasta), tb, vacio(usuario)));
            eventos.ocultarColumna("Antes");
            eventos.ocultarColumna("Después");
            antes.setText("");
            despues.setText("");
            ventana.estado(eventos.getRowCount() + " eventos (máx. 1000 más recientes)  ·  sp_bitacora_listar");
        });
    }

    private static String vacio(JTextField f) {
        String s = f.getText().trim();
        return s.isEmpty() ? null : s;
    }
}
