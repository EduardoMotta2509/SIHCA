package sihca.ui.modulos;

import java.awt.BorderLayout;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import sihca.dao.PeriodoDAO;
import sihca.datos.Resultado;
import sihca.datos.Sesion;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Opcion;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/**
 * Módulo Periodos académicos. Un periodo cerrado conserva su horario como
 * registro histórico de solo lectura; puede usarse como base del año siguiente.
 */
public class PanelPeriodos extends Modulo {

    private final Tabla tabla = new Tabla();

    public PanelPeriodos(VentanaPrincipal v) {
        super(v);
        Boton nuevo = botonEscritura("Nuevo periodo", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton estado = botonEscritura("Cerrar / reabrir", Iconos.Tipo.CANDADO, Boton.Estilo.SECUNDARIO);
        Boton replicar = botonEscritura("Reutilizar horario de otro año", Iconos.Tipo.COPIAR, Boton.Estilo.SECUNDARIO);
        Boton eliminar = botonEscritura("Eliminar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        nuevo.addActionListener(e -> nuevo());
        estado.addActionListener(e -> cambiarEstado());
        replicar.addActionListener(e -> replicar());
        eliminar.addActionListener(e -> eliminar());
        add(barra(null, UI.filaDerecha(8, eliminar, replicar, estado, nuevo)), BorderLayout.NORTH);

        Tarjeta t = new Tarjeta(new BorderLayout(0, 0));
        t.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(tabla.enScroll(), BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);

        Tarjeta ayuda = new Tarjeta(new BorderLayout(12, 0)).colores(Tema.PRIMARIO_SUAVE, null);
        ayuda.add(new javax.swing.JLabel(Iconos.de(Iconos.Tipo.CANDADO, 22, Tema.PRIMARIO)), BorderLayout.WEST);
        ayuda.add(UI.texto("<html>Al <b>cerrar</b> un periodo, sus horarios quedan como <b>registro histórico</b>: los "
                + "triggers impiden modificarlos o borrarlos. Para preparar un nuevo año, cree el periodo y use "
                + "<b>Reutilizar horario de otro año</b>: se copian los bloques y secciones, y los docentes vigentes.</html>",
                13f, Tema.TEXTO), BorderLayout.CENTER);
        add(ayuda, BorderLayout.SOUTH);
    }

    @Override public String titulo() { return "Periodos académicos"; }
    @Override public String descripcion() { return "Años lectivos: el periodo activo se edita; los cerrados forman el registro histórico"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.PERIODO; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            tabla.mostrar(PeriodoDAO.listar());
            ventana.estado(tabla.getRowCount() + " periodos  ·  sp_periodo_listar");
        });
    }

    private void cambios() {
        refrescar();
        ventana.cargarPeriodos();
    }

    private void nuevo() {
        JSpinner anio = UI.numero(java.time.Year.now().getValue() + 1, 2020, 2100);
        JComboBox<String> est = UI.combo("Activo", "Cerrado");
        if (new Formulario(this, "Nuevo periodo académico", null, "Registrar periodo")
                .campo("Año lectivo", anio)
                .campo("Estado inicial", est)
                .mostrar(() -> PeriodoDAO.insertar((Integer) anio.getValue(), (String) est.getSelectedItem()))) {
            cambios();
        }
    }

    private void cambiarEstado() {
        if (!haySeleccion(tabla, "un periodo")) {
            return;
        }
        Resultado r = tabla.resultado();
        int f = tabla.filaSeleccionada();
        int anio = (int) r.numero(f, 0);
        boolean cerrar = "Activo".equals(r.texto(f, 1));
        String msg = cerrar
                ? "¿Cerrar el periodo " + anio + "? Su horario quedará como registro histórico de solo lectura."
                : "¿Reabrir el periodo " + anio + "? Su horario podrá volver a modificarse.";
        if (Mensajes.confirmar(this, cerrar ? "Cerrar periodo" : "Reabrir periodo", msg, cerrar ? "Cerrar periodo" : "Reabrir")) {
            if (ejecutar(() -> PeriodoDAO.actualizar(anio, cerrar ? "Cerrado" : "Activo"))) {
                cambios();
            }
        }
    }

    private void replicar() {
        JComboBox<Opcion> origen = UI.combo();
        JComboBox<Opcion> destino = UI.combo();
        Resultado r = tabla.resultado();
        for (int i = 0; i < r.filas(); i++) {
            String t = r.texto(i, 0) + "  (" + r.texto(i, 1) + ", " + r.texto(i, 2) + " bloques)";
            if (r.numero(i, 2) > 0) {
                origen.addItem(new Opcion(r.numero(i, 0), t));
            }
            if (r.numero(i, 2) == 0 && "Activo".equals(r.texto(i, 1))) {
                destino.addItem(new Opcion(r.numero(i, 0), t));
            }
        }
        if (destino.getItemCount() == 0) {
            Mensajes.advertencia(this, "No hay un periodo destino",
                    "Primero cree un periodo nuevo (Activo y sin horario) para copiar en él el horario de otro año.");
            return;
        }
        final int[] res = new int[2];
        if (new Formulario(this, "Reutilizar horario de otro año",
                "Se copiarán todos los bloques y secciones del año de origen. Los docentes se asignan solo si siguen "
                        + "vigentes y conservan la especialidad; los demás bloques quedan «sin docente» para reasignar.",
                "Copiar horario")
                .campo("Copiar desde", origen)
                .campo("Hacia el periodo", destino)
                .mostrar(() -> {
                    int[] x = PeriodoDAO.replicarHorario(((Number) UI.valor(origen)).intValue(),
                            ((Number) UI.valor(destino)).intValue());
                    res[0] = x[0];
                    res[1] = x[1];
                })) {
            cambios();
            Mensajes.exito(this, "Horario copiado", "Se copiaron " + res[0] + " bloques horarios. "
                    + (res[1] > 0 ? res[1] + " bloques quedaron sin docente asignado." : "Todos tienen docente asignado."));
        }
    }

    private void eliminar() {
        if (!haySeleccion(tabla, "un periodo")) {
            return;
        }
        int anio = (int) tabla.resultado().numero(tabla.filaSeleccionada(), 0);
        if (Mensajes.confirmar(this, "Eliminar periodo", "¿Eliminar el periodo " + anio + "? Solo es posible si aún no "
                + "tiene horario registrado.", "Eliminar")) {
            if (ejecutar(() -> PeriodoDAO.eliminar(anio))) {
                if (Sesion.periodo() == anio) {
                    Sesion.setPeriodo((short) 0, false);
                }
                cambios();
            }
        }
    }
}
