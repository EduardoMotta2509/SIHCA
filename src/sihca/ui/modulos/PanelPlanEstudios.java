package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import sihca.dao.AsignaturaDAO;
import sihca.dao.GradoSeccionDAO;
import sihca.dao.PlanEstudiosDAO;
import sihca.datos.Resultado;
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

/** Módulo Plan de estudios: horas pedagógicas semanales por asignatura y grado. */
public class PanelPlanEstudios extends Modulo {

    private final JComboBox<Opcion> grado = UI.combo();
    private final Tabla tabla = new Tabla();
    private final JLabel total = UI.titulo("", 14f);
    private boolean cargando;

    public PanelPlanEstudios(VentanaPrincipal v) {
        super(v);
        grado.setPreferredSize(new Dimension(280, 36));
        grado.addActionListener(e -> {
            if (!cargando) {
                cargarPlan();
            }
        });
        Boton agregar = botonEscritura("Agregar asignatura", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton editar = botonEscritura("Modificar horas", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton quitar = botonEscritura("Quitar del plan", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        agregar.addActionListener(e -> formulario(false));
        editar.addActionListener(e -> formulario(true));
        quitar.addActionListener(e -> quitar());
        add(barra(UI.fila(10, UI.etiqueta("Grado"), grado), UI.filaDerecha(8, quitar, editar, agregar)), BorderLayout.NORTH);

        Tarjeta t = new Tarjeta(new BorderLayout(0, 0));
        t.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(tabla.enScroll(), BorderLayout.CENTER);
        JPanel pie = new JPanel(new BorderLayout());
        pie.setOpaque(false);
        pie.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 14, 10, 14));
        pie.add(total, BorderLayout.WEST);
        pie.add(UI.texto("La suma no puede superar el tope del nivel (validado por trigger).", 12.5f, Tema.TEXTO_TENUE),
                BorderLayout.EAST);
        t.add(pie, BorderLayout.SOUTH);
        add(t, BorderLayout.CENTER);
    }

    @Override public String titulo() { return "Plan de estudios"; }
    @Override public String descripcion() { return "Horas pedagógicas semanales que exige cada asignatura en cada grado"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.PLAN; }

    @Override
    public void refrescar() {
        Object sel = UI.valor(grado);
        cargando = true;
        ejecutar(() -> {
            grado.removeAllItems();
            Resultado r = GradoSeccionDAO.listarGrados();
            for (int i = 0; i < r.filas(); i++) {
                grado.addItem(new Opcion(r.numero(i, 0), r.texto(i, 1) + " - " + r.texto(i, 2)
                        + "   (tope " + r.texto(i, 5) + " h)"));
            }
        });
        cargando = false;
        if (sel != null) {
            UI.seleccionar(grado, sel);
        } else {
            UI.seleccionar(grado, 21);
        }
        cargarPlan();
    }

    private void cargarPlan() {
        Object g = UI.valor(grado);
        if (g == null) {
            return;
        }
        ejecutar(() -> {
            tabla.mostrar(PlanEstudiosDAO.listar(((Number) g).intValue()));
            long suma = 0;
            for (int i = 0; i < tabla.resultado().filas(); i++) {
                suma += tabla.resultado().numero(i, 2);
            }
            String t = grado.getSelectedItem().toString();
            String tope = t.substring(t.indexOf("tope ") + 5, t.indexOf(" h)"));
            total.setText("Total del plan: " + suma + " de " + tope + " horas semanales");
            total.setForeground(String.valueOf(suma).equals(tope) ? Tema.EXITO : Tema.ADVERTENCIA);
            ventana.estado(tabla.getRowCount() + " asignaturas en el plan  ·  sp_plan_listar");
        });
    }

    private void formulario(boolean edicion) {
        Object g = UI.valor(grado);
        if (g == null || (edicion && !haySeleccion(tabla, "una asignatura del plan"))) {
            return;
        }
        int idGrado = ((Number) g).intValue();
        JComboBox<String> materia = UI.combo();
        JSpinner horas = UI.numero(2, 1, 45);
        if (edicion) {
            String m = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
            materia.addItem(m);
            materia.setEnabled(false);
            horas.setValue((int) tabla.resultado().numero(tabla.filaSeleccionada(), 2));
        } else {
            ejecutar(() -> {
                Resultado r = AsignaturaDAO.listar(false);
                for (int i = 0; i < r.filas(); i++) {
                    materia.addItem(r.texto(i, 0));
                }
            });
        }
        if (new Formulario(this, edicion ? "Modificar horas del plan" : "Agregar asignatura al plan",
                grado.getSelectedItem().toString(), edicion ? "Guardar cambios" : "Agregar al plan")
                .campo("Asignatura", materia)
                .campo("Horas pedagógicas semanales", horas, "Bloques de 45 minutos por semana")
                .mostrar(() -> {
                    if (edicion) {
                        PlanEstudiosDAO.actualizar((String) materia.getSelectedItem(), idGrado, (Integer) horas.getValue());
                    } else {
                        PlanEstudiosDAO.insertar((String) materia.getSelectedItem(), idGrado, (Integer) horas.getValue());
                    }
                })) {
            cargarPlan();
        }
    }

    private void quitar() {
        Object g = UI.valor(grado);
        if (g == null || !haySeleccion(tabla, "una asignatura del plan")) {
            return;
        }
        String m = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
        if (Mensajes.confirmar(this, "Quitar del plan", "¿Desea quitar «" + m + "» del plan de estudios de este grado?",
                "Quitar")) {
            if (ejecutar(() -> PlanEstudiosDAO.eliminar(m, ((Number) g).intValue()))) {
                cargarPlan();
            }
        }
    }
}
