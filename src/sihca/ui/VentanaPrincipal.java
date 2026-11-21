package sihca.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import sihca.Main;
import sihca.dao.PeriodoDAO;
import sihca.datos.Conexion;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Opcion;
import sihca.ui.componentes.UI;
import sihca.ui.modulos.Modulo;
import sihca.ui.modulos.PanelActividades;
import sihca.ui.modulos.PanelAsignaturas;
import sihca.ui.modulos.PanelBitacora;
import sihca.ui.modulos.PanelDocentes;
import sihca.ui.modulos.PanelEspacios;
import sihca.ui.modulos.PanelGradosSecciones;
import sihca.ui.modulos.PanelInicio;
import sihca.ui.modulos.PanelMatriz;
import sihca.ui.modulos.PanelPeriodos;
import sihca.ui.modulos.PanelPlanEstudios;
import sihca.ui.modulos.PanelReportes;
import sihca.ui.modulos.PanelUsuarios;

/**
 * Ventana principal: menú lateral de módulos, encabezado con el periodo de
 * trabajo y área de contenido.
 */
public class VentanaPrincipal extends JFrame {

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final Map<String, Modulo> modulos = new LinkedHashMap<>();
    private final Map<String, ItemMenu> items = new LinkedHashMap<>();
    private final JLabel titulo = UI.titulo("", 22f);
    private final JLabel subtitulo = UI.texto("", 13.5f, Tema.TEXTO_SUAVE);
    private final JComboBox<Opcion> periodo = UI.combo();
    private final JLabel avisoPeriodo = new JLabel();
    private final JLabel barraEstado = new JLabel();
    private Modulo actual;
    private boolean cargandoPeriodos;

