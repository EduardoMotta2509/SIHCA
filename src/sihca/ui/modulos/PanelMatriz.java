package sihca.ui.modulos;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import sihca.dao.ConsultaDAO;
import sihca.dao.DocenteDAO;
import sihca.dao.GradoSeccionDAO;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Opcion;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/**
 * Matriz de horarios: cuadrícula semanal (días x franjas) de una sección o de
 * un docente, coloreada por asignatura. Se puede exportar como imagen PNG para
 * publicarla en la plataforma EdusoftNet, o imprimir.
 */
public class PanelMatriz extends Modulo {

    private final JComboBox<String> vista = UI.combo("Por sección", "Por docente");
    private final JComboBox<Opcion> elemento = UI.combo();
    private final Cuadricula cuadricula = new Cuadricula();
    private boolean cargando;

    public PanelMatriz(VentanaPrincipal v) {
        super(v);
        vista.setPreferredSize(new Dimension(150, 36));
        elemento.setPreferredSize(new Dimension(330, 36));
        vista.addActionListener(e -> cargarElementos());
        elemento.addActionListener(e -> {
            if (!cargando) {
                cargarMatriz();
            }
        });
        Boton png = boton("Exportar imagen", Iconos.Tipo.EXPORTAR, Boton.Estilo.SECUNDARIO);
        Boton imp = boton("Imprimir", Iconos.Tipo.IMPRIMIR, Boton.Estilo.SECUNDARIO);
        png.addActionListener(e -> exportarPng());
        imp.addActionListener(e -> imprimir());
        add(barra(UI.fila(8, UI.etiqueta("Ver"), vista, elemento), UI.filaDerecha(8, imp, png)), BorderLayout.NORTH);

        Tarjeta t = new Tarjeta(new BorderLayout());
        t.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane sp = new JScrollPane(cuadricula);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Tema.SUPERFICIE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        t.add(sp, BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);
    }

    @Override public String titulo() { return "Matriz de horarios"; }
    @Override public String descripcion() { return "Horario semanal por sección o por docente, listo para publicar en EdusoftNet"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.MATRIZ; }
    @Override public boolean dependeDelPeriodo() { return true; }

    @Override
    public void refrescar() {
        if (elemento.getItemCount() == 0) {
            cargarElementos();
        } else {
            cargarMatriz();
        }
    }

    /** Selecciona una vista y un elemento desde otro módulo (p. ej. la sección 211). */
    public void seleccionar(boolean porSeccion, Object valor) {
        vista.setSelectedIndex(porSeccion ? 0 : 1);
        UI.seleccionar(elemento, valor);
    }

    private void cargarElementos() {
        cargando = true;
        ejecutar(() -> {
            elemento.removeAllItems();
            if (vista.getSelectedIndex() == 0) {
                Resultado r = GradoSeccionDAO.listarSecciones(null);
                for (int i = 0; i < r.filas(); i++) {
                    elemento.addItem(new Opcion(r.numero(i, 0), r.texto(i, 1)));
                }
                UI.seleccionar(elemento, 211);
            } else {
                Resultado r = DocenteDAO.listar("", false);
                for (int i = 0; i < r.filas(); i++) {
                    elemento.addItem(new Opcion(r.texto(i, 0), r.texto(i, 1) + "  (" + r.texto(i, 0) + ")"));
                }
            }
        });
        cargando = false;
        cargarMatriz();
    }

    private void cargarMatriz() {
        Object v = UI.valor(elemento);
        if (v == null) {
            return;
        }
        boolean porSeccion = vista.getSelectedIndex() == 0;
        ejecutar(() -> {
            Resultado r = porSeccion
                    ? ConsultaDAO.reporte("sp_rep_matriz_seccion", Sesion.periodo(), ((Number) v).intValue())
                    : ConsultaDAO.reporte("sp_rep_horario_docente", Sesion.periodo(), v.toString());
            cuadricula.cargar(r, porSeccion, elemento.getSelectedItem().toString());
            ventana.estado(r.filas() + " bloques  ·  " + (porSeccion ? "sp_rep_matriz_seccion" : "sp_rep_horario_docente"));
        });
    }

