package sihca.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import sihca.Main;
import sihca.dao.UsuarioDAO;
import sihca.datos.Conexion;
import sihca.datos.Errores;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.UI;

/** Pantalla de inicio de sesión. */
public class VentanaLogin extends JFrame {

    private final JTextField usuario = UI.campo(20);
    private final JPasswordField clave = UI.campoClave(20);
    private final JLabel error = new JLabel(" ");
    private final JLabel estado = new JLabel();

    public VentanaLogin() {
        super(Main.NOMBRE + " - Iniciar sesión");
        setIconImages(Aplicacion.iconosVentana());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(new PanelMarca(), BorderLayout.WEST);
        raiz.add(formulario(), BorderLayout.CENTER);
        setContentPane(raiz);
        setSize(920, 580);
        setMinimumSize(new Dimension(860, 540));
        setLocationRelativeTo(null);
        actualizarEstado();
    }

    private JPanel formulario() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Tema.SUPERFICIE);
        JPanel caja = new JPanel();
        caja.setOpaque(false);
        caja.setLayout(new BoxLayout(caja, BoxLayout.Y_AXIS));

        JLabel t = UI.titulo("Iniciar sesión", 24f);
        JLabel s = UI.texto("Ingrese sus credenciales para acceder al sistema.", 13.5f, Tema.TEXTO_SUAVE);
        caja.add(alinear(t));
        caja.add(espacio(6));
        caja.add(alinear(s));
        caja.add(espacio(26));
        caja.add(alinear(UI.etiqueta("Usuario")));
        caja.add(espacio(6));
        usuario.setMaximumSize(new Dimension(340, 40));
        caja.add(alinear(usuario));
        caja.add(espacio(14));
        caja.add(alinear(UI.etiqueta("Contraseña")));
        caja.add(espacio(6));
        clave.setMaximumSize(new Dimension(340, 40));
        caja.add(alinear(clave));
        caja.add(espacio(10));
        error.setFont(Tema.medio(12.5f));
        error.setForeground(Tema.PELIGRO);
        caja.add(alinear(error));
        caja.add(espacio(10));

        Boton ingresar = Boton.primario("Ingresar al sistema");
        ingresar.setMaximumSize(new Dimension(340, 40));
        ingresar.setPreferredSize(new Dimension(340, 40));
        ingresar.addActionListener(e -> ingresar());
        caja.add(alinear(ingresar));
        caja.add(espacio(18));

        JLabel demo = UI.texto("<html>Cuentas de prueba: <b>admin</b> / admin123 &nbsp;·&nbsp; <b>coordinador</b> / coord123"
                + " &nbsp;·&nbsp; <b>consulta</b> / consulta123</html>", 12f, Tema.TEXTO_TENUE);
        caja.add(alinear(demo));
        caja.add(espacio(28));

        JPanel pie = new JPanel(new BorderLayout(10, 0));
        pie.setOpaque(false);
        estado.setFont(Tema.normal(12f));
        estado.setForeground(Tema.TEXTO_SUAVE);
        pie.add(estado, BorderLayout.CENTER);
        Boton config = new Boton("Configurar conexión", Iconos.de(Iconos.Tipo.BD, 16, Tema.PRIMARIO), Boton.Estilo.FANTASMA);
        config.addActionListener(e -> {
            DialogoConexion.mostrar(this, null);
            actualizarEstado();
        });
        pie.add(config, BorderLayout.EAST);
        pie.setMaximumSize(new Dimension(340, 40));
        caja.add(alinear(pie));

        getRootPane().setDefaultButton(ingresar);
        p.add(caja, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER,
                GridBagConstraints.NONE, new Insets(20, 20, 20, 20), 0, 0));
        return p;
    }

    private void actualizarEstado() {
        estado.setIcon(null);
        try {
            String v = sihca.datos.Conexion.probar(Conexion.config(), true);
            estado.setText("Conectado a MySQL " + v);
            estado.setIcon(Iconos.de(Iconos.Tipo.OK, 14, Tema.EXITO));
        } catch (SQLException ex) {
            estado.setText("Sin conexión a la base de datos");
            estado.setIcon(Iconos.de(Iconos.Tipo.ALERTA, 14, Tema.PELIGRO));
        }
    }

    private void ingresar() {
        String u = usuario.getText().trim();
        String c = new String(clave.getPassword());
        if (u.isEmpty() || c.isEmpty()) {
            error.setText("Ingrese el usuario y la contraseña.");
            return;
        }
        try {
            Resultado r = UsuarioDAO.login(u, c);
            Sesion.iniciar(r.texto(0, 0), r.texto(0, 1), r.texto(0, 2));
            dispose();
            Aplicacion.abrirPrincipal();
        } catch (SQLException ex) {
            error.setText("<html><div style='width:330px'>" + Errores.mensaje(ex) + "</div></html>");
            clave.setText("");
            clave.requestFocusInWindow();
        }
    }

    private static javax.swing.JComponent alinear(javax.swing.JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        if (c instanceof JLabel && c.getBorder() == null) {
            c.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12)); // evita recortes del último carácter
        }
        return c;
    }

    private static javax.swing.JComponent espacio(int h) {
        JPanel e = new JPanel();
        e.setOpaque(false);
        e.setPreferredSize(new Dimension(1, h));
        e.setMaximumSize(new Dimension(1, h));
        e.setAlignmentX(LEFT_ALIGNMENT);
        return e;
    }

    /** Panel lateral con el nombre del sistema y de la institución. */
    private static final class PanelMarca extends JPanel {
        PanelMarca() {
            setPreferredSize(new Dimension(380, 10));
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(44, 40, 36, 40));
            setOpaque(false);
            JPanel arriba = new JPanel();
            arriba.setOpaque(false);
            arriba.setLayout(new BoxLayout(arriba, BoxLayout.Y_AXIS));
            JLabel logo = new JLabel(new ImageIcon(Aplicacion.logo(56)));
            arriba.add(alinear(logo));
            arriba.add(espacio(22));
            JLabel n = new JLabel(Main.NOMBRE);
            n.setFont(Tema.negrita(40f));
            n.setForeground(Color.WHITE);
            arriba.add(alinear(n));
            JLabel nl = new JLabel("<html>" + Main.NOMBRE_LARGO + "</html>");
            nl.setFont(Tema.medio(18f));
            nl.setForeground(new Color(0xDCE4F5));
            arriba.add(alinear(nl));
            arriba.add(espacio(26));
            JLabel d = new JLabel("<html><div style='width:240px'>Organice la carga académica, valide cruces de "
                    + "horario y aforos, y conserve el registro histórico de cada año lectivo.</div></html>");
            d.setFont(Tema.normal(14f));
            d.setForeground(new Color(0xB9C6E0));
            arriba.add(alinear(d));
            add(arriba, BorderLayout.NORTH);

            JPanel abajo = new JPanel();
            abajo.setOpaque(false);
            abajo.setLayout(new BoxLayout(abajo, BoxLayout.Y_AXIS));
            JLabel col = new JLabel("<html>Para instituciones educativas de<br>Inicial, Primaria y Secundaria</html>");
            col.setFont(Tema.semi(14f));
            col.setForeground(Color.WHITE);
            abajo.add(alinear(col));
            abajo.add(espacio(4));
            JLabel lema = new JLabel("Cruces, aforos e histórico bajo control");
            lema.setFont(Tema.normal(12.5f));
            lema.setForeground(Tema.DORADO);
            abajo.add(alinear(lema));
            abajo.add(espacio(16));
            JLabel v = new JLabel("Versión " + Main.VERSION + "  ·  GDI - Grupo 09");
            v.setFont(Tema.normal(11.5f));
            v.setForeground(new Color(0x8C9BBA));
            abajo.add(alinear(v));
            add(abajo, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, Tema.AZUL_MEDIO, getWidth(), getHeight(), Tema.AZUL_OSCURO));
            g2.fillRect(0, 0, getWidth(), getHeight());
            // trama decorativa de cuadrícula de horario
            g2.setColor(new Color(255, 255, 255, 14));
            int c = 34;
            for (int x = getWidth() - 6 * c; x < getWidth(); x += c) {
                for (int y = getHeight() / 2 - 2 * c; y < getHeight() / 2 + 3 * c; y += c) {
                    g2.fillRoundRect(x + 3, y + 3, c - 6, c - 6, 8, 8);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
