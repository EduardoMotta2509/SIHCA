package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import sihca.dao.AsignaturaDAO;
import sihca.dao.ConsultaDAO;
import sihca.dao.DocenteDAO;
import sihca.dao.GradoSeccionDAO;
import sihca.dao.PeriodoDAO;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Exportador;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Opcion;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/**
 * Módulo Reportes: 13 consultas sobre la base de datos, cada una implementada
 * como procedimiento almacenado (11 de ellas con JOIN entre varias tablas).
 */
public class PanelReportes extends Modulo {

    /** Tipos de parámetro que puede pedir un reporte. */
    private enum Param { PERIODO, SECCION, DOCENTE, GRADO_OPCIONAL, DIA, BLOQUE, MATERIA_OPCIONAL, ANIO_A, ANIO_B }

    /** Definición de un reporte. */
    private static final class Def {
        final String codigo;
        final String nombre;
        final String descripcion;
        final boolean join;
        final String procedimiento;
        final Param[] params;

        Def(String codigo, String nombre, String descripcion, boolean join, String procedimiento, Param... params) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.join = join;
            this.procedimiento = procedimiento;
            this.params = params;
        }
    }

    private static final Def[] REPORTES = {
        new Def("R01", "Matriz de horarios por sección", "Bloques de la semana de una sección con docente, ambiente "
                + "y exigencia cognitiva (vista que se publica en EdusoftNet).", true, "sp_rep_matriz_seccion",
                Param.PERIODO, Param.SECCION),
        new Def("R02", "Horario personal del docente", "Todas las actividades de un docente: clases, recesos y "
                + "reuniones, con las secciones atendidas.", true, "sp_rep_horario_docente", Param.PERIODO, Param.DOCENTE),
        new Def("R03", "Carga horaria docente", "Cuadro de carga lectiva y no lectiva por docente: horas de clase, "
                + "turnos de receso y refrigerio, reuniones y horas reloj.", true, "sp_rep_carga_docente", Param.PERIODO),
        new Def("R04", "Cumplimiento del plan de estudios", "Compara las horas que exige el plan con las programadas "
                + "en cada sección y detecta faltantes antes de publicar.", true, "sp_rep_cumplimiento_plan",
                Param.PERIODO, Param.GRADO_OPCIONAL),
        new Def("R05", "Aforo y cruces en espacios compartidos", "Franjas en que un ambiente es usado por varios "
                + "grupos a la vez, comparadas con su límite de grupos.", true, "sp_rep_aforo_espacios", Param.PERIODO),
        new Def("R06", "Turnos de vigilancia de recesos", "Docentes que cuidan recreos y refrigerios; señala quién "
                + "repite vigilancia en días consecutivos.", true, "sp_rep_turnos_receso", Param.PERIODO),
        new Def("R07", "Bloqueos del Consejo y Pastoral", "Reuniones del Consejo Directivo y jornadas de Pastoral, "
                + "con verificación de que no haya clases en conflicto.", true, "sp_rep_bloqueos_prioritarios", Param.PERIODO),
        new Def("R08", "Docentes disponibles para un reemplazo", "Especialistas libres en una franja, ordenados de "
                + "menor a mayor carga semanal.", true, "sp_rep_docentes_disponibles",
                Param.PERIODO, Param.DIA, Param.BLOQUE, Param.MATERIA_OPCIONAL),
        new Def("R09", "Materias exigentes en las primeras horas", "Porcentaje de bloques de exigencia Alta "
                + "dictados antes del recreo (10:40) en cada sección.", true, "sp_rep_exigencia_primeras_horas", Param.PERIODO),
        new Def("R10", "Ocupación de espacios físicos", "Minutos usados frente a la capacidad semanal de cada "
                + "ambiente (límite de grupos × jornada).", true, "sp_rep_ocupacion_espacios", Param.PERIODO),
        new Def("R11", "Comparativo histórico de carga docente", "Horas por docente en dos años lectivos y su "
                + "variación (aprovecha el registro histórico).", true, "sp_rep_comparativo_periodos", Param.ANIO_A, Param.ANIO_B),
        new Def("R12", "Resumen de bloques por tipo y día", "Cantidad de bloques de clase, receso, Consejo y Pastoral "
                + "por día de la semana.", false, "sp_rep_resumen_naturaleza", Param.PERIODO),
        new Def("R13", "Plantilla docente por modalidad", "Docentes vigentes y dados de baja por modalidad de "
                + "contratación y condición especial.", false, "sp_rep_plantilla_docente"),
    };

    private final JList<Def> lista;
    private final JLabel nombre = UI.titulo("", 17f);
    private final JLabel descripcion = UI.texto("", 13f, Tema.TEXTO_SUAVE);
    private final JPanel parametros = UI.fila(8);
    private final Tabla tabla = new Tabla();
    private final JLabel pie = UI.texto(" ", 12.5f, Tema.TEXTO_SUAVE);
    private final List<Object[]> controles = new ArrayList<>(); // {Param, JComponent}

    public PanelReportes(VentanaPrincipal v) {
        super(v);
        DefaultListModel<Def> m = new DefaultListModel<>();
        for (Def d : REPORTES) {
            m.addElement(d);
        }
        lista = new JList<>(m);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setCellRenderer(new RenderReporte());
        lista.setBackground(Tema.SUPERFICIE);
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lista.getSelectedValue() != null) {
                elegir(lista.getSelectedValue());
            }
        });
        Tarjeta izq = new Tarjeta(new BorderLayout(0, 8));
        izq.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
        izq.setPreferredSize(new Dimension(320, 10));
        JLabel ti = UI.titulo("  13 reportes  ·  11 con JOIN", 13.5f);
        izq.add(ti, BorderLayout.NORTH);
        JScrollPane sl = new JScrollPane(lista);
        sl.setBorder(BorderFactory.createEmptyBorder());
        sl.getVerticalScrollBar().setUnitIncrement(16);
        izq.add(sl, BorderLayout.CENTER);
        add(izq, BorderLayout.WEST);

        Tarjeta der = new Tarjeta(new BorderLayout(0, 12));
        JPanel cab = new JPanel(new BorderLayout(0, 4));
        cab.setOpaque(false);
        cab.add(nombre, BorderLayout.NORTH);
        cab.add(descripcion, BorderLayout.CENTER);
        Boton generar = boton("Generar reporte", Iconos.Tipo.REFRESCAR, Boton.Estilo.PRIMARIO);
        generar.addActionListener(e -> generar());
        JPanel fp = new JPanel(new BorderLayout(10, 0));
        fp.setOpaque(false);
        fp.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        fp.add(parametros, BorderLayout.CENTER);
        fp.add(generar, BorderLayout.EAST);
        cab.add(fp, BorderLayout.SOUTH);
        der.add(cab, BorderLayout.NORTH);
        JScrollPane st = tabla.enScroll();
        st.setBorder(new UI.BordeRedondeado(Tema.BORDE, false));
        der.add(st, BorderLayout.CENTER);
        JPanel abajo = new JPanel(new BorderLayout());
        abajo.setOpaque(false);
        abajo.add(pie, BorderLayout.WEST);
        Boton csv = boton("Exportar a Excel", Iconos.Tipo.EXPORTAR, Boton.Estilo.SECUNDARIO);
        Boton imp = boton("Imprimir / PDF", Iconos.Tipo.IMPRIMIR, Boton.Estilo.SECUNDARIO);
        csv.addActionListener(e -> Exportador.csv(this, tabla.resultado(), lista.getSelectedValue().codigo + " "
                + lista.getSelectedValue().nombre + " " + Sesion.periodo()));
        imp.addActionListener(e -> Exportador.imprimir(this, tabla, lista.getSelectedValue().codigo + " - "
                + lista.getSelectedValue().nombre));
        abajo.add(UI.filaDerecha(8, csv, imp), BorderLayout.EAST);
        der.add(abajo, BorderLayout.SOUTH);
        add(der, BorderLayout.CENTER);
        ((BorderLayout) getLayout()).setHgap(16);
        tabla.setMensajeVacio("Elija los parámetros y presione «Generar reporte».");
    }

    @Override public String titulo() { return "Reportes"; }
    @Override public String descripcion() { return "Consultas de gestión sobre horarios, carga docente, espacios e histórico"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.REPORTE; }
    @Override public boolean dependeDelPeriodo() { return true; }

    @Override
    public void refrescar() {
        if (lista.getSelectedIndex() < 0) {
            lista.setSelectedIndex(0);
        } else {
            elegir(lista.getSelectedValue());
        }
    }

    /** Abre directamente un reporte (desde otros módulos). */
    public void abrir(String codigo) {
        for (int i = 0; i < REPORTES.length; i++) {
            if (REPORTES[i].codigo.equals(codigo)) {
                lista.setSelectedIndex(i);
            }
        }
    }

    private void elegir(Def d) {
        nombre.setText(d.codigo + "  ·  " + d.nombre);
        descripcion.setText("<html>" + d.descripcion + "</html>");
        parametros.removeAll();
        controles.clear();
        for (Param p : d.params) {
            JComponent c = control(p);
            if (c != null) {
                parametros.add(UI.etiqueta(etiqueta(p)));
                parametros.add(c);
                controles.add(new Object[] {p, c});
            }
        }
        if (d.params.length == 0 || (d.params.length == 1 && d.params[0] == Param.PERIODO)) {
            JLabel l = UI.texto(d.params.length == 0 ? "Este reporte no requiere parámetros."
                    : "Periodo de trabajo: " + Sesion.periodo() + " (cámbielo arriba a la derecha).", 13f, Tema.TEXTO_SUAVE);
            parametros.add(l);
        }
        parametros.revalidate();
        parametros.repaint();
        generar();
    }

    private String etiqueta(Param p) {
        switch (p) {
            case SECCION: return "Sección";
            case DOCENTE: return "Docente";
            case GRADO_OPCIONAL: return "Grado";
            case DIA: return "Día";
            case BLOQUE: return "Bloque";
            case MATERIA_OPCIONAL: return "Asignatura";
            case ANIO_A: return "Año A";
            case ANIO_B: return "Año B";
            default: return "";
        }
    }

    @SuppressWarnings("unchecked")
    private JComponent control(Param p) {
        JComboBox<Opcion> c = UI.combo();
        try {
            switch (p) {
                case PERIODO:
                    return null;
                case SECCION: {
                    Resultado r = GradoSeccionDAO.listarSecciones(null);
                    for (int i = 0; i < r.filas(); i++) {
                        c.addItem(new Opcion(r.numero(i, 0), r.texto(i, 1)));
                    }
                    UI.seleccionar(c, 211);
                    c.setPreferredSize(new Dimension(230, 36));
                    break;
                }
                case DOCENTE: {
                    Resultado r = DocenteDAO.listar("", true);
                    for (int i = 0; i < r.filas(); i++) {
                        c.addItem(new Opcion(r.texto(i, 0), r.texto(i, 1)));
                    }
                    c.setPreferredSize(new Dimension(300, 36));
                    break;
                }
                case GRADO_OPCIONAL: {
                    c.addItem(new Opcion(null, "Todos"));
                    Resultado r = GradoSeccionDAO.listarGrados();
                    for (int i = 0; i < r.filas(); i++) {
                        c.addItem(new Opcion(r.numero(i, 0), r.texto(i, 1) + " - " + r.texto(i, 2)));
                    }
                    UI.seleccionar(c, 21);
                    c.setPreferredSize(new Dimension(210, 36));
                    break;
                }
                case DIA:
                    for (String d : Bloques.DIAS) {
                        c.addItem(new Opcion(d, d));
                    }
                    c.setPreferredSize(new Dimension(125, 36));
                    break;
                case BLOQUE:
                    for (String[] b : Bloques.CLASES) {
                        c.addItem(new Opcion(b[0] + "|" + b[1], b[0] + " - " + b[1]));
                    }
                    c.setSelectedIndex(2);
                    c.setPreferredSize(new Dimension(140, 36));
                    break;
                case MATERIA_OPCIONAL: {
                    c.addItem(new Opcion(null, "Todas"));
                    Resultado r = AsignaturaDAO.listar(false);
                    for (int i = 0; i < r.filas(); i++) {
                        c.addItem(new Opcion(r.texto(i, 0), r.texto(i, 0)));
                    }
                    UI.seleccionar(c, "Matemática");
                    c.setPreferredSize(new Dimension(200, 36));
                    break;
                }
                case ANIO_A:
                case ANIO_B: {
                    Resultado r = PeriodoDAO.listar();
                    for (int i = 0; i < r.filas(); i++) {
                        c.addItem(new Opcion(r.numero(i, 0), r.texto(i, 0)));
                    }
                    int idx = p == Param.ANIO_A ? Math.min(1, c.getItemCount() - 1) : 0;
                    if (p == Param.ANIO_A && r.filas() > 2) {
                        UI.seleccionar(c, Sesion.periodo() - 1);
                        idx = c.getSelectedIndex();
                    } else if (p == Param.ANIO_B) {
                        UI.seleccionar(c, Sesion.periodo());
                        idx = c.getSelectedIndex();
                    }
                    c.setSelectedIndex(Math.max(idx, 0));
                    c.setPreferredSize(new Dimension(100, 36));
                    break;
                }
                default:
                    break;
            }
        } catch (java.sql.SQLException ex) {
            sihca.ui.componentes.Mensajes.error(this, ex);
        }
        return c;
    }

    private void generar() {
        Def d = lista.getSelectedValue();
        if (d == null) {
            return;
        }
        List<Object> valores = new ArrayList<>();
        StringBuilder llamada = new StringBuilder();
        for (Param p : d.params) {
            if (p == Param.PERIODO) {
                valores.add(Sesion.periodo());
                continue;
            }
            for (Object[] c : controles) {
                if (c[0] == p) {
                    Object v = UI.valor((JComboBox<?>) c[1]);
                    if (p == Param.BLOQUE) {
                        String[] b = v.toString().split("\\|");
                        valores.add(b[0]);
                        valores.add(b[1]);
                    } else {
                        valores.add(v);
                    }
                }
            }
        }
        for (Object v : valores) {
            llamada.append(llamada.length() == 0 ? "" : ", ").append(v == null ? "NULL"
                    : v instanceof Number ? v.toString() : "'" + v + "'");
        }
        ejecutar(() -> {
            long t0 = System.currentTimeMillis();
            Resultado r = ConsultaDAO.reporte(d.procedimiento, valores.toArray());
            tabla.mostrar(r);
            long ms = System.currentTimeMillis() - t0;
            pie.setText(r.filas() + " filas  ·  CALL " + d.procedimiento + "(" + llamada + ")  ·  " + ms + " ms   ");
            ventana.estado(d.codigo + " generado.");
        });
    }

    /** Dibuja cada reporte de la lista como una pequeña tarjeta. */
    private static final class RenderReporte extends JPanel implements ListCellRenderer<Def> {
        private final JLabel cod = new JLabel();
        private final JLabel nom = new JLabel();
        private final JLabel tag = new JLabel();

        RenderReporte() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
            cod.setFont(Tema.semi(12f));
            cod.setPreferredSize(new Dimension(34, 20));
            nom.setFont(Tema.normal(13f));
            tag.setFont(Tema.medio(10.5f));
            add(cod, BorderLayout.WEST);
            add(nom, BorderLayout.CENTER);
            add(tag, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Def> l, Def d, int i, boolean sel, boolean foco) {
            cod.setText(d.codigo);
            nom.setText("<html><div style='width:200px'>" + d.nombre + "</div></html>");
            tag.setText(d.join ? "JOIN" : "1 tabla");
            tag.setForeground(d.join ? Tema.PRIMARIO : Tema.TEXTO_TENUE);
            setBackground(sel ? Tema.SELECCION : Tema.SUPERFICIE);
            cod.setForeground(sel ? Tema.PRIMARIO : Tema.TEXTO_TENUE);
            nom.setForeground(Tema.TEXTO);
            nom.setFont(sel ? Tema.medio(13f) : Tema.normal(13f));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF0F2F6)),
                    BorderFactory.createEmptyBorder(9, 10, 9, 10)));
            return this;
        }
    }
}