    private void exportarPng() {
        if (cuadricula.celdas.isEmpty()) {
            Mensajes.advertencia(this, "Nada que exportar", "La matriz seleccionada no tiene bloques.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar matriz como imagen");
        fc.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
        fc.setSelectedFile(new File("Horario " + cuadricula.titulo.replaceAll("[^\\p{L}0-9 .-]", "")
                + " " + Sesion.periodo() + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }
        int w = 1400;
        int h = cuadricula.altoNecesario(w);
        BufferedImage img = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(2, 2);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        cuadricula.dibujar(g, w, h);
        g.dispose();
        try {
            ImageIO.write(img, "png", f);
            Mensajes.exito(this, "Imagen exportada", "La matriz se guardó en:\n" + f.getAbsolutePath());
        } catch (IOException ex) {
            Mensajes.error(this, "No se pudo exportar", ex.getMessage());
        }
    }

    private void imprimir() {
        if (cuadricula.celdas.isEmpty()) {
            Mensajes.advertencia(this, "Nada que imprimir", "La matriz seleccionada no tiene bloques.");
            return;
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Horario " + cuadricula.titulo);
        PageFormat pf = job.defaultPage();
        pf.setOrientation(PageFormat.LANDSCAPE);
        job.setPrintable((g, formato, pagina) -> {
            if (pagina > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.translate(formato.getImageableX(), formato.getImageableY());
            int w = 1400;
            int h = cuadricula.altoNecesario(w);
            double esc = Math.min(formato.getImageableWidth() / w, formato.getImageableHeight() / h);
            g2.scale(esc, esc);
            cuadricula.dibujar(g2, w, h);
            return Printable.PAGE_EXISTS;
        }, pf);
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ex) {
                Mensajes.error(this, "No se pudo imprimir", ex.getMessage());
            }
        }
    }

    /** Componente que dibuja la cuadrícula del horario. */
    private static final class Cuadricula extends JPanel {
        private static final String[] DIAS = Bloques.DIAS;
        private static final Color[] PALETA = {
            new Color(0xDCE8FD), new Color(0xDDF3E6), new Color(0xFDEBD3), new Color(0xEFE3FB),
            new Color(0xD9F1F3), new Color(0xFCE1E4), new Color(0xF3F0D2), new Color(0xE3E9F5),
            new Color(0xFFE6CC), new Color(0xE0F2D9), new Color(0xE6E1F7), new Color(0xD7ECF8),
            new Color(0xF8E0F0), new Color(0xE9EEDA), new Color(0xF4E5DA)
        };
        private static final Color[] TINTA = {
            new Color(0x1F4E9A), new Color(0x17693F), new Color(0x93540E), new Color(0x5E3192),
            new Color(0x116B73), new Color(0x9C2A3A), new Color(0x6B6315), new Color(0x3B4A6B),
            new Color(0x9A4B00), new Color(0x376B22), new Color(0x4A3D8F), new Color(0x1C5D85),
            new Color(0x8A2A6E), new Color(0x55622B), new Color(0x7A4A2C)
        };

        final List<String[]> celdas = new ArrayList<>(); // dia, inicio, fin, texto, linea2, linea3, exigencia
        final List<String> franjas = new ArrayList<>();
        final Map<String, Integer> colorMateria = new HashMap<>();
        String titulo = "";
        boolean porSeccion = true;

        Cuadricula() {
            setBackground(Tema.SUPERFICIE);
            setToolTipText("");
        }

        void cargar(Resultado r, boolean seccion, String nombre) {
            porSeccion = seccion;
            titulo = nombre;
            celdas.clear();
            franjas.clear();
            TreeSet<String> fr = new TreeSet<>();
            TreeSet<String> materias = new TreeSet<>();
            for (int i = 0; i < r.filas(); i++) {
                String linea2 = seccion ? corto(r.texto(i, 5)) : r.texto(i, 5);
                String linea3 = r.texto(i, 6);
                celdas.add(new String[] {r.texto(i, 0), r.texto(i, 1), r.texto(i, 2), r.texto(i, 3),
                    linea2, linea3, seccion ? r.texto(i, 4) : ""});
                fr.add(r.texto(i, 1) + "-" + r.texto(i, 2));
                materias.add(r.texto(i, 3));
            }
            franjas.addAll(fr);
            colorMateria.clear();
            int k = 0;
            for (String m : materias) {
                colorMateria.put(m, k++ % PALETA.length);
            }
            setPreferredSize(new Dimension(900, altoNecesario(900)));
            revalidate();
            repaint();
        }

        /** "Paredes Villanueva, Carlos Alberto; Otro" -> "Paredes V., Carlos" */
        private static String corto(String s) {
            if (s.startsWith("(")) {
                return s;
            }
            String p = s.split(";")[0].trim();
            int c = p.indexOf(',');
            if (c < 0) {
                return p;
            }
            String[] ap = p.substring(0, c).trim().split(" ");
            String[] no = p.substring(c + 1).trim().split(" ");
            return ap[0] + (ap.length > 1 ? " " + ap[1].charAt(0) + "." : "") + ", " + no[0];
        }

        int altoNecesario(int ancho) {
            return 70 + 34 + franjas.size() * 64 + 40;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            dibujar((Graphics2D) g, getWidth(), getHeight());
        }

        void dibujar(Graphics2D g0, int w, int h) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (celdas.isEmpty()) {
                g.setColor(Tema.TEXTO_TENUE);
                g.setFont(Tema.normal(14f));
                g.drawString("No hay bloques registrados para esta selección en el periodo " + Sesion.periodo() + ".", 16, 40);
                g.dispose();
                return;
            }
            g.setColor(Tema.TEXTO);
            g.setFont(Tema.semi(17f));
            g.drawString((porSeccion ? "Horario de " : "Horario del docente ") + titulo, 8, 26);
            g.setFont(Tema.normal(12.5f));
            g.setColor(Tema.TEXTO_SUAVE);
            g.drawString("Periodo " + Sesion.periodo() + "  ·  Colegio Particular Esclavas del Sagrado Corazón de Jesús"
                    + "  ·  " + celdas.size() + " bloques", 8, 46);

            int x0 = 8;
            int y0 = 62;
            int colHora = 92;
            int cw = (w - x0 - colHora - 8) / 5;
            int filaH = 64;
            g.setFont(Tema.semi(12.5f));
            for (int d = 0; d < 5; d++) {
                int x = x0 + colHora + d * cw;
                g.setColor(Tema.AZUL_OSCURO);
                g.fillRoundRect(x + 2, y0, cw - 4, 30, 8, 8);
                g.setColor(Color.WHITE);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(DIAS[d], x + (cw - fm.stringWidth(DIAS[d])) / 2, y0 + 20);
            }
            int y = y0 + 34;
            for (String fr : franjas) {
                String[] hh = fr.split("-");
                g.setColor(Tema.TEXTO);
                g.setFont(Tema.semi(13f));
                g.drawString(hh[0], x0 + 6, y + 24);
                g.setColor(Tema.TEXTO_TENUE);
                g.setFont(Tema.normal(11.5f));
                g.drawString("a " + hh[1], x0 + 6, y + 42);
                for (int d = 0; d < 5; d++) {
                    int x = x0 + colHora + d * cw;
                    String[] c = buscar(DIAS[d], hh[0], hh[1]);
                    if (c == null) {
                        g.setColor(new Color(0xF6F7FA));
                        g.fillRoundRect(x + 2, y + 2, cw - 4, filaH - 4, 10, 10);
                        continue;
                    }
                    dibujarCelda(g, c, x + 2, y + 2, cw - 4, filaH - 4);
                }
                y += filaH;
            }
            // leyenda
            g.setFont(Tema.normal(11.5f));
            g.setColor(Tema.TEXTO_SUAVE);
            int ly = y + 22;
            g.setColor(Tema.PELIGRO);
            g.fillOval(x0 + 4, ly - 9, 9, 9);
            g.setColor(Tema.TEXTO_SUAVE);
            g.drawString(porSeccion ? "Asignatura de exigencia cognitiva alta (se prefiere en las primeras horas)"
                    : "Secciones atendidas y ambiente de cada bloque", x0 + 18, ly);
            g.dispose();
        }

        private void dibujarCelda(Graphics2D g, String[] c, int x, int y, int w, int h) {
            String materia = c[3];
            boolean noLectivo = materia.equals("Receso") || materia.equals("Consejo") || materia.equals("Pastoral");
            Color fondo;
            Color tinta;
            if (noLectivo) {
                fondo = materia.equals("Receso") ? new Color(0xEEF0F4) : new Color(0xF1E9FB);
                tinta = materia.equals("Receso") ? Tema.TEXTO_SUAVE : new Color(0x5E3192);
            } else {
                int k = colorMateria.getOrDefault(materia, 0);
                fondo = PALETA[k];
                tinta = TINTA[k];
            }
            g.setColor(fondo);
            g.fillRoundRect(x, y, w, h, 10, 10);
            g.setColor(tinta);
            g.fillRoundRect(x, y, 4, h, 4, 4);
            if (noLectivo) {
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f));
                g.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
                g.setStroke(new BasicStroke(1f));
            }
            int tx = x + 11;
            g.setFont(Tema.semi(12.5f));
            g.setColor(tinta);
            g.drawString(recortar(g, materia, w - 30), tx, y + 18);
            if ("Alta".equals(c[6])) {
                g.setColor(Tema.PELIGRO);
                g.fillOval(x + w - 15, y + 8, 8, 8);
            }
            g.setFont(Tema.normal(11.5f));
            g.setColor(Tema.TEXTO);
            g.drawString(recortar(g, c[4], w - 16), tx, y + 35);
            g.setColor(Tema.TEXTO_SUAVE);
            g.drawString(recortar(g, c[5], w - 16), tx, y + 50);
        }

