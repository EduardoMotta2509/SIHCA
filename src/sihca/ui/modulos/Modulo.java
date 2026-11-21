package sihca.ui.modulos;

import java.awt.BorderLayout;
import java.awt.Cursor;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tabla;

/**
 * Clase base de todos los módulos (pantallas) del aplicativo.
 * Define el título, la descripción, el ícono del menú y la recarga de datos.
 */
public abstract class Modulo extends JPanel {

    /** Operación contra la base de datos que puede fallar. */
    protected interface OperacionBD {
        void ejecutar() throws Exception;
    }

    protected final VentanaPrincipal ventana;

    protected Modulo(VentanaPrincipal ventana) {
        super(new BorderLayout(0, 14));
        this.ventana = ventana;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    public abstract String titulo();

    public abstract String descripcion();

    public abstract Iconos.Tipo icono();

    /** Texto del módulo en el menú lateral (por defecto, el título). */
    public String nombreMenu() {
        return titulo();
    }

    /** Vuelve a leer los datos desde la base de datos. */
    public abstract void refrescar();

    /** ¿Las acciones de este módulo dependen del periodo de trabajo? */
    public boolean dependeDelPeriodo() {
        return false;
    }

    /** Ejecuta una operación mostrando el cursor de espera y los errores en un cuadro de mensaje. */
    protected boolean ejecutar(OperacionBD op) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            op.ejecutar();
            return true;
        } catch (Exception ex) {
            Mensajes.error(this, ex);
            return false;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    /** Verifica que haya una fila seleccionada en la tabla. */
    protected boolean haySeleccion(Tabla t, String elemento) {
        if (t.filaSeleccionada() < 0) {
            Mensajes.advertencia(this, "Seleccione " + elemento,
                    "Primero haga clic en una fila de la tabla para seleccionar " + elemento + ".");
            return false;
        }
        return true;
    }

    protected Boton boton(String texto, Iconos.Tipo icono, Boton.Estilo estilo) {
        java.awt.Color c = estilo == Boton.Estilo.PRIMARIO || estilo == Boton.Estilo.PELIGRO
                ? java.awt.Color.WHITE : estilo == Boton.Estilo.FANTASMA ? Tema.PRIMARIO : Tema.TEXTO;
        return new Boton(texto, icono == null ? null : Iconos.de(icono, 16, c), estilo);
    }

    /** Botón que solo se habilita para perfiles con permiso de escritura. */
    protected Boton botonEscritura(String texto, Iconos.Tipo icono, Boton.Estilo estilo) {
        Boton b = boton(texto, icono, estilo);
        if (!Sesion.puedeEscribir()) {
            b.setEnabled(false);
            b.setToolTipText("Su perfil (Consulta) solo permite ver información.");
        }
        return b;
    }

    /** Barra superior con controles a la izquierda y acciones a la derecha. */
    protected JPanel barra(JPanel izquierda, JPanel derecha) {
        JPanel b = new JPanel(new BorderLayout(12, 0));
        b.setOpaque(false);
        if (izquierda != null) {
            b.add(izquierda, BorderLayout.WEST);
        }
        if (derecha != null) {
            b.add(derecha, BorderLayout.EAST);
        }
        return b;
    }
}
