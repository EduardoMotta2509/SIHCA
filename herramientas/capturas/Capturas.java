import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * Herramienta de desarrollo (NO forma parte del entregable ejecutable):
 * recorre la interfaz real del aplicativo contra la base de datos, ejecuta
 * operaciones y guarda capturas PNG para el manual de usuario.
 */
public class Capturas {

    static File salida;
    static final List<String> log = new ArrayList<>();

    public static void main(String[] a) throws Exception {
        try {
            correr(a);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println(String.join("\n", log));
            System.exit(1);
        }
    }

    static void correr(String[] a) throws Exception {
        salida = new File(a[0]);
        salida.mkdirs();
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeAndWait(() -> {
            sihca.ui.Tema.instalar();
            sihca.datos.Conexion.configurar(sihca.datos.ConfigConexion.cargar());
            sihca.ui.Aplicacion.mostrarLogin();
        });
        esperar(1500);
        Window login = ventana("Iniciar sesión");
        capturar(login, "01_login");

        // Configurar conexión
        clic(login, "Configurar conexión");
        esperar(1500);
        Window con = ventana("Configurar conexión");
        clic(con, "Probar conexión");
        esperar(800);
        capturar(con, "02_conexion");
        cerrar(con);
        esperar(500);

        // Error de login
        escribir(login, 0, "admin");
        escribir(login, 1, "equivocada");
        clic(login, "Ingresar al sistema");
        esperar(800);
        capturar(login, "03_login_error");
        escribir(login, 1, "admin123");
        clic(login, "Ingresar al sistema");
        esperar(3500);
        final Window p = ventana("Sistema de Horarios");
        capturar(p, "04_inicio");

        modulo(p, "PanelDocentes"); esperar(1200); capturar(p, "05_docentes");
        // Nuevo docente (válido)
        clic(p, "Nuevo docente"); esperar(900);
        Window f = dialogo();
        escribir(f, 0, "71234567");
        escribir(f, 1, "Gómez Paredes, Lucía Fernanda");
        capturar(f, "06_docente_nuevo");
        clic(f, "Registrar docente"); esperar(1000);
        // Nuevo docente con DNI repetido -> error dentro del formulario
        clic(p, "Nuevo docente"); esperar(900);
        f = dialogo();
        escribir(f, 0, "45231678");
        escribir(f, 1, "Prueba Repetida, Docente");
        clic(f, "Registrar docente"); esperar(900);
        capturar(f, "07_docente_error");
        cerrar(f); esperar(500);
        // Buscar y seleccionar el nuevo docente
        escribirCampo(p, "Buscar", "Gómez");
        esperar(1000);
        seleccionarFila(p, 0); esperar(300);
        capturar(p, "08_docentes_busqueda");
        clic(p, "Especialidades"); esperar(1200);
        Window esp = dialogo();
        clic(esp, "Agregar"); esperar(800);
        capturar(esp, "09_especialidades");
        cerrar(esp); esperar(1000);
        seleccionarFila(p, 0);
        clic(p, "Eliminar / dar de baja"); esperar(900);
        Window conf = dialogo();
        capturar(conf, "10_confirmar_eliminar");
        clic(conf, "Eliminar"); esperar(1000);
        Window ok = dialogo();
        capturar(ok, "11_resultado_eliminar");
        clic(ok, "Aceptar"); esperar(600);
        escribirCampo(p, "Buscar", "");
        esperar(800);

        // Docente con carga: intento de baja -> rechazo por trigger
        escribirCampo(p, "Buscar", "Paredes");
        esperar(900);
        seleccionarFila(p, 0);
        clic(p, "Eliminar / dar de baja"); esperar(900);
        clic(dialogo(), "Eliminar"); esperar(1000);
        Window err = dialogo();
        capturar(err, "12_error_trigger_baja");
        clic(err, "Entendido"); esperar(500);
        escribirCampo(p, "Buscar", "");
        esperar(800);

        modulo(p, "PanelAsignaturas"); esperar(1000); capturar(p, "13_asignaturas");
        modulo(p, "PanelEspacios"); esperar(1000); capturar(p, "14_espacios");
        modulo(p, "PanelGradosSecciones"); esperar(1000);
        seleccionarFila(p, 9); esperar(800);
        capturar(p, "15_grados_secciones");
        modulo(p, "PanelPlanEstudios"); esperar(1200); capturar(p, "16_plan_estudios");
        clic(p, "Agregar asignatura"); esperar(900);
        Window pl = dialogo();
        clic(pl, "Agregar al plan"); esperar(900);
        capturar(pl, "17_plan_error_tope");
        cerrar(pl); esperar(500);

        modulo(p, "PanelPeriodos"); esperar(1000);
        clic(p, "Nuevo periodo"); esperar(900);
        Window np = dialogo();
        capturar(np, "18_periodo_nuevo");
        clic(np, "Registrar periodo"); esperar(1500);
        clic(p, "Reutilizar horario de otro año"); esperar(900);
        Window rp = dialogo();
        capturar(rp, "19_replicar");
        clic(rp, "Copiar horario"); esperar(6000);
        Window rok = dialogo();
        capturar(rok, "20_replicar_ok");
        clic(rok, "Aceptar"); esperar(800);
        capturar(p, "21_periodos");

        modulo(p, "PanelActividades"); esperar(1500);
        capturar(p, "22_actividades");
        clic(p, "Programar clase"); esperar(1500);
        Window pc = dialogo();
        elegir(pc, 2, "3 Años A"); esperar(300);
        elegir(pc, 3, "Matemática"); esperar(800);
        capturar(pc, "23_programar_clase");
        clic(pc, "Programar clase"); esperar(1500);
        clic(p, "Programar clase"); esperar(1500);
        pc = dialogo();
        elegir(pc, 2, "3 Años A"); esperar(300);
        elegir(pc, 3, "Comunicación"); esperar(800);
        elegir(pc, 4, "Aula 4 Años A"); esperar(300);
        clic(pc, "Programar clase"); esperar(1200);
        capturar(pc, "24_programar_error");
        cerrar(pc); esperar(600);
        seleccionarFila(p, 3); esperar(300);
        clic(p, "Secciones y docentes"); esperar(1500);
        Window part = dialogo();
        capturar(part, "25_participantes");
        cerrar(part); esperar(800);

        modulo(p, "PanelMatriz"); esperar(1800); capturar(p, "26_matriz_seccion");
        Object[] mats = componentes(p, JComboBox.class);
        // Vista por docente
        SwingUtilities.invokeAndWait(() -> {
            for (Object o : mats) {
                JComboBox<?> c = (JComboBox<?>) o;
                if (c.getItemCount() == 2 && "Por docente".equals(c.getItemAt(1))) {
                    c.setSelectedIndex(1);
                }
            }
        });
        esperar(1500);
        SwingUtilities.invokeAndWait(() -> {
            for (Object o : componentes(p, JComboBox.class)) {
                JComboBox<?> c = (JComboBox<?>) o;
                for (int i = 0; i < c.getItemCount(); i++) {
                    if (c.getItemAt(i).toString().startsWith("Paredes")) {
                        c.setSelectedIndex(i);
                    }
                }
            }
        });
        esperar(1500);
        capturar(p, "27_matriz_docente");

        modulo(p, "PanelReportes"); esperar(1500);
        for (int i = 0; i < 13; i++) {
            final int k = i;
            SwingUtilities.invokeAndWait(() -> {
                JList<?> l = (JList<?>) componentes(p, JList.class)[0];
                l.setSelectedIndex(k);
            });
            esperar(1600);
            capturar(p, String.format("R%02d_reporte", i + 1));
        }
        modulo(p, "PanelBitacora"); esperar(1500);
        seleccionarFila(p, 0); esperar(400);
        capturar(p, "28_bitacora");
        modulo(p, "PanelUsuarios"); esperar(1000); capturar(p, "29_usuarios");

        // Periodo cerrado
        SwingUtilities.invokeAndWait(() -> {
            for (Object o : componentes(p, JComboBox.class)) {
                JComboBox<?> c = (JComboBox<?>) o;
                for (int i = 0; i < c.getItemCount(); i++) {
                    if (c.getItemAt(i).toString().startsWith("2025")) {
                        c.setSelectedIndex(i);
                    }
                }
            }
        });
        modulo(p, "PanelActividades"); esperar(1500);
        clic(p, "Programar clase"); esperar(900);
        Window pcerr = dialogo();
        capturar(p, "30_periodo_cerrado");
        capturar(pcerr, "31_periodo_cerrado_msg");
        clic(pcerr, "Entendido"); esperar(500);

        // Cerrar sesión y entrar como Consulta
        clic(p, "Cerrar sesión"); esperar(1500);
        login = ventana("Iniciar sesión");
        escribir(login, 0, "consulta");
        escribir(login, 1, "consulta123");
        clic(login, "Ingresar al sistema");
        esperar(3000);
        Window p2 = ventana("Sistema de Horarios");
        modulo(p2, "PanelDocentes"); esperar(1200); capturar(p2, "32_rol_consulta");
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w instanceof JFrame) {
                    w.dispatchEvent(new java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING));
                }
            }
        });
        esperar(1000);
        capturar(dialogo(), "33_salir");
        System.out.println(String.join("\n", log));
        System.exit(0);
    }

    // ---------------------------------------------------------------- utilidades
    static void esperar(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    static Window ventana(String titulo) {
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w instanceof Frame && ((Frame) w).getTitle().contains(titulo)) {
                return w;
            }
            if (w.isShowing() && w instanceof Dialog && ((Dialog) w).getTitle().contains(titulo)) {
                return w;
            }
        }
        throw new RuntimeException("No hay ventana " + titulo);
    }

    static Window dialogo() {
        Window r = null;
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w instanceof JDialog) {
                r = w;
            }
        }
        if (r == null) {
            throw new RuntimeException("No hay diálogo visible");
        }
        return r;
    }

    static void cerrar(Window w) throws Exception {
        SwingUtilities.invokeAndWait(w::dispose);
    }

    static void modulo(Window p, String clave) throws Exception {
        SwingUtilities.invokeLater(() -> ((sihca.ui.VentanaPrincipal) p).mostrar(clave));
    }

    static void clic(Window w, String texto) throws Exception {
        AbstractButton[] b = new AbstractButton[1];
        SwingUtilities.invokeAndWait(() -> b[0] = buscarBoton(w, texto));
        if (b[0] == null) {
            // ítems del menú lateral (JPanel con JLabel)
            JComponent[] it = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> it[0] = buscarEtiqueta(w, texto));
            if (it[0] == null) {
                throw new RuntimeException("Botón no encontrado: " + texto);
            }
            SwingUtilities.invokeLater(() -> {
                for (java.awt.event.MouseListener ml : it[0].getParent().getMouseListeners()) {
                    ml.mouseClicked(new java.awt.event.MouseEvent(it[0].getParent(), 500, 0, 0, 5, 5, 1, false));
                }
            });
            return;
        }
        SwingUtilities.invokeLater(b[0]::doClick);
    }

    static AbstractButton buscarBoton(Container c, String texto) {
        if (c instanceof RootPaneContainer) {
            c = ((RootPaneContainer) c).getContentPane();
        }
        for (Component x : todos(c)) {
            if (x instanceof AbstractButton && x.isShowing() && texto.equals(((AbstractButton) x).getText())) {
                return (AbstractButton) x;
            }
        }
        return null;
    }

    static JComponent buscarEtiqueta(Container c, String texto) {
        for (Component x : todos(((RootPaneContainer) c).getContentPane())) {
            if (x instanceof JLabel && texto.equals(((JLabel) x).getText())) {
                return (JComponent) x;
            }
        }
        return null;
    }

    static List<Component> todos(Container c) {
        List<Component> l = new ArrayList<>();
        for (Component x : c.getComponents()) {
            l.add(x);
            if (x instanceof Container) {
                l.addAll(todos((Container) x));
            }
        }
        return l;
    }

    static Object[] componentes(Window w, Class<?> tipo) {
        List<Object> l = new ArrayList<>();
        for (Component x : todos(((RootPaneContainer) w).getContentPane())) {
            if (tipo.isInstance(x) && x.isShowing()) {
                l.add(x);
            }
        }
        return l.toArray();
    }

    static void escribir(Window w, int indice, String texto) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int i = 0;
            for (Component x : todos(((RootPaneContainer) w).getContentPane())) {
                if (x instanceof JTextField && x.isShowing() && ((JTextField) x).isEditable()
                        && !(x.getParent() instanceof JSpinner.DefaultEditor)) {
                    if (i++ == indice) {
                        ((JTextComponent) x).setText(texto);
                        return;
                    }
                }
            }
        });
    }

    static void escribirCampo(Window w, String tooltip, String texto) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Component x : todos(((RootPaneContainer) w).getContentPane())) {
                if (x instanceof JTextField && x.isShowing() && ((JTextField) x).getToolTipText() != null
                        && ((JTextField) x).getToolTipText().startsWith(tooltip)) {
                    ((JTextField) x).setText(texto);
                    for (java.awt.event.KeyListener k : x.getKeyListeners()) {
                        k.keyReleased(new java.awt.event.KeyEvent(x, 402, 0, 0, 65, 'a'));
                    }
                }
            }
        });
    }

    static void elegir(Window w, int indice, String prefijo) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object[] cs = componentes(w, JComboBox.class);
            JComboBox<?> c = (JComboBox<?>) cs[indice];
            for (int i = 0; i < c.getItemCount(); i++) {
                if (c.getItemAt(i).toString().startsWith(prefijo)) {
                    c.setSelectedIndex(i);
                    return;
                }
            }
            StringBuilder sb = new StringBuilder();
            for (Object o : cs) { JComboBox<?> k = (JComboBox<?>) o; sb.append(k.getItemCount()).append(':').append(k.getItemCount() > 0 ? k.getItemAt(0) : "").append(" | "); }
            throw new RuntimeException("Opción no encontrada: " + prefijo + " en " + sb);
        });
    }

    static void seleccionarFila(Window w, int fila) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Object o : componentes(w, JTable.class)) {
                JTable t = (JTable) o;
                if (t.getRowCount() > fila) {
                    t.setRowSelectionInterval(fila, fila);
                    return;
                }
            }
        });
    }

    /** Pinta la ventana (con una barra de título neutra) a 2x y la guarda como PNG. */
    static void capturar(Window w, String nombre) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Component raiz = ((RootPaneContainer) w).getRootPane();
                int ancho = raiz.getWidth();
                int alto = raiz.getHeight();
                int barra = 34;
                double e = 2.0;
                BufferedImage img = new BufferedImage((int) ((ancho + 2) * e), (int) ((alto + barra + 2) * e),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.scale(e, e);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0x9AA3B2));
                g.fillRect(0, 0, ancho + 2, alto + barra + 2);
                g.setColor(new Color(0xF3F3F3));
                g.fillRect(1, 1, ancho, barra);
                String titulo = w instanceof Frame ? ((Frame) w).getTitle() : ((Dialog) w).getTitle();
                g.drawImage(sihca.ui.Aplicacion.logo(16), 12, 10, null);
                g.setColor(new Color(0x202020));
                g.setFont(sihca.ui.Tema.normal(12.5f));
                g.drawString(titulo, 36, 22);
                g.setColor(new Color(0x404040));
                g.setStroke(new BasicStroke(1f));
                int x = ancho - 22;
                g.drawLine(x - 5, 12, x + 5, 22);
                g.drawLine(x - 5, 22, x + 5, 12);
                if (w instanceof Frame) {
                    g.drawRect(x - 52, 12, 10, 10);
                    g.drawLine(x - 102, 17, x - 92, 17);
                }
                g.translate(1, barra + 1);
                raiz.paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(salida, nombre + ".png"));
                log.add(nombre + " " + ancho + "x" + alto);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