        private static String recortar(Graphics2D g, String s, int ancho) {
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(s) <= ancho) {
                return s;
            }
            String t = s;
            while (t.length() > 1 && fm.stringWidth(t + "…") > ancho) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "…";
        }

        private String[] buscar(String dia, String ini, String fin) {
            for (String[] c : celdas) {
                if (c[0].equals(dia) && c[1].equals(ini) && c[2].equals(fin)) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int x0 = 8 + 92;
            int cw = (getWidth() - 8 - 92 - 8) / 5;
            int d = (e.getX() - x0) / Math.max(cw, 1);
            int f = (e.getY() - 96) / 64;
            if (e.getX() < x0 || d < 0 || d > 4 || f < 0 || f >= franjas.size()) {
                return null;
            }
            String[] hh = franjas.get(f).split("-");
            String[] c = buscar(DIAS[d], hh[0], hh[1]);
            if (c == null) {
                return DIAS[d] + " " + franjas.get(f) + ": libre";
            }
            return "<html><b>" + c[3] + "</b><br>" + DIAS[d] + " " + c[1] + " - " + c[2] + "<br>" + c[4] + "<br>"
                    + c[5] + (c[6].isEmpty() ? "" : "<br>Exigencia: " + c[6]) + "</html>";
        }
    }
}