    public VentanaPrincipal() {
        super(Main.NOMBRE + " - " + Main.NOMBRE_LARGO);
        setIconImages(Aplicacion.iconosVentana());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                salir();
            }
        });

        cargarPeriodos();

        registrar(new PanelInicio(this), "General");
        registrar(new PanelDocentes(this), "Catálogos");
        registrar(new PanelAsignaturas(this), null);
        registrar(new PanelEspacios(this), null);
        registrar(new PanelGradosSecciones(this), null);
        registrar(new PanelPlanEstudios(this), null);
        registrar(new PanelPeriodos(this), null);
        registrar(new PanelActividades(this), "Horarios");
        registrar(new PanelMatriz(this), null);
        registrar(new PanelReportes(this), "Consultas");
        registrar(new PanelBitacora(this), null);
        if (Sesion.esAdministrador()) {
            registrar(new PanelUsuarios(this), "Administración");
        }

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(menuLateral(), BorderLayout.WEST);
        JPanel derecha = new JPanel(new BorderLayout());
        derecha.setBackground(Tema.FONDO);
        derecha.add(encabezado(), BorderLayout.NORTH);
        contenido.setOpaque(false);
        contenido.setBorder(BorderFactory.createEmptyBorder(0, 28, 18, 28));
        derecha.add(contenido, BorderLayout.CENTER);
        derecha.add(pie(), BorderLayout.SOUTH);
        raiz.add(derecha, BorderLayout.CENTER);
        setContentPane(raiz);

        setSize(1366, 800);
        setMinimumSize(new Dimension(1180, 700));
        setLocationRelativeTo(null);
        mostrar(PanelInicio.class.getSimpleName());
    }

    // ------------------------------------------------------------------ módulos
    private final List<Object[]> grupos = new ArrayList<>();

    private void registrar(Modulo m, String grupo) {
        String clave = m.getClass().getSimpleName();
        modulos.put(clave, m);
        contenido.add(m, clave);
        grupos.add(new Object[] {grupo, clave});
    }

    /** Muestra un módulo por el nombre de su clase (p. ej. "PanelDocentes"). */
    public void mostrar(String clave) {
        Modulo m = modulos.get(clave);
        if (m == null) {
            return;
        }
        actual = m;
        titulo.setText(m.titulo());
        subtitulo.setText(m.descripcion());
        for (Map.Entry<String, ItemMenu> e : items.entrySet()) {
            e.getValue().setSeleccionado(e.getKey().equals(clave));
        }
        tarjetas.show(contenido, clave);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            m.refrescar();
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    public Modulo modulo(String clave) {
        return modulos.get(clave);
    }

    public void estado(String texto) {
        barraEstado.setText(texto);
    }

    // ------------------------------------------------------------------ periodo de trabajo
    /** Recarga la lista de periodos (después de crear, cerrar o eliminar uno). */
    public void cargarPeriodos() {
        cargandoPeriodos = true;
        try {
            short anterior = Sesion.periodo();
            periodo.removeAllItems();
            Resultado r = PeriodoDAO.listar();
            Opcion elegir = null;
            for (int i = 0; i < r.filas(); i++) {
                long anio = r.numero(i, 0);
                String est = r.texto(i, 1);
                Opcion o = new Opcion(anio + "|" + est, anio + "  ·  " + est);
                periodo.addItem(o);
                if (anio == anterior) {
                    elegir = o;
                }
                if (elegir == null && anterior == 0 && "Activo".equals(est) && r.numero(i, 2) > 0) {
                    elegir = o;
                }
            }
            if (elegir == null && periodo.getItemCount() > 0) {
                elegir = periodo.getItemAt(0);
            }
            if (elegir != null) {
                periodo.setSelectedItem(elegir);
            }
        } catch (SQLException ex) {
            Mensajes.error(this, ex);
        } finally {
            cargandoPeriodos = false;
        }
        aplicarPeriodo();
    }

    private void aplicarPeriodo() {
        Object v = UI.valor(periodo);
        if (v == null) {
            Sesion.setPeriodo((short) 0, false);
        } else {
            String[] p = v.toString().split("\\|");
            Sesion.setPeriodo(Short.parseShort(p[0]), "Cerrado".equals(p[1]));
        }
        boolean cerrado = Sesion.periodoCerrado();
        avisoPeriodo.setVisible(cerrado);
    }

    // ------------------------------------------------------------------ construcción de la interfaz
    private JPanel encabezado() {
        JPanel p = new JPanel(new BorderLayout(20, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(22, 28, 16, 28));
        JPanel t = new JPanel(new BorderLayout(0, 3));
        t.setOpaque(false);
        t.add(titulo, BorderLayout.NORTH);
        t.add(subtitulo, BorderLayout.CENTER);
        p.add(t, BorderLayout.CENTER);

        JPanel der = new JPanel();
        der.setOpaque(false);
        der.setLayout(new BoxLayout(der, BoxLayout.X_AXIS));
        avisoPeriodo.setIcon(Iconos.de(Iconos.Tipo.CANDADO, 15, Tema.ADVERTENCIA));
        avisoPeriodo.setText("Periodo cerrado: solo lectura");
        avisoPeriodo.setFont(Tema.medio(12.5f));
        avisoPeriodo.setForeground(Tema.ADVERTENCIA);
        avisoPeriodo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        avisoPeriodo.setVisible(Sesion.periodoCerrado());
        der.add(avisoPeriodo);
        der.add(Box.createHorizontalStrut(12));
        JLabel lp = UI.etiqueta("Periodo de trabajo");
        der.add(lp);
        der.add(Box.createHorizontalStrut(8));
        periodo.setPreferredSize(new Dimension(170, 36));
        periodo.setMaximumSize(new Dimension(170, 36));
        periodo.setToolTipText("Año lectivo sobre el que trabajan Actividades, Matriz, Reportes e Inicio");
        periodo.addActionListener(e -> {
            if (!cargandoPeriodos) {
                aplicarPeriodo();
                if (actual != null && actual.dependeDelPeriodo()) {
                    actual.refrescar();
                }
            }
        });
        der.add(periodo);
        p.add(der, BorderLayout.EAST);
        return p;
    }

    private JPanel pie() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Tema.SUPERFICIE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(6, 28, 6, 28)));
        JLabel con = UI.texto("MySQL " + Conexion.versionServidor() + "  ·  " + Conexion.config().descripcion(),
                12f, Tema.TEXTO_SUAVE);
        con.setIcon(Iconos.de(Iconos.Tipo.BD, 14, Tema.EXITO));
        p.add(con, BorderLayout.WEST);
        barraEstado.setFont(Tema.normal(12f));
        barraEstado.setForeground(Tema.TEXTO_SUAVE);
        barraEstado.setHorizontalAlignment(SwingConstants.RIGHT);
        p.add(barraEstado, BorderLayout.EAST);
        return p;
    }

    private JPanel menuLateral() {
        JPanel lateral = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Tema.AZUL_OSCURO);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        lateral.setPreferredSize(new Dimension(248, 10));

        JPanel marca = new JPanel(new BorderLayout(12, 0));
        marca.setOpaque(false);
        marca.setBorder(BorderFactory.createEmptyBorder(20, 20, 14, 16));
        marca.add(new JLabel(new ImageIcon(Aplicacion.logo(38))), BorderLayout.WEST);
        JPanel mt = new JPanel(new GridLayout(2, 1));
        mt.setOpaque(false);
        JLabel n = new JLabel(Main.NOMBRE);
        n.setFont(Tema.negrita(19f));
        n.setForeground(Color.WHITE);
        JLabel c = new JLabel("Horarios y carga académica");
        c.setFont(Tema.normal(11.5f));
        c.setForeground(new Color(0x9FB0D0));
        mt.add(n);
        mt.add(c);
        marca.add(mt, BorderLayout.CENTER);
        lateral.add(marca, BorderLayout.NORTH);

        JPanel lista = new JPanel();
        lista.setOpaque(false);
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        lista.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));
        for (Object[] g : grupos) {
            String grupo = (String) g[0];
            String clave = (String) g[1];
            if (grupo != null) {
                JLabel lg = new JLabel(grupo.toUpperCase());
                lg.setFont(Tema.semi(10.5f));
                lg.setForeground(new Color(0x6F82A8));
                lg.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 0));
                lg.setAlignmentX(LEFT_ALIGNMENT);
                lista.add(lg);
            }
            Modulo m = modulos.get(clave);
            ItemMenu it = new ItemMenu(m.nombreMenu(), m.icono(), () -> mostrar(clave));
            items.put(clave, it);
            lista.add(it);
        }
        JScrollPane sp = new JScrollPane(lista);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(0x34466E);
                trackColor = Tema.AZUL_OSCURO;
            }
            @Override protected javax.swing.JButton createDecreaseButton(int o) { return sinBoton(); }
            @Override protected javax.swing.JButton createIncreaseButton(int o) { return sinBoton(); }
        });
        lateral.add(sp, BorderLayout.CENTER);

        // Tarjeta de usuario y cierre de sesión
        JPanel usuario = new JPanel(new BorderLayout(10, 8));
        usuario.setOpaque(false);
        usuario.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x2A3A5F)),
                BorderFactory.createEmptyBorder(14, 20, 16, 16)));
        JLabel av = new JLabel(iniciales(Sesion.nombre()), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Tema.DORADO);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        av.setPreferredSize(new Dimension(38, 38));
        av.setFont(Tema.semi(14f));
        av.setForeground(Tema.AZUL_OSCURO);
        usuario.add(av, BorderLayout.WEST);
        JPanel ut = new JPanel(new GridLayout(2, 1));
        ut.setOpaque(false);
        JLabel un = new JLabel(Sesion.nombre());
        un.setFont(Tema.medio(13f));
        un.setForeground(Color.WHITE);
        JLabel ur = new JLabel(Sesion.rol() + "  ·  " + Sesion.usuario());
        ur.setFont(Tema.normal(11.5f));
        ur.setForeground(new Color(0x9FB0D0));
        ut.add(un);
        ut.add(ur);
        usuario.add(ut, BorderLayout.CENTER);
        JPanel acciones = new JPanel(new BorderLayout(4, 0));
        acciones.setOpaque(false);
        ItemMenu clave = new ItemMenu("Clave", Iconos.Tipo.LLAVE, this::cambiarMiClave);
        ItemMenu salir = new ItemMenu("Cerrar sesión", Iconos.Tipo.SALIR, () -> Aplicacion.cerrarSesion(this));
        clave.compacto();
        salir.compacto();
        clave.setPreferredSize(new Dimension(80, 36));
        salir.setToolTipText("Cerrar sesión y volver a la pantalla de ingreso");
        clave.setToolTipText("Cambiar mi contraseña");
        acciones.add(clave, BorderLayout.WEST);
        acciones.add(salir, BorderLayout.CENTER);
        usuario.add(acciones, BorderLayout.SOUTH);
        lateral.add(usuario, BorderLayout.SOUTH);
        return lateral;
    }

    private void cambiarMiClave() {
        javax.swing.JPasswordField n1 = UI.campoClave(20);
        javax.swing.JPasswordField n2 = UI.campoClave(20);
        new sihca.ui.componentes.Formulario(this, "Cambiar mi contraseña",
                "La contraseña se guarda cifrada (SHA-256) en la base de datos.", "Cambiar contraseña")
                .campo("Nueva contraseña", n1, "Mínimo 6 caracteres")
                .campo("Repita la nueva contraseña", n2)
                .mostrar(() -> {
                    String a = new String(n1.getPassword());
                    if (!a.equals(new String(n2.getPassword()))) {
                        throw new IllegalArgumentException("Las contraseñas no coinciden.");
                    }
                    sihca.dao.UsuarioDAO.cambiarClave(Sesion.usuario(), a);
                    estado("Contraseña actualizada.");
                });
    }

    private void salir() {
        if (Mensajes.confirmar(this, "Salir del sistema", "¿Desea cerrar el aplicativo?", "Salir")) {
            try {
                sihca.dao.UsuarioDAO.logout();
            } catch (SQLException ex) {
                // se cierra de todas formas
            }
            Conexion.cerrar();
            dispose();
            System.exit(0);
        }
    }

    private static javax.swing.JButton sinBoton() {
        javax.swing.JButton b = new javax.swing.JButton();
        b.setPreferredSize(new Dimension(0, 0));
        return b;
    }

    private static String iniciales(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return "?";
        }
        String[] p = nombre.replace(",", " ").trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[1].substring(0, 1) : "")).toUpperCase();
    }

    /** Opción del menú lateral. */
    private static final class ItemMenu extends JPanel {
        private final JLabel etiqueta;
        private boolean seleccionado;
        private boolean encima;
        private final Iconos.Tipo icono;

        ItemMenu(String texto, Iconos.Tipo icono, Runnable accion) {
            super(new BorderLayout());
            this.icono = icono;
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(400, 36));
            setPreferredSize(new Dimension(210, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            etiqueta = new JLabel(texto);
            etiqueta.setIconTextGap(12);
            etiqueta.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
            add(etiqueta, BorderLayout.CENTER);
            actualizar();
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { accion.run(); }
                @Override public void mouseEntered(MouseEvent e) { encima = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { encima = false; repaint(); }
            });
        }

        void compacto() {
            etiqueta.setIconTextGap(7);
            etiqueta.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));
        }

        void setSeleccionado(boolean s) {
            seleccionado = s;
            actualizar();
            repaint();
        }

        private void actualizar() {
            Color c = seleccionado ? Color.WHITE : new Color(0xC3CEE4);
            etiqueta.setForeground(c);
            etiqueta.setFont(seleccionado ? Tema.semi(13.5f) : Tema.normal(13.5f));
            etiqueta.setIcon(Iconos.de(icono, 18, seleccionado ? Tema.DORADO : new Color(0x8FA2C7)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (seleccionado || encima) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(seleccionado ? new Color(0x2A3D66) : new Color(0x1E2E50));
                g2.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 10, 10);
                if (seleccionado) {
                    g2.setColor(Tema.DORADO);
                    g2.fillRoundRect(0, 10, 4, getHeight() - 20, 4, 4);
                }
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
