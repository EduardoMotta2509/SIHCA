package sihca.ui.componentes;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import sihca.ui.Tema;

/** Botón plano con esquinas redondeadas y cuatro estilos. */
public class Boton extends JButton {

    public enum Estilo { PRIMARIO, SECUNDARIO, PELIGRO, FANTASMA }

    private final Estilo estilo;
    private boolean encima;

    public Boton(String texto, Estilo estilo) {
        this(texto, null, estilo);
    }

    public Boton(String texto, Icon icono, Estilo estilo) {
        super(texto, icono);
        this.estilo = estilo;
        setFont(Tema.medio(13f));
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setIconTextGap(6);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        setForeground(colorTexto());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { encima = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { encima = false; repaint(); }
        });
    }

    public static Boton primario(String t) { return new Boton(t, Estilo.PRIMARIO); }
    public static Boton secundario(String t) { return new Boton(t, Estilo.SECUNDARIO); }
    public static Boton peligro(String t) { return new Boton(t, Estilo.PELIGRO); }
    public static Boton fantasma(String t) { return new Boton(t, Estilo.FANTASMA); }

    private Color colorTexto() {
        switch (estilo) {
            case PRIMARIO:
            case PELIGRO:
                return Color.WHITE;
            case FANTASMA:
                return Tema.PRIMARIO;
            default:
                return Tema.TEXTO;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 34);
        return d;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        boolean activo = isEnabled();
        Color fondo = null;
        Color borde = null;
        switch (estilo) {
            case PRIMARIO:
                fondo = !activo ? Tema.BORDE_FUERTE : encima ? Tema.PRIMARIO_HOVER : Tema.PRIMARIO;
                break;
            case PELIGRO:
                fondo = !activo ? Tema.BORDE_FUERTE : encima ? Tema.PELIGRO.darker() : Tema.PELIGRO;
                break;
            case SECUNDARIO:
                fondo = encima && activo ? Tema.FONDO : Tema.SUPERFICIE;
                borde = Tema.BORDE_FUERTE;
                break;
            default:
                fondo = encima && activo ? Tema.PRIMARIO_SUAVE : null;
                break;
        }
        if (fondo != null) {
            g2.setColor(fondo);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        }
        if (borde != null) {
            g2.setColor(borde);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        }
        g2.dispose();
        setForeground(activo ? colorTexto() : (estilo == Estilo.SECUNDARIO || estilo == Estilo.FANTASMA
                ? Tema.TEXTO_TENUE : Color.WHITE));
        super.paintComponent(g);
    }
}
