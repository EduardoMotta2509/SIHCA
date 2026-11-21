package sihca.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * Identidad visual del aplicativo: paleta de colores, tipografía y ajustes
 * globales de Swing. Solo usa las clases estándar de Java (sin librerías de
 * apariencia externas). La fuente Fira Sans (licencia SIL OFL) viaja dentro
 * del .jar para que el sistema se vea igual en cualquier computadora.
 */
public final class Tema {

    // Paleta institucional
    public static final Color AZUL_OSCURO = new Color(0x14213D);
    public static final Color AZUL_MEDIO = new Color(0x22335A);
    public static final Color PRIMARIO = new Color(0x2457C5);
    public static final Color PRIMARIO_HOVER = new Color(0x1D47A3);
    public static final Color PRIMARIO_SUAVE = new Color(0xE8EFFC);
    public static final Color DORADO = new Color(0xE3A82B);
    public static final Color FONDO = new Color(0xF3F5F9);
    public static final Color SUPERFICIE = Color.WHITE;
    public static final Color BORDE = new Color(0xDDE2EB);
    public static final Color BORDE_FUERTE = new Color(0xC3CAD7);
    public static final Color TEXTO = new Color(0x1C2333);
    public static final Color TEXTO_SUAVE = new Color(0x5F6B7E);
    public static final Color TEXTO_TENUE = new Color(0x8A94A6);
    public static final Color EXITO = new Color(0x17804F);
    public static final Color EXITO_SUAVE = new Color(0xE3F4EB);
    public static final Color PELIGRO = new Color(0xC2362F);
    public static final Color PELIGRO_SUAVE = new Color(0xFBEAE9);
    public static final Color ADVERTENCIA = new Color(0xA86A0B);
    public static final Color ADVERTENCIA_SUAVE = new Color(0xFDF3DF);
    public static final Color FILA_ALTERNA = new Color(0xF8F9FC);
    public static final Color SELECCION = new Color(0xDCE7FB);

    private static Font regular;
    private static Font medium;
    private static Font semibold;
    private static Font bold;

    private Tema() {
    }

    public static Font normal(float tam) { return regular.deriveFont(tam); }
    public static Font medio(float tam) { return medium.deriveFont(tam); }
    public static Font semi(float tam) { return semibold.deriveFont(tam); }
    public static Font negrita(float tam) { return bold.deriveFont(tam); }

    /** Carga las fuentes y configura la apariencia de Swing. Debe llamarse en el hilo de eventos. */
    public static void instalar() {
        regular = cargar("FiraSans-Regular.ttf", Font.PLAIN);
        medium = cargar("FiraSans-Medium.ttf", Font.PLAIN);
        semibold = cargar("FiraSans-SemiBold.ttf", Font.BOLD);
        bold = cargar("FiraSans-Bold.ttf", Font.BOLD);

        MetalLookAndFeel.setCurrentTheme(new TemaMetal());
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException ex) {
            // Metal siempre está disponible en Java estándar
        }
        Font base = normal(13f);
        FontUIResource f = new FontUIResource(base);
        for (Object k : UIManager.getLookAndFeelDefaults().keySet().toArray()) {
            if (k.toString().endsWith(".font")) {
                UIManager.put(k, f);
            }
        }
        UIManager.put("Button.font", new FontUIResource(medio(13f)));
        UIManager.put("TableHeader.font", new FontUIResource(semi(12.5f)));
        UIManager.put("Panel.background", new ColorUIResource(FONDO));
        UIManager.put("OptionPane.background", new ColorUIResource(SUPERFICIE));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(TEXTO));
        UIManager.put("OptionPane.messageFont", new FontUIResource(normal(13.5f)));
        UIManager.put("OptionPane.buttonFont", new FontUIResource(medio(13f)));
        UIManager.put("TextField.margin", new java.awt.Insets(4, 6, 4, 6));
        UIManager.put("ToolTip.background", new ColorUIResource(AZUL_OSCURO));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.background", new ColorUIResource(FONDO));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(BORDE_FUERTE));
        UIManager.put("ScrollBar.thumbHighlight", new ColorUIResource(BORDE_FUERTE));
        UIManager.put("ScrollBar.thumbShadow", new ColorUIResource(BORDE_FUERTE));
        UIManager.put("ScrollBar.thumbDarkShadow", new ColorUIResource(BORDE_FUERTE));
        UIManager.put("ScrollBar.track", new ColorUIResource(FONDO));
        UIManager.put("ScrollBar.trackHighlight", new ColorUIResource(FONDO));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(SELECCION));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(TEXTO));
        UIManager.put("ComboBox.background", new ColorUIResource(SUPERFICIE));
        UIManager.put("List.selectionBackground", new ColorUIResource(SELECCION));
        UIManager.put("List.selectionForeground", new ColorUIResource(TEXTO));
        UIManager.put("CheckBox.background", new ColorUIResource(FONDO));
        UIManager.put("ProgressBar.foreground", new ColorUIResource(PRIMARIO));
        UIManager.put("ProgressBar.selectionBackground", new ColorUIResource(TEXTO));
        UIManager.put("ProgressBar.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("SplitPane.background", new ColorUIResource(FONDO));
        UIManager.put("SplitPaneDivider.draggingColor", new ColorUIResource(BORDE));
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        UIManager.put("Spinner.font", f);
    }

    private static Font cargar(String archivo, int estiloRespaldo) {
        try (InputStream in = Tema.class.getResourceAsStream("/fuentes/" + archivo)) {
            if (in != null) {
                Font fnt = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(fnt);
                return fnt.deriveFont(13f);
            }
        } catch (IOException | FontFormatException ex) {
            // se usa la fuente del sistema
        }
        return new Font(Font.SANS_SERIF, estiloRespaldo, 13);
    }

    /** Tema Metal con la paleta y tipografía del aplicativo. */
    private static final class TemaMetal extends DefaultMetalTheme {
        @Override public String getName() { return "SIHCA"; }
        @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(PRIMARIO); }
        @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(BORDE_FUERTE); }
        @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(SELECCION); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(BORDE_FUERTE); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(BORDE); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(FONDO); }
        @Override public ColorUIResource getWindowBackground() { return new ColorUIResource(SUPERFICIE); }
        @Override public ColorUIResource getControlTextColor() { return new ColorUIResource(TEXTO); }
        @Override public ColorUIResource getUserTextColor() { return new ColorUIResource(TEXTO); }
        @Override public ColorUIResource getSystemTextColor() { return new ColorUIResource(TEXTO); }
        @Override public FontUIResource getControlTextFont() { return new FontUIResource(normal(13f)); }
        @Override public FontUIResource getUserTextFont() { return new FontUIResource(normal(13f)); }
        @Override public FontUIResource getMenuTextFont() { return new FontUIResource(normal(13f)); }
        @Override public FontUIResource getSystemTextFont() { return new FontUIResource(normal(13f)); }
        @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(semi(13f)); }
        @Override public FontUIResource getSubTextFont() { return new FontUIResource(normal(11.5f)); }
    }
}
