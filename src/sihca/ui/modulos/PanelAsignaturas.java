package sihca.ui.modulos;

import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import sihca.dao.AsignaturaDAO;
import sihca.datos.Resultado;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/** Módulo Asignaturas: áreas curriculares y su nivel de exigencia cognitiva. */
public class PanelAsignaturas extends Modulo {

    private final Tabla tabla = new Tabla();
    private final JCheckBox bajas = UI.casilla("Mostrar dadas de baja");

    public PanelAsignaturas(VentanaPrincipal v) {
        super(v);
        bajas.addActionListener(e -> refrescar());
        Boton nuevo = botonEscritura("Nueva asignatura", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton editar = botonEscritura("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton reactivar = botonEscritura("Reactivar", Iconos.Tipo.REACTIVAR, Boton.Estilo.SECUNDARIO);
        Boton eliminar = botonEscritura("Eliminar / dar de baja", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        nuevo.addActionListener(e -> formulario(false));
        editar.addActionListener(e -> formulario(true));
        reactivar.addActionListener(e -> {
            if (haySeleccion(tabla, "una asignatura") && ejecutar(() ->
                    AsignaturaDAO.reactivar(tabla.resultado().texto(tabla.filaSeleccionada(), 0)))) {
                refrescar();
            }
        });
        eliminar.addActionListener(e -> eliminar());
        add(barra(UI.fila(10, bajas), UI.filaDerecha(8, reactivar, eliminar, editar, nuevo)), BorderLayout.NORTH);
        Tarjeta t = new Tarjeta(new BorderLayout());
        t.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(tabla.enScroll(), BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);
    }

    @Override public String titulo() { return "Asignaturas"; }
    @Override public String descripcion() { return "Áreas curriculares y exigencia cognitiva (las de exigencia Alta se ubican en las primeras horas)"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.ASIGNATURA; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            tabla.mostrar(AsignaturaDAO.listar(bajas.isSelected()));
            ventana.estado(tabla.getRowCount() + " asignaturas  ·  sp_asignatura_listar");
        });
    }

    private void formulario(boolean edicion) {
        if (edicion && !haySeleccion(tabla, "una asignatura")) {
            return;
        }
        JTextField nombre = UI.campo(30);
        JComboBox<String> exig = UI.combo("Alta", "Media", "Baja");
        String actual = null;
        if (edicion) {
            Resultado r = tabla.resultado();
            int f = tabla.filaSeleccionada();
            actual = r.texto(f, 0);
            nombre.setText(actual);
            exig.setSelectedItem(r.texto(f, 1));
        } else {
            exig.setSelectedItem("Media");
        }
        final String anterior = actual;
        if (new Formulario(this, edicion ? "Modificar asignatura" : "Nueva asignatura",
                edicion ? "Si cambia el nombre, el cambio se propaga al plan de estudios, especialidades y horarios "
                        + "del periodo activo (ON UPDATE CASCADE)." : null,
                edicion ? "Guardar cambios" : "Registrar asignatura")
                .campo("Nombre de la asignatura", nombre, "Máximo 60 caracteres")
                .campo("Nivel de exigencia cognitiva", exig, "Alta: Matemática, Comunicación, Ciencia...  Baja: Arte, Tutoría...")
                .mostrar(() -> {
                    if (edicion) {
                        AsignaturaDAO.actualizar(anterior, nombre.getText(), (String) exig.getSelectedItem());
                    } else {
                        AsignaturaDAO.insertar(nombre.getText(), (String) exig.getSelectedItem());
                    }
                })) {
            refrescar();
        }
    }

    private void eliminar() {
        if (!haySeleccion(tabla, "una asignatura")) {
            return;
        }
        String n = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
        if (Mensajes.confirmar(this, "Eliminar asignatura", "¿Desea eliminar «" + n + "»?\n\nSi figura en algún "
                + "plan de estudios u horario se dará de baja (borrado lógico).", "Eliminar")) {
            ejecutar(() -> {
                String res = AsignaturaDAO.eliminar(n);
                refrescar();
                Mensajes.exito(this, "Operación realizada", res);
            });
        }
    }
}
