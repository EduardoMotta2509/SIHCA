package sihca.ui.componentes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Íconos vectoriales dibujados con Java2D (no se usan archivos de imagen),
 * de modo que se ven nítidos en pantallas de cualquier resolución.
 */
public final class Iconos {

    public enum Tipo {
        INICIO, DOCENTE, ASIGNATURA, ESPACIO, GRADO, PLAN, PERIODO, ACTIVIDAD, MATRIZ, REPORTE,
        BITACORA, USUARIOS, SALIR, BD, BUSCAR, MAS, EDITAR, ELIMINAR, REFRESCAR, EXPORTAR, IMPRIMIR,
        REACTIVAR, PERSONAS, COPIAR, LLAVE, CANDADO, OK, ALERTA
    }

    private Iconos() {
    }

    public static Icon de(Tipo tipo, int tam, Color color) {
        return new Vector(tipo, tam, color);
    }

    private static final class Vector implements Icon {
        private final Tipo tipo;
        private final int tam;
        private final Color color;

        Vector(Tipo tipo, int tam, Color color) {
            this.tipo = tipo;
            this.tam = tam;
            this.color = color;
        }

        @Override public int getIconWidth() { return tam; }
        @Override public int getIconHeight() { return tam; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            double s = tam / 24.0;
            g2.scale(s, s);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            dibujar(g2);
            g2.dispose();
        }

