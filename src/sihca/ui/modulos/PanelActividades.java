package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import sihca.dao.ActividadDAO;
import sihca.dao.AsignaturaDAO;
import sihca.dao.ConsultaDAO;
import sihca.dao.DocenteDAO;
import sihca.dao.EspacioDAO;
import sihca.dao.GradoSeccionDAO;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Opcion;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/**
 * Módulo Actividades: bloques horarios del periodo de trabajo (clases, recesos,
 * Consejo y Pastoral) con las secciones y docentes que participan.
 */
public class PanelActividades extends Modulo {

    private static final String TODOS = "Todos";
    private static final String[] NATURALEZAS = {"Clase", "Receso", "Consejo", "Pastoral"};

    private final Tabla tabla = new Tabla();
    private final JComboBox<String> dia = UI.combo();
    private final JComboBox<String> tipo = UI.combo();
    private final JComboBox<Opcion> seccion = UI.combo();
    private final JComboBox<Opcion> docente = UI.combo();
    private boolean cargando;

    public PanelActividades(VentanaPrincipal v) {
        super(v);
        dia.addItem(TODOS);
        for (String d : Bloques.DIAS) {
            dia.addItem(d);
        }
        tipo.addItem(TODOS);
        for (String n : NATURALEZAS) {
            tipo.addItem(n);
        }
        dia.setPreferredSize(new Dimension(120, 36));
        tipo.setPreferredSize(new Dimension(115, 36));
        seccion.setPreferredSize(new Dimension(220, 36));
        docente.setPreferredSize(new Dimension(250, 36));
        for (JComboBox<?> c : new JComboBox<?>[] {dia, tipo, seccion, docente}) {
            c.addActionListener(e -> {
                if (!cargando) {
                    cargarTabla();
                }
            });
        }
        JPanel filtros = UI.fila(8, UI.etiqueta("Día"), dia, UI.etiqueta("Tipo"), tipo,
                UI.etiqueta("Sección"), seccion, UI.etiqueta("Docente"), docente);

        Boton programar = botonEscritura("Programar clase", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton nuevo = botonEscritura("Otro bloque", Iconos.Tipo.ACTIVIDAD, Boton.Estilo.SECUNDARIO);
        Boton editar = botonEscritura("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton part = botonEscritura("Secciones y docentes", Iconos.Tipo.PERSONAS, Boton.Estilo.SECUNDARIO);
        Boton eliminar = botonEscritura("Eliminar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        programar.addActionListener(e -> programarClase());
        nuevo.addActionListener(e -> formBloque(false));
        editar.addActionListener(e -> formBloque(true));
        part.addActionListener(e -> participantes());
        eliminar.addActionListener(e -> eliminar());

        JPanel arriba = new JPanel(new GridLayout(2, 1, 0, 12));
        arriba.setOpaque(false);
        arriba.add(filtros);
        arriba.add(UI.filaDerecha(8, eliminar, part, editar, nuevo, programar));
        add(arriba, BorderLayout.NORTH);

        Tarjeta t = new Tarjeta(new BorderLayout());
        t.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(tabla.enScroll(), BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);
        tabla.setMensajeVacio("No hay bloques que coincidan con los filtros en este periodo.");
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && Sesion.puedeEscribir()) {
                    participantes();
                }
            }
        });
    }

    @Override public String titulo() { return "Actividades (bloques horarios)"; }
    @Override public String descripcion() { return "Clases, recesos, Consejo Directivo y Pastoral del periodo de trabajo"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.ACTIVIDAD; }
    @Override public String nombreMenu() { return "Actividades"; }
    @Override public boolean dependeDelPeriodo() { return true; }

    @Override
    public void refrescar() {
        cargando = true;
        Object s = UI.valor(seccion);
        Object d = UI.valor(docente);
        ejecutar(() -> {
            seccion.removeAllItems();
            seccion.addItem(new Opcion(null, "Todas"));
            Resultado r = GradoSeccionDAO.listarSecciones(null);
            for (int i = 0; i < r.filas(); i++) {
                seccion.addItem(new Opcion(r.numero(i, 0), r.texto(i, 1)));
            }
            docente.removeAllItems();
            docente.addItem(new Opcion(null, TODOS));
            Resultado dr = DocenteDAO.listar("", true);
            for (int i = 0; i < dr.filas(); i++) {
                docente.addItem(new Opcion(dr.texto(i, 0), dr.texto(i, 1)));
            }
        });
        UI.seleccionar(seccion, s);
        UI.seleccionar(docente, d);
        cargando = false;
        cargarTabla();
    }

    private void cargarTabla() {
        ejecutar(() -> {
            String d = TODOS.equals(dia.getSelectedItem()) ? null : (String) dia.getSelectedItem();
            String n = TODOS.equals(tipo.getSelectedItem()) ? null : (String) tipo.getSelectedItem();
            Object s = UI.valor(seccion);
            Object doc = UI.valor(docente);
            tabla.mostrar(ActividadDAO.listar(Sesion.periodo(), d, n,
                    s == null ? null : ((Number) s).intValue(), (String) doc));
            ventana.estado(tabla.getRowCount() + " bloques en " + Sesion.periodo() + "  ·  sp_actividad_listar");
        });
    }

    private boolean periodoEditable() {
        if (Sesion.periodoCerrado()) {
            Mensajes.advertencia(this, "Periodo cerrado", "El periodo " + Sesion.periodo() + " está cerrado y su horario "
                    + "es histórico (solo lectura). Elija un periodo activo en «Periodo de trabajo».");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ programar clase
    private void programarClase() {
        if (!periodoEditable()) {
            return;
        }
        JComboBox<String> cDia = UI.combo(Bloques.DIAS);
        JComboBox<Opcion> cBloque = UI.combo();
        for (String[] b : Bloques.CLASES) {
            cBloque.addItem(new Opcion(b[0] + "|" + b[1], b[2] + "   " + b[0] + " - " + b[1]));
        }
        JComboBox<Opcion> cSec = UI.combo();
        JComboBox<String> cMat = UI.combo();
        JComboBox<String> cEsp = UI.combo();
        JComboBox<Opcion> cDoc = UI.combo();
        Object filtroSec = UI.valor(seccion);
        ejecutar(() -> {
            Resultado r = GradoSeccionDAO.listarSecciones(null);
            for (int i = 0; i < r.filas(); i++) {
                cSec.addItem(new Opcion(r.numero(i, 0) + "|" + r.texto(i, 3) + "|" + r.texto(i, 4) + "|" + r.texto(i, 2),
                        r.texto(i, 1)));
            }
            Resultado m = AsignaturaDAO.listar(false);
            for (int i = 0; i < m.filas(); i++) {
                cMat.addItem(m.texto(i, 0));
            }
            Resultado e = EspacioDAO.listar(false);
            for (int i = 0; i < e.filas(); i++) {
                cEsp.addItem(e.texto(i, 0));
            }
        });
        if (filtroSec != null) {
            for (int i = 0; i < cSec.getItemCount(); i++) {
                if (cSec.getItemAt(i).valor().toString().startsWith(filtroSec + "|")) {
                    cSec.setSelectedIndex(i);
                }
            }
        }
        Runnable aulaSugerida = () -> {
            Object v = UI.valor(cSec);
            if (v != null) {
                String[] p = v.toString().split("\\|");
                String grado = p[1].replaceAll("[^0-9]", "");
                String aula = "Inicial".equals(p[3]) ? "Aula " + p[1] + " " + p[2] : "Aula " + grado + p[2] + " " + p[3];
                cEsp.setSelectedItem(aula);
            }
        };
        Runnable disponibles = () -> {
            cDoc.removeAllItems();
            String[] b = UI.valor(cBloque).toString().split("\\|");
            try {
                Resultado r = ConsultaDAO.reporte("sp_rep_docentes_disponibles", Sesion.periodo(),
                        cDia.getSelectedItem(), b[0], b[1], cMat.getSelectedItem());
                for (int i = 0; i < r.filas(); i++) {
                    cDoc.addItem(new Opcion(r.texto(i, 0), r.texto(i, 1) + "  ·  " + r.texto(i, 5) + " h/sem"));
                }
            } catch (SQLException ex) {
                Mensajes.error(this, ex);
            }
            cDoc.addItem(new Opcion(null, "(asignar después)"));
        };
        cSec.addActionListener(e -> aulaSugerida.run());
        cDia.addActionListener(e -> disponibles.run());
        cBloque.addActionListener(e -> disponibles.run());
        cMat.addActionListener(e -> disponibles.run());
        aulaSugerida.run();
        disponibles.run();

        final int[] id = new int[1];
        if (new Formulario(this, "Programar clase  ·  periodo " + Sesion.periodo(),
                "Registra en una sola operación el bloque, la sección y el docente (sp_programar_clase). "
                        + "Solo se listan los especialistas libres en esa franja.", "Programar clase")
                .campo("Día", cDia)
                .campo("Bloque horario", cBloque)
                .campo("Sección", cSec)
                .campo("Asignatura", cMat, "Debe figurar en el plan de estudios del grado")
                .campo("Ambiente físico", cEsp, "Se sugiere el aula de la sección; cambie si usa cancha, laboratorio, etc.")
                .campo("Docente especialista disponible", cDoc, "Ordenados de menor a mayor carga semanal")
                .mostrar(() -> {
                    String[] b = UI.valor(cBloque).toString().split("\\|");
                    int idSec = Integer.parseInt(UI.valor(cSec).toString().split("\\|")[0]);
                    Object dni = UI.valor(cDoc);
                    id[0] = ActividadDAO.programarClase(Sesion.periodo(), (String) cDia.getSelectedItem(), b[0], b[1],
                            (String) cMat.getSelectedItem(), (String) cEsp.getSelectedItem(), idSec, (String) dni);
                })) {
            cargarTabla();
            ventana.estado("Clase programada con ID " + id[0] + " (sp_programar_clase).");
        }
    }

    // ------------------------------------------------------------------ bloque genérico
    private void formBloque(boolean edicion) {
        if (!periodoEditable() || (edicion && !haySeleccion(tabla, "un bloque"))) {
            return;
        }
        JComboBox<String> cTipo = UI.combo(NATURALEZAS);
        JComboBox<String> cDia = UI.combo(Bloques.DIAS);
        JTextField ini = UI.campo(6);
        JTextField fin = UI.campo(6);
        JComboBox<String> cMat = UI.combo();
        JComboBox<String> cEsp = UI.combo();
        ejecutar(() -> {
            cMat.addItem("");
            Resultado m = AsignaturaDAO.listar(false);
            for (int i = 0; i < m.filas(); i++) {
                cMat.addItem(m.texto(i, 0));
            }
            Resultado e = EspacioDAO.listar(false);
            for (int i = 0; i < e.filas(); i++) {
                cEsp.addItem(e.texto(i, 0));
            }
        });
        int id = 0;
        if (edicion) {
            Resultado r = tabla.resultado();
            int f = tabla.filaSeleccionada();
            id = (int) r.numero(f, 0);
            cDia.setSelectedItem(r.texto(f, 1));
            ini.setText(r.texto(f, 2));
            fin.setText(r.texto(f, 3));
            cTipo.setSelectedItem(r.texto(f, 4));
            cMat.setSelectedItem("-".equals(r.texto(f, 5)) ? "" : r.texto(f, 5));
            cEsp.setSelectedItem(r.texto(f, 6));
        } else {
            cTipo.setSelectedItem("Receso");
            ini.setText("10:40");
            fin.setText("11:00");
        }
        cMat.setEnabled("Clase".equals(cTipo.getSelectedItem()));
        cTipo.addActionListener(e -> {
            boolean clase = "Clase".equals(cTipo.getSelectedItem());
            cMat.setEnabled(clase);
            if (!clase) {
                cMat.setSelectedItem("");
            }
        });
        JPanel horas = new JPanel(new GridLayout(1, 2, 10, 0));
        horas.setOpaque(false);
        horas.add(ini);
        horas.add(fin);
        final int idf = id;
        if (new Formulario(this, edicion ? "Modificar bloque " + id : "Nuevo bloque horario",
                "Para clases conviene usar «Programar clase». Aquí puede registrar recesos, reuniones del Consejo, "
                        + "jornadas de Pastoral o un horario especial. Luego asigne secciones y docentes.",
                edicion ? "Guardar cambios" : "Registrar bloque")
                .campo("Tipo de bloque", cTipo)
                .campo("Día", cDia)
                .campo("Hora de inicio y de fin (HH:MM)", horas, "Jornada escolar: 07:00 a 17:00")
                .campo("Asignatura (solo para clases)", cMat)
                .campo("Ambiente físico", cEsp)
                .mostrar(() -> {
                    String materia = (String) cMat.getSelectedItem();
                    if (edicion) {
                        ActividadDAO.actualizar(idf, ini.getText().trim(), fin.getText().trim(),
                                (String) cDia.getSelectedItem(), (String) cTipo.getSelectedItem(), materia,
                                Sesion.periodo(), (String) cEsp.getSelectedItem());
                    } else {
                        ActividadDAO.insertar(ini.getText().trim(), fin.getText().trim(), (String) cDia.getSelectedItem(),
                                (String) cTipo.getSelectedItem(), materia, Sesion.periodo(), (String) cEsp.getSelectedItem());
                    }
                })) {
            cargarTabla();
        }
    }

    private void eliminar() {
        if (!periodoEditable() || !haySeleccion(tabla, "un bloque")) {
            return;
        }
        Resultado r = tabla.resultado();
        int f = tabla.filaSeleccionada();
        int id = (int) r.numero(f, 0);
        String desc = r.texto(f, 4) + " " + ("-".equals(r.texto(f, 5)) ? "" : r.texto(f, 5)) + " del " + r.texto(f, 1)
                + " de " + r.texto(f, 2) + " a " + r.texto(f, 3);
        if (Mensajes.confirmar(this, "Eliminar bloque", "¿Eliminar el bloque " + id + " (" + desc.trim() + ")?\n\n"
                + "También se quitarán sus secciones y docentes. Cada borrado queda en la bitácora.", "Eliminar")) {
            if (ejecutar(() -> ActividadDAO.eliminar(id))) {
                cargarTabla();
                ventana.estado("Bloque " + id + " eliminado (sp_actividad_eliminar).");
            }
        }
    }

    // ------------------------------------------------------------------ participantes
    private void participantes() {
        if (!haySeleccion(tabla, "un bloque")) {
            return;
        }
        Resultado r = tabla.resultado();
        int f = tabla.filaSeleccionada();
        int id = (int) r.numero(f, 0);
        String natur = r.texto(f, 4);
        String titulo = "Bloque " + id + ":  " + natur + ("-".equals(r.texto(f, 5)) ? "" : " de " + r.texto(f, 5))
                + "  ·  " + r.texto(f, 1) + " " + r.texto(f, 2) + "-" + r.texto(f, 3) + "  ·  " + r.texto(f, 6);

        JDialog d = new JDialog(ventana, "Secciones y docentes del bloque " + id, JDialog.ModalityType.APPLICATION_MODAL);
        JPanel raiz = new JPanel(new BorderLayout(0, 14));
        raiz.setBackground(Tema.SUPERFICIE);
        raiz.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JPanel cab = new JPanel(new BorderLayout(0, 4));
        cab.setOpaque(false);
        cab.add(UI.titulo("Participantes del bloque", 17f), BorderLayout.NORTH);
        cab.add(UI.texto(titulo, 13f, Tema.TEXTO_SUAVE), BorderLayout.CENTER);
        raiz.add(cab, BorderLayout.NORTH);

        Tabla tSec = new Tabla();
        Tabla tDoc = new Tabla();
        tSec.setMensajeVacio("Sin secciones.");
        tDoc.setMensajeVacio("Sin docentes asignados.");
        JComboBox<Opcion> cSec = UI.combo();
        JComboBox<Opcion> cDoc = UI.combo();
        JComboBox<String> cRol = UI.combo();
        if ("Clase".equals(natur)) {
            cRol.addItem("Dicta clase");
        } else if ("Receso".equals(natur)) {
            cRol.addItem("Cuida receso");
            cRol.addItem("Refrigera con estudiantes");
        } else {
            cRol.addItem("Reunión");
        }
        cSec.setPreferredSize(new Dimension(250, 36));
        cDoc.setPreferredSize(new Dimension(250, 36));
        cRol.setPreferredSize(new Dimension(200, 36));
        ejecutar(() -> {
            Resultado s = GradoSeccionDAO.listarSecciones(null);
            for (int i = 0; i < s.filas(); i++) {
                cSec.addItem(new Opcion(s.numero(i, 0), s.texto(i, 1)));
            }
            Resultado dd = "Clase".equals(natur) ? DocenteDAO.especialistasDe(r.texto(f, 5)) : DocenteDAO.listar("", false);
            for (int i = 0; i < dd.filas(); i++) {
                cDoc.addItem(new Opcion(dd.texto(i, 0), dd.texto(i, 1)));
            }
        });
        Runnable recargar = () -> ejecutar(() -> {
            tSec.mostrar(ActividadDAO.secciones(id));
            tDoc.mostrar(ActividadDAO.docentes(id));
        });

        Boton addSec = boton("Agregar", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton delSec = boton("Quitar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        Boton addDoc = boton("Asignar", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton delDoc = boton("Quitar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        addSec.addActionListener(e -> {
            if (ejecutar(() -> ActividadDAO.agregarSeccion(id, ((Number) UI.valor(cSec)).intValue()))) {
                recargar.run();
            }
        });
        delSec.addActionListener(e -> {
            if (tSec.filaSeleccionada() >= 0 && ejecutar(() ->
                    ActividadDAO.quitarSeccion(id, (int) tSec.resultado().numero(tSec.filaSeleccionada(), 0)))) {
                recargar.run();
            }
        });
        addDoc.addActionListener(e -> {
            if (cDoc.getSelectedItem() != null && ejecutar(() ->
                    ActividadDAO.asignarDocente(id, (String) UI.valor(cDoc), (String) cRol.getSelectedItem()))) {
                recargar.run();
            }
        });
        delDoc.addActionListener(e -> {
            if (tDoc.filaSeleccionada() >= 0 && ejecutar(() ->
                    ActividadDAO.quitarDocente(id, tDoc.resultado().texto(tDoc.filaSeleccionada(), 0)))) {
                recargar.run();
            }
        });
        boolean editable = Sesion.puedeEscribir() && !Sesion.periodoCerrado();
        for (Boton b : new Boton[] {addSec, delSec, addDoc, delDoc}) {
            b.setEnabled(editable);
        }

        JPanel cuerpo = new JPanel(new GridLayout(1, 2, 16, 0));
        cuerpo.setOpaque(false);
        cuerpo.add(columna("Secciones que participan", tSec, UI.fila(6, cSec, addSec, delSec)));
        cuerpo.add(columna("Docentes asignados", tDoc, UI.fila(6, cDoc, cRol, addDoc, delDoc)));
        raiz.add(cuerpo, BorderLayout.CENTER);
        Boton cerrar = Boton.secundario("Cerrar");
        cerrar.addActionListener(e -> d.dispose());
        raiz.add(UI.filaDerecha(0, cerrar), BorderLayout.SOUTH);
        recargar.run();
        d.setContentPane(raiz);
        d.setSize(1180, 520);
        d.setLocationRelativeTo(ventana);
        d.setVisible(true);
        cargarTabla();
    }

    private JPanel columna(String titulo, Tabla t, JPanel controles) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(UI.titulo(titulo, 14f), BorderLayout.NORTH);
        javax.swing.JScrollPane sp = t.enScroll();
        sp.setBorder(new UI.BordeRedondeado(Tema.BORDE, false));
        p.add(sp, BorderLayout.CENTER);
        p.add(controles, BorderLayout.SOUTH);
        return p;
    }
}
