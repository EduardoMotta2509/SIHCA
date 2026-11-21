package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import sihca.dao.GradoSeccionDAO;
import sihca.datos.Resultado;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/** Módulo Grados y secciones: estructura del alumnado (máximo 2 secciones por grado). */
public class PanelGradosSecciones extends Modulo {

    private final Tabla grados = new Tabla();
    private final Tabla secciones = new Tabla();

    public PanelGradosSecciones(VentanaPrincipal v) {
        super(v);
        JPanel cuerpo = new JPanel(new GridLayout(1, 2, 16, 0));
        cuerpo.setOpaque(false);

        Boton gNuevo = botonEscritura("Nuevo", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton gEditar = botonEscritura("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton gEliminar = botonEscritura("Eliminar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        gNuevo.addActionListener(e -> formGrado(false));
        gEditar.addActionListener(e -> formGrado(true));
        gEliminar.addActionListener(e -> eliminarGrado());
        cuerpo.add(panel("Grados escolares", grados, gEliminar, gEditar, gNuevo));

        Boton sNuevo = botonEscritura("Nueva", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton sEditar = botonEscritura("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton sEliminar = botonEscritura("Eliminar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        sNuevo.addActionListener(e -> formSeccion(false));
        sEditar.addActionListener(e -> formSeccion(true));
        sEliminar.addActionListener(e -> eliminarSeccion());
        cuerpo.add(panel("Secciones del grado", secciones, sEliminar, sEditar, sNuevo));
        add(cuerpo, BorderLayout.CENTER);

        grados.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSecciones();
            }
        });
        secciones.setMensajeVacio("Seleccione un grado para ver sus secciones.");
    }

    private JPanel panel(String titulo, Tabla t, Boton... botones) {
        Tarjeta c = new Tarjeta(new BorderLayout(0, 10));
        JPanel cab = new JPanel(new BorderLayout());
        cab.setOpaque(false);
        cab.add(UI.titulo(titulo, 14.5f), BorderLayout.WEST);
        cab.add(UI.filaDerecha(6, botones), BorderLayout.EAST);
        c.add(cab, BorderLayout.NORTH);
        c.add(t.enScroll(), BorderLayout.CENTER);
        return c;
    }

    @Override public String titulo() { return "Grados y secciones"; }
    @Override public String descripcion() { return "Grados de Inicial, Primaria y Secundaria y sus secciones A y B (máx. 35 alumnos)"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.GRADO; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            grados.mostrar(GradoSeccionDAO.listarGrados());
            if (grados.getRowCount() > 0) {
                grados.setRowSelectionInterval(0, 0);
            }
            ventana.estado(grados.getRowCount() + " grados  ·  sp_grado_listar / sp_seccion_listar");
        });
    }

    private Integer gradoSeleccionado() {
        Object v = grados.valorSeleccionado(0);
        return v == null ? null : ((Number) v).intValue();
    }

    private void cargarSecciones() {
        Integer g = gradoSeleccionado();
        if (g != null) {
            ejecutar(() -> {
                secciones.mostrar(GradoSeccionDAO.listarSecciones(g));
                secciones.ocultarColumna("ID grado");
                secciones.ocultarColumna("Descripción");
            });
        }
    }

    private void formGrado(boolean edicion) {
        if (edicion && !haySeleccion(grados, "un grado")) {
            return;
        }
        JSpinner id = UI.numero(1, 1, 999);
        JTextField nombre = UI.campo(20);
        JComboBox<String> nivel = UI.combo("Inicial", "Primaria", "Secundaria");
        if (edicion) {
            Resultado r = grados.resultado();
            int f = grados.filaSeleccionada();
            id.setValue((int) r.numero(f, 0));
            id.setEnabled(false);
            nombre.setText(r.texto(f, 1));
            nivel.setSelectedItem(r.texto(f, 2));
        }
        if (new Formulario(this, edicion ? "Modificar grado" : "Nuevo grado", null,
                edicion ? "Guardar cambios" : "Registrar grado")
                .campo("Código del grado", id, "Número único (Inicial 1-9, Primaria 11-16, Secundaria 21-25)")
                .campo("Nombre", nombre, "Ej.: 1.° Año, 3.° Grado, 5 Años")
                .campo("Nivel educativo", nivel, "Tope semanal: Secundaria 45 h, Primaria 40 h, Inicial 30 h")
                .mostrar(() -> {
                    int i = (Integer) id.getValue();
                    if (edicion) {
                        GradoSeccionDAO.actualizarGrado(i, nombre.getText(), (String) nivel.getSelectedItem());
                    } else {
                        GradoSeccionDAO.insertarGrado(i, nombre.getText(), (String) nivel.getSelectedItem());
                    }
                })) {
            refrescar();
        }
    }

    private void eliminarGrado() {
        if (!haySeleccion(grados, "un grado")) {
            return;
        }
        int id = gradoSeleccionado();
        String n = grados.resultado().texto(grados.filaSeleccionada(), 1);
        if (Mensajes.confirmar(this, "Eliminar grado", "¿Desea eliminar el grado «" + n + "»? También se "
                + "eliminará su plan de estudios.", "Eliminar")) {
            if (ejecutar(() -> GradoSeccionDAO.eliminarGrado(id))) {
                refrescar();
            }
        }
    }

    private void formSeccion(boolean edicion) {
        Integer g = gradoSeleccionado();
        if (g == null) {
            Mensajes.advertencia(this, "Seleccione un grado", "Primero seleccione el grado en la tabla de la izquierda.");
            return;
        }
        if (edicion && !haySeleccion(secciones, "una sección")) {
            return;
        }
        JSpinner id = UI.numero(g * 10 + 1, 1, 99999);
        JComboBox<String> letra = UI.combo("A", "B");
        if (edicion) {
            Resultado r = secciones.resultado();
            int f = secciones.filaSeleccionada();
            id.setValue((int) r.numero(f, 0));
            id.setEnabled(false);
            letra.setSelectedItem(r.texto(f, 4));
        } else if (secciones.getRowCount() == 1) {
            letra.setSelectedItem("B");
            id.setValue(g * 10 + 2);
        }
        String gNombre = grados.resultado().texto(grados.filaSeleccionada(), 1);
        if (new Formulario(this, edicion ? "Modificar sección" : "Nueva sección de " + gNombre,
                "Cada grado admite como máximo dos secciones (A y B) por limitaciones de infraestructura.",
                edicion ? "Guardar cambios" : "Registrar sección")
                .campo("Código de la sección", id, "Sugerido: código del grado + 1 (A) o + 2 (B)")
                .campo("Letra", letra)
                .mostrar(() -> {
                    int i = (Integer) id.getValue();
                    if (edicion) {
                        GradoSeccionDAO.actualizarSeccion(i, (String) letra.getSelectedItem(), g);
                    } else {
                        GradoSeccionDAO.insertarSeccion(i, (String) letra.getSelectedItem(), g);
                    }
                })) {
            int fila = grados.getSelectedRow();
            refrescar();
            if (fila >= 0 && fila < grados.getRowCount()) {
                grados.setRowSelectionInterval(fila, fila);
            }
        }
    }

    private void eliminarSeccion() {
        if (!haySeleccion(secciones, "una sección")) {
            return;
        }
        int id = (int) secciones.resultado().numero(secciones.filaSeleccionada(), 0);
        if (Mensajes.confirmar(this, "Eliminar sección", "¿Desea eliminar la sección " + id + "?\n\nSi ya tiene "
                + "horario registrado no se podrá eliminar (forma parte del histórico).", "Eliminar")) {
            if (ejecutar(() -> GradoSeccionDAO.eliminarSeccion(id))) {
                cargarSecciones();
            }
        }
    }
}