        private void dibujar(Graphics2D g) {
            Path2D p = new Path2D.Double();
            switch (tipo) {
                case INICIO:
                    p.moveTo(3, 11); p.lineTo(12, 3.5); p.lineTo(21, 11);
                    p.moveTo(5.5, 9.5); p.lineTo(5.5, 20); p.lineTo(18.5, 20); p.lineTo(18.5, 9.5);
                    p.moveTo(10, 20); p.lineTo(10, 14); p.lineTo(14, 14); p.lineTo(14, 20);
                    g.draw(p);
                    break;
                case DOCENTE:
                    g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                    g.draw(new Arc2D.Double(4, 13.5, 16, 14, 0, 180, Arc2D.OPEN));
                    break;
                case PERSONAS:
                case USUARIOS:
                    g.draw(new Ellipse2D.Double(5, 5, 7, 7));
                    g.draw(new Arc2D.Double(2, 14, 13, 12, 0, 180, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(13, 4.5, 6.5, 6.5, -100, 280, Arc2D.OPEN));
                    g.draw(new Arc2D.Double(15, 13.5, 7, 9, 0, 110, Arc2D.OPEN));
                    break;
                case ASIGNATURA:
                    p.moveTo(4, 5); p.lineTo(10, 5); p.quadTo(12, 5, 12, 7.5); p.lineTo(12, 20);
                    p.quadTo(12, 18, 10, 18); p.lineTo(4, 18); p.closePath();
                    p.moveTo(20, 5); p.lineTo(14, 5); p.quadTo(12, 5, 12, 7.5);
                    p.moveTo(12, 20); p.quadTo(12, 18, 14, 18); p.lineTo(20, 18); p.lineTo(20, 5);
                    g.draw(p);
                    break;
                case ESPACIO:
                    g.draw(new RoundRectangle2D.Double(4, 4, 16, 17, 2, 2));
                    p.moveTo(8, 8); p.lineTo(10, 8); p.moveTo(14, 8); p.lineTo(16, 8);
                    p.moveTo(8, 12); p.lineTo(10, 12); p.moveTo(14, 12); p.lineTo(16, 12);
                    p.moveTo(10.5, 21); p.lineTo(10.5, 16.5); p.lineTo(13.5, 16.5); p.lineTo(13.5, 21);
                    g.draw(p);
                    break;
                case GRADO:
                    p.moveTo(12, 3.5); p.lineTo(21, 8); p.lineTo(12, 12.5); p.lineTo(3, 8); p.closePath();
                    p.moveTo(3, 12); p.lineTo(12, 16.5); p.lineTo(21, 12);
                    p.moveTo(3, 16); p.lineTo(12, 20.5); p.lineTo(21, 16);
                    g.draw(p);
                    break;
                case PLAN:
                    g.draw(new RoundRectangle2D.Double(5, 3.5, 14, 17.5, 3, 3));
                    p.moveTo(8.5, 9); p.lineTo(9.8, 10.3); p.lineTo(12, 8);
                    p.moveTo(13.5, 9.3); p.lineTo(16, 9.3);
                    p.moveTo(8.5, 14.5); p.lineTo(9.8, 15.8); p.lineTo(12, 13.5);
                    p.moveTo(13.5, 14.8); p.lineTo(16, 14.8);
                    g.draw(p);
                    break;
                case PERIODO:
                    g.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 3, 3));
                    p.moveTo(3.5, 10); p.lineTo(20.5, 10);
                    p.moveTo(8, 3); p.lineTo(8, 7); p.moveTo(16, 3); p.lineTo(16, 7);
                    g.draw(p);
                    g.fill(new Ellipse2D.Double(7, 13, 2.4, 2.4));
                    g.fill(new Ellipse2D.Double(11, 13, 2.4, 2.4));
                    g.fill(new Ellipse2D.Double(15, 13, 2.4, 2.4));
                    break;
                case ACTIVIDAD:
                    g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                    p.moveTo(12, 7.5); p.lineTo(12, 12); p.lineTo(15, 14);
                    g.draw(p);
                    break;
                case MATRIZ:
                    g.draw(new RoundRectangle2D.Double(3.5, 3.5, 17, 17, 3, 3));
                    p.moveTo(3.5, 9.2); p.lineTo(20.5, 9.2); p.moveTo(3.5, 14.8); p.lineTo(20.5, 14.8);
                    p.moveTo(9.2, 3.5); p.lineTo(9.2, 20.5); p.moveTo(14.8, 3.5); p.lineTo(14.8, 20.5);
                    g.draw(p);
                    break;
                case REPORTE:
                    p.moveTo(4, 20); p.lineTo(20, 20);
                    p.moveTo(7, 16.5); p.lineTo(7, 12); p.moveTo(11, 16.5); p.lineTo(11, 6);
                    p.moveTo(15, 16.5); p.lineTo(15, 9.5); p.moveTo(19, 16.5); p.lineTo(19, 4);
                    g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(p);
                    break;
                case BITACORA:
                    p.moveTo(12, 3); p.lineTo(19.5, 6); p.lineTo(19.5, 11.5);
                    p.quadTo(19.5, 18, 12, 21); p.quadTo(4.5, 18, 4.5, 11.5); p.lineTo(4.5, 6); p.closePath();
                    p.moveTo(8.8, 12); p.lineTo(11, 14.2); p.lineTo(15.3, 9.8);
                    g.draw(p);
                    break;
                case SALIR:
                    p.moveTo(10, 4); p.lineTo(5, 4); p.lineTo(5, 20); p.lineTo(10, 20);
                    p.moveTo(10, 12); p.lineTo(20, 12); p.moveTo(16.5, 8.5); p.lineTo(20, 12); p.lineTo(16.5, 15.5);
                    g.draw(p);
                    break;
                case BD:
                    g.draw(new Ellipse2D.Double(5, 3.5, 14, 5));
                    p.moveTo(5, 6); p.lineTo(5, 18); p.quadTo(12, 22.5, 19, 18); p.lineTo(19, 6);
                    p.moveTo(5, 12); p.quadTo(12, 16.5, 19, 12);
                    g.draw(p);
                    break;
                case BUSCAR:
                    g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                    g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
                    break;
                case MAS:
                    p.moveTo(12, 5); p.lineTo(12, 19); p.moveTo(5, 12); p.lineTo(19, 12);
                    g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(p);
                    break;
                case EDITAR:
                    p.moveTo(15.5, 4.5); p.lineTo(19.5, 8.5); p.lineTo(8.5, 19.5); p.lineTo(4.5, 19.5);
                    p.lineTo(4.5, 15.5); p.closePath();
                    p.moveTo(13, 7); p.lineTo(17, 11);
                    g.draw(p);
                    break;
                case ELIMINAR:
                    p.moveTo(4, 7); p.lineTo(20, 7); p.moveTo(9, 7); p.lineTo(9, 4.5); p.lineTo(15, 4.5);
                    p.lineTo(15, 7); p.moveTo(6, 7); p.lineTo(7, 20); p.lineTo(17, 20); p.lineTo(18, 7);
                    p.moveTo(10, 11); p.lineTo(10, 16.5); p.moveTo(14, 11); p.lineTo(14, 16.5);
                    g.draw(p);
                    break;
                case REFRESCAR:
                    g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 60, 280, Arc2D.OPEN));
                    p.moveTo(15.5, 3.5); p.lineTo(16.3, 6.2); p.lineTo(13.5, 7);
                    g.draw(p);
                    break;
                case EXPORTAR:
                    p.moveTo(12, 4); p.lineTo(12, 15); p.moveTo(7.5, 10.5); p.lineTo(12, 15); p.lineTo(16.5, 10.5);
                    p.moveTo(4.5, 15.5); p.lineTo(4.5, 20); p.lineTo(19.5, 20); p.lineTo(19.5, 15.5);
                    g.draw(p);
                    break;
                case IMPRIMIR:
                    p.moveTo(7, 9); p.lineTo(7, 4); p.lineTo(17, 4); p.lineTo(17, 9);
                    p.moveTo(7, 17); p.lineTo(4, 17); p.lineTo(4, 9); p.lineTo(20, 9); p.lineTo(20, 17); p.lineTo(17, 17);
                    p.moveTo(7, 14); p.lineTo(17, 14); p.lineTo(17, 20.5); p.lineTo(7, 20.5); p.closePath();
                    g.draw(p);
                    break;
                case REACTIVAR:
                    g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 120, 300, Arc2D.OPEN));
                    p.moveTo(12, 3); p.lineTo(12, 10);
                    g.draw(p);
                    break;
                case COPIAR:
                    g.draw(new RoundRectangle2D.Double(8.5, 8.5, 11.5, 11.5, 3, 3));
                    p.moveTo(15.5, 8.5); p.lineTo(15.5, 5.5); p.quadTo(15.5, 4, 14, 4); p.lineTo(5.5, 4);
                    p.quadTo(4, 4, 4, 5.5); p.lineTo(4, 14); p.quadTo(4, 15.5, 5.5, 15.5); p.lineTo(8.5, 15.5);
                    g.draw(p);
                    break;
                case LLAVE:
                    g.draw(new Ellipse2D.Double(3.5, 9.5, 8, 8));
                    p.moveTo(11.5, 13.5); p.lineTo(20.5, 13.5); p.lineTo(20.5, 17); p.moveTo(17, 13.5); p.lineTo(17, 16.5);
                    g.draw(p);
                    break;
                case CANDADO:
                    g.draw(new RoundRectangle2D.Double(5, 10.5, 14, 10, 3, 3));
                    p.moveTo(8, 10.5); p.lineTo(8, 7.5); p.quadTo(8, 3.5, 12, 3.5); p.quadTo(16, 3.5, 16, 7.5); p.lineTo(16, 10.5);
                    g.draw(p);
                    break;
                case OK:
                    g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                    p.moveTo(8, 12.3); p.lineTo(10.8, 15); p.lineTo(16, 9.5);
                    g.draw(p);
                    break;
                case ALERTA:
                    p.moveTo(12, 3.8); p.lineTo(21, 19.5); p.lineTo(3, 19.5); p.closePath();
                    p.moveTo(12, 9.5); p.lineTo(12, 13.5);
                    g.draw(p);
                    g.fill(new Ellipse2D.Double(10.9, 15.6, 2.2, 2.2));
                    break;
                default:
                    break;
            }
        }
    }
}
