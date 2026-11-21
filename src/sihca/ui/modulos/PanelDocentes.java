package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import sihca.dao.AsignaturaDAO;
import sihca.dao.DocenteDAO;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/** Módulo Docentes: registro, modificación, baja lógica y especialidades. */
public class PanelDocentes extends Modulo {

    private static final String[] MODALIDADES = {"Jornada completa", "Por horas"};
    private static final String[] CONDICIONES = {"Docente", "Tutor", "Miembro del Consejo Directivo",
        "Hermana de la Congregación"};

    private final Tabla tabla = new Tabla();
    private final JTextField buscar = UI.campo(22);
    private final JCheckBox bajas = UI.casilla("Mostrar dados de baja");

    public PanelDocentes(VentanaPrincipal v) {
        super(v);
        buscar.setToolTipText("Buscar por apellidos, nombres o DNI");
        buscar.setPreferredSize(new Dimension(260, 36));
        buscar.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { refrescar(); }
        });
        bajas.addActionListener(e -> refrescar());
        JPanel izq = UI.fila(10, new javax.swing.JLabel(Iconos.de(Iconos.Tipo.BUSCAR, 18, Tema.TEXTO_SUAVE)), buscar, bajas);

        Boton nuevo = botonEscritura("Nuevo docente", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton editar = botonEscritura("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton esp = botonEscritura("Especialidades", Iconos.Tipo.ASIGNATURA, Boton.Estilo.SECUNDARIO);
        Boton reactivar = botonEscritura("Reactivar", Iconos.Tipo.REACTIVAR, Boton.Estilo.SECUNDARIO);
        Boton eliminar = botonEscritura("Eliminar / dar de baja", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        nuevo.addActionListener(e -> formulario(false));
        editar.addActionListener(e -> formulario(true));
        esp.addActionListener(e -> especialidades());
        reactivar.addActionListener(e -> reactivar());
        eliminar.addActionListener(e -> eliminar());
        JPanel arriba = new JPanel(new java.awt.GridLayout(2, 1, 0, 12));
        arriba.setOpaque(false);
        arriba.add(izq);
        arriba.add(UI.filaDerecha(8, esp, reactivar, eliminar, editar, nuevo));
        add(arriba, BorderLayout.NORTH);

        Tarjeta t = new Tarjeta(new BorderLayout());
        t.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(tabla.enScroll(), BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);
        tabla.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && Sesion.puedeEscribir()) {
                    formulario(true);
                }
            }
        });
    }

    @Override public String titulo() { return "Docentes"; }
    @Override public String descripcion() { return "Personal docente, modalidad de contratación, condición especial y especialidades"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.DOCENTE; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            tabla.mostrar(DocenteDAO.listar(buscar.getText().trim(), bajas.isSelected()));
            ventana.estado(tabla.getRowCount() + " docentes  ·  sp_docente_listar");
        });
    }

    private void formulario(boolean edicion) {
        if (edicion && !haySeleccion(tabla, "un docente")) {
            return;
        }
        JTextField dni = UI.campo(10);
        JTextField nombre = UI.campo(30);
        JComboBox<String> modalidad = UI.combo(MODALIDADES);
        JComboBox<String> condicion = UI.combo(CONDICIONES);
        if (edicion) {
            int f = tabla.filaSeleccionada();
            Resultado r = tabla.resultado();
            dni.setText(r.texto(f, 0));
            dni.setEditable(false);
            dni.setBackground(Tema.FONDO);
            nombre.setText(r.texto(f, 1));
            modalidad.setSelectedItem(r.texto(f, 2));
            condicion.setSelectedItem(r.texto(f, 3));
        }
        boolean ok = new Formulario(this, edicion ? "Modificar docente" : "Nuevo docente",
                "La condición especial determina prioridades de horario (Consejo Directivo: lunes; Hermanas: Pastoral).",
                edicion ? "Guardar cambios" : "Registrar docente")
                .campo("DNI", dni, edicion ? "El DNI es la clave del registro y no se modifica." : "8 dígitos numéricos")
                .campo("Apellidos y nombres", nombre, "Formato sugerido: Apellidos, Nombres")
                .campo("Modalidad de contratación", modalidad)
                .campo("Condición especial", condicion)
                .mostrar(() -> {
                    if (edicion) {
                        DocenteDAO.actualizar(dni.getText().trim(), nombre.getText(), (String) modalidad.getSelectedItem(),
                                (String) condicion.getSelectedItem());
                    } else {
                        DocenteDAO.insertar(dni.getText().trim(), nombre.getText(), (String) modalidad.getSelectedItem(),
                                (String) condicion.getSelectedItem());
                    }
                });
        if (ok) {
            refrescar();
            ventana.estado(edicion ? "Docente actualizado (sp_docente_actualizar)." : "Docente registrado (sp_docente_insertar).");
        }
    }

    private void eliminar() {
        if (!haySeleccion(tabla, "un docente")) {
            return;
        }
        int f = tabla.filaSeleccionada();
        String dni = tabla.resultado().texto(f, 0);
        String nom = tabla.resultado().texto(f, 1);
        if (Mensajes.confirmar(this, "Eliminar docente",
                "¿Desea eliminar a " + nom + "?\n\nSi el docente tiene historial de carga horaria se dará de baja "
                        + "(borrado lógico) para conservar los horarios de años anteriores; si no tiene historial "
                        + "se eliminará definitivamente.", "Eliminar")) {
            ejecutar(() -> {
                String res = DocenteDAO.eliminar(dni);
                refrescar();
                Mensajes.exito(this, "Operación realizada", res);
            });
        }
    }

    private void reactivar() {
        if (!haySeleccion(tabla, "un docente")) {
            return;
        }
        String dni = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
        if (ejecutar(() -> DocenteDAO.reactivar(dni))) {
            refrescar();
            ventana.estado("Docente reactivado (sp_docente_reactivar).");
        }
    }

    private void especialidades() {
        if (!haySeleccion(tabla, "un docente")) {
            return;
        }
        int f = tabla.filaSeleccionada();
        String dni = tabla.resultado().texto(f, 0);
        String nom = tabla.resultado().texto(f, 1);
        JDialog d = new JDialog(ventana, "Especialidades de " + nom, JDialog.ModalityType.APPLICATION_MODAL);
        JPanel raiz = new JPanel(new BorderLayout(0, 12));
        raiz.setBackground(Tema.SUPERFICIE);
        raiz.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 22, 18, 22));
        JPanel cab = new JPanel(new BorderLayout(0, 4));
        cab.setOpaque(false);
        cab.add(UI.titulo("Especialidades", 17f), BorderLayout.NORTH);
        cab.add(UI.texto(nom + "  ·  DNI " + dni + "  —  solo puede dictar las asignaturas de su especialidad.",
                13f, Tema.TEXTO_SUAVE), BorderLayout.CENTER);
        raiz.add(cab, BorderLayout.NORTH);
        Tabla te = new Tabla();
        te.setMensajeVacio("El docente aún no tiene especialidades.");
        javax.swing.JScrollPane sp = te.enScroll();
        sp.setPreferredSize(new Dimension(560, 220));
        sp.setBorder(new UI.BordeRedondeado(Tema.BORDE, false));
        raiz.add(sp, BorderLayout.CENTER);
        JComboBox<String> materias = UI.combo();
        materias.setPreferredSize(new Dimension(280, 36));
        Boton agregar = boton("Agregar", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton quitar = boton("Quitar seleccionada", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        Runnable recargar = () -> ejecutar(() -> te.mostrar(DocenteDAO.especialidades(dni)));
        ejecutar(() -> {
            Resultado r = AsignaturaDAO.listar(false);
            for (int i = 0; i < r.filas(); i++) {
                materias.addItem(r.texto(i, 0));
            }
        });
        agregar.addActionListener(e -> {
            if (ejecutar(() -> DocenteDAO.asignarEspecialidad(dni, (String) materias.getSelectedItem()))) {
                recargar.run();
            }
        });
        quitar.addActionListener(e -> {
            if (te.filaSeleccionada() >= 0 && ejecutar(() -> DocenteDAO.quitarEspecialidad(dni,
                    te.resultado().texto(te.filaSeleccionada(), 0)))) {
                recargar.run();
            }
        });
        Boton cerrar = Boton.secundario("Cerrar");
        cerrar.addActionListener(e -> d.dispose());
        JPanel pie = new JPanel(new BorderLayout());
        pie.setOpaque(false);
        pie.add(UI.fila(8, materias, agregar, quitar), BorderLayout.WEST);
        pie.add(UI.filaDerecha(0, cerrar), BorderLayout.EAST);
        raiz.add(pie, BorderLayout.SOUTH);
        recargar.run();
        d.setContentPane(raiz);
        d.pack();
        d.setLocationRelativeTo(ventana);
        d.setVisible(true);
        refrescar();
    }
}
