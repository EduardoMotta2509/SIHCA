package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalTime;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import sihca.dao.ConsultaDAO;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/** Panel de inicio: indicadores del periodo de trabajo y accesos rápidos. */
public class PanelInicio extends Modulo {

    private final JLabel saludo = UI.titulo("", 18f);
    private final JPanel indicadores = new JPanel(new GridLayout(2, 4, 14, 14));
    private final Grafico grafico = new Grafico();

    public PanelInicio(VentanaPrincipal v) {
        super(v);
        JPanel arriba = new JPanel(new BorderLayout(0, 14));
        arriba.setOpaque(false);
        arriba.add(saludo, BorderLayout.NORTH);
        indicadores.setOpaque(false);
        arriba.add(indicadores, BorderLayout.CENTER);
        add(arriba, BorderLayout.NORTH);

        JPanel abajo = new JPanel(new BorderLayout(14, 0));
        abajo.setOpaque(false);
        Tarjeta tg = new Tarjeta(new BorderLayout(0, 10));
        tg.add(UI.titulo("Bloques programados por día y tipo", 14.5f), BorderLayout.NORTH);
        tg.add(grafico, BorderLayout.CENTER);
        abajo.add(tg, BorderLayout.CENTER);

        Tarjeta acc = new Tarjeta(new BorderLayout(0, 12));
        acc.setPreferredSize(new Dimension(300, 10));
        acc.add(UI.titulo("Accesos rápidos", 14.5f), BorderLayout.NORTH);
        JPanel bl = new JPanel(new GridLayout(5, 1, 0, 8));
        bl.setOpaque(false);
        bl.add(acceso("Ver matriz de horarios", Iconos.Tipo.MATRIZ, "PanelMatriz"));
        bl.add(acceso("Programar una clase", Iconos.Tipo.ACTIVIDAD, "PanelActividades"));
        bl.add(acceso("Generar reportes", Iconos.Tipo.REPORTE, "PanelReportes"));
        bl.add(acceso("Registrar docentes", Iconos.Tipo.DOCENTE, "PanelDocentes"));
        bl.add(acceso("Revisar bitácora", Iconos.Tipo.BITACORA, "PanelBitacora"));
        acc.add(bl, BorderLayout.CENTER);
        abajo.add(acc, BorderLayout.EAST);
        add(abajo, BorderLayout.CENTER);
    }

    private Boton acceso(String texto, Iconos.Tipo icono, String modulo) {
        Boton b = new Boton(texto, Iconos.de(icono, 17, Tema.PRIMARIO), Boton.Estilo.SECUNDARIO);
        b.setHorizontalAlignment(JLabel.LEFT);
        b.addActionListener(e -> ventana.mostrar(modulo));
        return b;
    }

    @Override public String titulo() { return "Inicio"; }
    @Override public String descripcion() { return "Resumen del periodo de trabajo y accesos rápidos"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.INICIO; }
    @Override public boolean dependeDelPeriodo() { return true; }

    @Override
    public void refrescar() {
        int h = LocalTime.now().getHour();
        String s = h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
        String nombre = Sesion.nombre();
        if (nombre != null && nombre.contains(",")) {
            nombre = nombre.substring(nombre.indexOf(',') + 1).trim();
        }
        saludo.setText(s + ", " + nombre + "  ·  Periodo " + Sesion.periodo()
                + (Sesion.periodoCerrado() ? " (cerrado, histórico)" : " (activo)"));
        ejecutar(() -> {
            Resultado r = ConsultaDAO.resumenInicio(Sesion.periodo());
            indicadores.removeAll();
            indicadores.add(indicador("Docentes vigentes", r.texto(0, 0), Iconos.Tipo.DOCENTE, Tema.PRIMARIO));
            indicadores.add(indicador("Secciones", r.texto(0, 1), Iconos.Tipo.GRADO, new Color(0x7B4FC9)));
            indicadores.add(indicador("Espacios disponibles", r.texto(0, 2), Iconos.Tipo.ESPACIO, new Color(0x0F8A8A)));
            indicadores.add(indicador("Asignaturas vigentes", r.texto(0, 3), Iconos.Tipo.ASIGNATURA, new Color(0xC0612B)));
            indicadores.add(indicador("Bloques programados", r.texto(0, 4), Iconos.Tipo.ACTIVIDAD, Tema.PRIMARIO));
            indicadores.add(indicador("Cumplimiento del plan", r.texto(0, 7) + " %", Iconos.Tipo.PLAN, Tema.EXITO));
            long sin = r.numero(0, 6);
            indicadores.add(indicador("Bloques sin docente", String.valueOf(sin), Iconos.Tipo.ALERTA,
                    sin > 0 ? Tema.PELIGRO : Tema.EXITO));
            indicadores.add(indicador("Cambios registrados hoy", r.texto(0, 8), Iconos.Tipo.BITACORA, new Color(0x5F6B7E)));
            indicadores.revalidate();
            indicadores.repaint();
            grafico.datos = ConsultaDAO.reporte("sp_rep_resumen_naturaleza", Sesion.periodo());
            grafico.repaint();
            ventana.estado("Indicadores del periodo " + Sesion.periodo() + " actualizados.");
        });
    }

