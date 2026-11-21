package sihca.ui.modulos;

import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import sihca.dao.EspacioDAO;
import sihca.datos.Resultado;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/** Módulo Espacios: infraestructura física y límite de grupos simultáneos (aforo). */
public class PanelEspacios extends Modulo {

    private static final String[] CATEGORIAS = {"Aula", "Cancha", "Ambiente de música", "Centro de cómputo",
        "Laboratorio", "Patio"};
    private final Tabla tabla = new Tabla();
    private final JCheckBox bajas = UI.casilla("Mostrar dados de baja");

    public PanelEspacios(VentanaPrincipal v) {
        super(v);
        bajas.addActionListener(e -> refrescar());
        Boton nuevo = botonEscritura("Nuevo espacio", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton editar = botonEscritura("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton reactivar = botonEscritura("Reactivar", Iconos.Tipo.REACTIVAR, Boton.Estilo.SECUNDARIO);
        Boton eliminar = botonEscritura("Eliminar / dar de baja", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        nuevo.addActionListener(e -> formulario(false));
        editar.addActionListener(e -> formulario(true));
        reactivar.addActionListener(e -> {
            if (haySeleccion(tabla, "un espacio") && ejecutar(() ->
                    EspacioDAO.reactivar(tabla.resultado().texto(tabla.filaSeleccionada(), 0)))) {
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

    @Override public String titulo() { return "Espacios físicos"; }
    @Override public String descripcion() { return "Aulas, canchas, laboratorios y ambientes con su límite de grupos simultáneos"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.ESPACIO; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            tabla.mostrar(EspacioDAO.listar(bajas.isSelected()));
            ventana.estado(tabla.getRowCount() + " espacios  ·  sp_espacio_listar");
        });
    }

    private void formulario(boolean edicion) {
        if (edicion && !haySeleccion(tabla, "un espacio")) {
            return;
        }
        JTextField nombre = UI.campo(30);
        JComboBox<String> cat = UI.combo(CATEGORIAS);
        JSpinner limite = UI.numero(1, 1, 20);
        String actual = null;
        if (edicion) {
            Resultado r = tabla.resultado();
            int f = tabla.filaSeleccionada();
            actual = r.texto(f, 0);
            nombre.setText(actual);
            cat.setSelectedItem(r.texto(f, 1));
            limite.setValue((int) r.numero(f, 2));
        }
        final String anterior = actual;
        if (new Formulario(this, edicion ? "Modificar espacio" : "Nuevo espacio",
                "El límite de grupos indica cuántas actividades pueden usar el ambiente a la vez "
                        + "(por ejemplo, 2 clases de Educación Física en la cancha).",
                edicion ? "Guardar cambios" : "Registrar espacio")
                .campo("Denominación", nombre, "Ej.: Aula 3A Secundaria, Cancha Sintética Principal")
                .campo("Categoría", cat)
                .campo("Límite de grupos simultáneos", limite)
                .mostrar(() -> {
                    int l = (Integer) limite.getValue();
                    if (edicion) {
                        EspacioDAO.actualizar(anterior, nombre.getText(), (String) cat.getSelectedItem(), l);
                    } else {
                        EspacioDAO.insertar(nombre.getText(), (String) cat.getSelectedItem(), l);
                    }
                })) {
            refrescar();
        }
    }

    private void eliminar() {
        if (!haySeleccion(tabla, "un espacio")) {
            return;
        }
        String n = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
        if (Mensajes.confirmar(this, "Eliminar espacio", "¿Desea eliminar «" + n + "»?\n\nSi tiene historial de "
                + "uso se dará de baja (borrado lógico).", "Eliminar")) {
            ejecutar(() -> {
                String res = EspacioDAO.eliminar(n);
                refrescar();
                Mensajes.exito(this, "Operación realizada", res);
            });
        }
    }
}
