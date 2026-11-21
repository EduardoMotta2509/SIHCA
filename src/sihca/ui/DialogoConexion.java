package sihca.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import sihca.datos.ConfigConexion;
import sihca.datos.Conexion;
import sihca.datos.Errores;
import sihca.datos.InstaladorBD;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/**
 * Configuración de la conexión con MySQL e instalación de la base de datos.
 * Se abre automáticamente la primera vez o cuando no es posible conectarse.
 */
public final class DialogoConexion extends JDialog {

    private final JTextField host = UI.campo(18);
    private final JSpinner puerto = UI.numero(3306, 1, 65535);
    private final JTextField usuario = UI.campo(18);
    private final JPasswordField clave = UI.campoClave(18);
    private final JTextField base = UI.campo(18);
    private final JLabel estado = new JLabel(" ");
    private final JProgressBar progreso = new JProgressBar();
    private final Boton instalar;
    private final Boton guardar;
    private final Boton probar;
    private boolean conectado;

    private DialogoConexion(Window padre, String problema) {
        super(padre, "Configurar conexión a MySQL", ModalityType.APPLICATION_MODAL);
        setIconImages(Aplicacion.iconosVentana());
        ConfigConexion c = Conexion.config() != null ? Conexion.config() : ConfigConexion.cargar();
        host.setText(c.getHost());
        puerto.setValue(c.getPuerto());
        usuario.setText(c.getUsuario());
        clave.setText(c.getClave());
        base.setText(c.getBaseDatos());

        JPanel raiz = new JPanel(new BorderLayout(0, 16));
        raiz.setBackground(Tema.SUPERFICIE);
        raiz.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));

        JPanel cab = new JPanel(new BorderLayout(14, 4));
        cab.setOpaque(false);
        cab.add(new JLabel(Iconos.de(Iconos.Tipo.BD, 34, Tema.PRIMARIO)), BorderLayout.WEST);
        JPanel tt = new JPanel(new BorderLayout(0, 3));
        tt.setOpaque(false);
        tt.add(UI.titulo("Conexión con el servidor MySQL", 18f), BorderLayout.NORTH);
        tt.add(UI.texto("<html><div style='width:420px'>Ingrese los datos de su servidor MySQL 8.0. "
                + "Si la base de datos aún no existe, use <b>Instalar base de datos</b>: se crearán las tablas, "
                + "triggers, procedimientos y los datos de prueba.</div></html>", 13f, Tema.TEXTO_SUAVE), BorderLayout.CENTER);
        cab.add(tt, BorderLayout.CENTER);
        raiz.add(cab, BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.setOpaque(false);
        if (problema != null) {
            Tarjeta aviso = new Tarjeta(new BorderLayout(10, 0)).colores(Tema.ADVERTENCIA_SUAVE, null);
            aviso.add(new JLabel(Iconos.de(Iconos.Tipo.ALERTA, 20, Tema.ADVERTENCIA)), BorderLayout.WEST);
            aviso.add(UI.texto("<html><div style='width:420px'>" + problema + "</div></html>", 13f, Tema.ADVERTENCIA),
                    BorderLayout.CENTER);
            centro.add(aviso, BorderLayout.NORTH);
        }
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        agregar(form, 0, "Servidor (host)", host, "Puerto", puerto);
        agregar(form, 2, "Usuario de MySQL", usuario, "Contraseña de MySQL", clave);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 4;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(10, 0, 4, 0);
        form.add(UI.etiqueta("Nombre de la base de datos"), g);
        g.gridy = 5;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 0, 12);
        form.add(base, g);
        centro.add(form, BorderLayout.CENTER);

        JPanel est = new JPanel(new BorderLayout(0, 6));
        est.setOpaque(false);
        estado.setFont(Tema.medio(13f));
        progreso.setStringPainted(true);
        progreso.setVisible(false);
        progreso.setPreferredSize(new Dimension(100, 20));
        est.add(estado, BorderLayout.NORTH);
        est.add(progreso, BorderLayout.SOUTH);
        centro.add(est, BorderLayout.SOUTH);
        raiz.add(centro, BorderLayout.CENTER);

        probar = new Boton("Probar conexión", Iconos.de(Iconos.Tipo.OK, 16, Tema.TEXTO), Boton.Estilo.SECUNDARIO);
        instalar = new Boton("Instalar base de datos", Iconos.de(Iconos.Tipo.BD, 16, Tema.TEXTO), Boton.Estilo.SECUNDARIO);
        guardar = Boton.primario("Guardar y continuar");
        Boton cancelar = Boton.fantasma("Cancelar");
        probar.addActionListener(e -> probar());
        instalar.addActionListener(e -> instalar());
        guardar.addActionListener(e -> guardar());
        cancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new BorderLayout());
        botones.setOpaque(false);
        botones.add(UI.fila(8, probar, instalar), BorderLayout.WEST);
        botones.add(UI.filaDerecha(8, cancelar, guardar), BorderLayout.EAST);
        raiz.add(botones, BorderLayout.SOUTH);

        setContentPane(raiz);
        getRootPane().setDefaultButton(guardar);
        pack();
        setResizable(false);
        setLocationRelativeTo(padre);
    }

    private void agregar(JPanel p, int fila, String e1, Component c1, String e2, Component c2) {
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.gridy = fila;
        g.gridx = 0;
        g.insets = new Insets(10, 0, 4, 12);
        p.add(UI.etiqueta(e1), g);
        g.gridx = 1;
        p.add(UI.etiqueta(e2), g);
        g.gridy = fila + 1;
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(0, 0, 0, 12);
        c1.setPreferredSize(new Dimension(250, 36));
        p.add(c1, g);
        g.gridx = 1;
        c2.setPreferredSize(new Dimension(200, 36));
        p.add(c2, g);
    }

    private ConfigConexion leer() {
        ConfigConexion c = new ConfigConexion();
        c.setHost(host.getText().trim().isEmpty() ? "localhost" : host.getText().trim());
        c.setPuerto((Integer) puerto.getValue());
        c.setUsuario(usuario.getText().trim());
        c.setClave(new String(clave.getPassword()));
        c.setBaseDatos(base.getText().trim().isEmpty() ? "bd_horarios_esclavas" : base.getText().trim());
        return c;
    }

    private void mostrarEstado(boolean ok, String texto) {
        estado.setForeground(ok ? Tema.EXITO : Tema.PELIGRO);
        estado.setIcon(Iconos.de(ok ? Iconos.Tipo.OK : Iconos.Tipo.ALERTA, 16, ok ? Tema.EXITO : Tema.PELIGRO));
        estado.setText("<html><div style='width:440px'>" + texto + "</div></html>");
        pack();
    }

    private boolean probar() {
        ConfigConexion c = leer();
        try {
            String v = Conexion.probar(c, false);
            try {
                Conexion.probar(c, true);
                mostrarEstado(true, "Conexión correcta con MySQL " + v + ". La base de datos " + c.getBaseDatos()
                        + " está disponible.");
                return true;
            } catch (SQLException ex) {
                mostrarEstado(false, "El servidor MySQL " + v + " responde, pero la base de datos "
                        + c.getBaseDatos() + " no existe. Presione «Instalar base de datos».");
                return false;
            }
        } catch (SQLException ex) {
            mostrarEstado(false, Errores.mensaje(ex));
            return false;
        }
    }

    private void guardar() {
        ConfigConexion c = leer();
        try {
            Conexion.probar(c, true);
            c.guardar();
            Conexion.configurar(c);
            conectado = true;
            dispose();
        } catch (SQLException ex) {
            mostrarEstado(false, Errores.mensaje(ex));
        } catch (java.io.IOException ex) {
            mostrarEstado(false, "No se pudo guardar config/conexion.properties: " + ex.getMessage());
        }
    }

    private void instalar() {
        ConfigConexion c = leer();
        try {
            Conexion.probar(c, false);
        } catch (SQLException ex) {
            mostrarEstado(false, Errores.mensaje(ex));
            return;
        }
        if (!Mensajes.confirmar(this, "Instalar base de datos",
                "Se creará (o reemplazará) la base de datos «" + c.getBaseDatos() + "» con su estructura, "
                        + "triggers, procedimientos y datos de prueba.\n\nSi ya existe, sus datos actuales se perderán. "
                        + "¿Desea continuar?", "Instalar")) {
            return;
        }
        instalar.setEnabled(false);
        guardar.setEnabled(false);
        probar.setEnabled(false);
        progreso.setVisible(true);
        progreso.setValue(0);
        estado.setForeground(Tema.TEXTO_SUAVE);
        estado.setIcon(null);
        estado.setText("Instalando...");
        pack();
        new SwingWorker<Void, int[]>() {
            private String paso = "";

            @Override
            protected Void doInBackground() throws Exception {
                InstaladorBD.instalar(c, (actual, total, desc) -> {
                    paso = desc;
                    publish(new int[] {actual, total});
                });
                return null;
            }

            @Override
            protected void process(java.util.List<int[]> l) {
                int[] u = l.get(l.size() - 1);
                progreso.setMaximum(u[1]);
                progreso.setValue(u[0]);
                progreso.setString("Sentencia " + u[0] + " de " + u[1] + "  ·  " + paso);
            }

            @Override
            protected void done() {
                instalar.setEnabled(true);
                guardar.setEnabled(true);
                probar.setEnabled(true);
                try {
                    get();
                    progreso.setValue(progreso.getMaximum());
                    progreso.setString("Instalación completa");
                    try {
                        c.guardar();
                    } catch (java.io.IOException ex) {
                        // se informará al guardar
                    }
                    Conexion.configurar(c);
                    mostrarEstado(true, "Base de datos instalada con datos de prueba. Presione «Guardar y continuar».");
                } catch (Exception ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    mostrarEstado(false, "La instalación falló: " + Errores.mensaje(causa));
                }
            }
        }.execute();
    }

    /** Muestra el diálogo. Devuelve true si quedó una conexión válida guardada. */
    public static boolean mostrar(Component padre, String problema) {
        Window w = padre == null ? null : (padre instanceof Window ? (Window) padre : SwingUtilities.getWindowAncestor(padre));
        DialogoConexion d = new DialogoConexion(w, problema);
        d.setVisible(true);
        return d.conectado;
    }
}