    private JPanel indicador(String etiqueta, String valor, Iconos.Tipo icono, Color color) {
        Tarjeta t = new Tarjeta(new BorderLayout(12, 0));
        t.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JLabel ic = new JLabel(Iconos.de(icono, 22, color)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        ic.setPreferredSize(new Dimension(46, 46));
        t.add(ic, BorderLayout.WEST);
        JPanel txt = new JPanel(new BorderLayout(0, 2));
        txt.setOpaque(false);
        JLabel v = new JLabel(valor);
        v.setFont(Tema.negrita(24f));
        v.setForeground(Tema.TEXTO);
        txt.add(v, BorderLayout.CENTER);
        txt.add(UI.texto(etiqueta, 12.5f, Tema.TEXTO_SUAVE), BorderLayout.SOUTH);
        t.add(txt, BorderLayout.CENTER);
        return t;
    }

    /** Gráfico de barras apiladas (bloques por día y naturaleza) dibujado con Java2D. */
    private static final class Grafico extends JPanel {
        Resultado datos = Resultado.vacio();
        private final Color[] colores = {Tema.PRIMARIO, new Color(0x9AA5B8), new Color(0x7B4FC9), Tema.DORADO};

        Grafico() {
            setOpaque(false);
            setPreferredSize(new Dimension(400, 260));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (datos.filas() == 0) {
                g.setColor(Tema.TEXTO_TENUE);
                g.setFont(Tema.normal(13f));
                g.drawString("No hay bloques programados en este periodo.", 10, 30);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            String[] dias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
            long max = 0;
            for (int d = 0; d < 5; d++) {
                long s = 0;
                for (int i = 0; i < datos.filas(); i++) {
                    s += datos.numero(i, d + 1);
                }
                max = Math.max(max, s);
            }
            int izq = 44;
            int abajo = getHeight() - 52;
            int alto = abajo - 14;
            int ancho = getWidth() - izq - 10;
            long escala = ((max + 49) / 50) * 50;
            g2.setFont(Tema.normal(11.5f));
            FontMetrics fm = g2.getFontMetrics();
            for (int k = 0; k <= 4; k++) {
                int y = abajo - (int) (alto * k / 4.0);
                g2.setColor(new Color(0xEEF1F6));
                g2.drawLine(izq, y, izq + ancho, y);
                g2.setColor(Tema.TEXTO_TENUE);
                String t = String.valueOf(escala * k / 4);
                g2.drawString(t, izq - 8 - fm.stringWidth(t), y + 4);
            }
            int bw = Math.min(70, ancho / 5 - 30);
            for (int d = 0; d < 5; d++) {
                int cx = izq + (int) (ancho * (d + 0.5) / 5);
                int y = abajo;
                for (int i = 0; i < datos.filas(); i++) {
                    long v = datos.numero(i, d + 1);
                    int h = (int) Math.round(alto * (double) v / escala);
                    g2.setColor(colores[i % colores.length]);
                    g2.fillRect(cx - bw / 2, y - h, bw, h);
                    y -= h;
                }
                g2.setColor(Tema.TEXTO_SUAVE);
                g2.setFont(Tema.medio(12f));
                fm = g2.getFontMetrics();
                g2.drawString(dias[d], cx - fm.stringWidth(dias[d]) / 2, abajo + 18);
            }
            int lx = izq;
            g2.setFont(Tema.normal(12f));
            fm = g2.getFontMetrics();
            for (int i = 0; i < datos.filas(); i++) {
                g2.setColor(colores[i % colores.length]);
                g2.fillRoundRect(lx, getHeight() - 18, 12, 12, 4, 4);
                g2.setColor(Tema.TEXTO_SUAVE);
                String t = datos.texto(i, 0) + " (" + datos.texto(i, 6) + ")";
                g2.drawString(t, lx + 17, getHeight() - 8);
                lx += 17 + fm.stringWidth(t) + 22;
            }
            g2.dispose();
        }
    }
}
