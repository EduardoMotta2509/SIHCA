package sihca.ui.modulos;

import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import sihca.dao.UsuarioDAO;
import sihca.datos.Resultado;
import sihca.ui.Tema;
import sihca.ui.VentanaPrincipal;
import sihca.ui.componentes.Boton;
import sihca.ui.componentes.Formulario;
import sihca.ui.componentes.Iconos;
import sihca.ui.componentes.Mensajes;
import sihca.ui.componentes.Tabla;
import sihca.ui.componentes.Tarjeta;
import sihca.ui.componentes.UI;

/** Módulo Usuarios (solo Administrador): cuentas de acceso y sus perfiles. */
public class PanelUsuarios extends Modulo {

    private static final String[] ROLES = {"Administrador", "Coordinador", "Consulta"};
    private final Tabla tabla = new Tabla();

    public PanelUsuarios(VentanaPrincipal v) {
        super(v);
        Boton nuevo = boton("Nuevo usuario", Iconos.Tipo.MAS, Boton.Estilo.PRIMARIO);
        Boton editar = boton("Modificar", Iconos.Tipo.EDITAR, Boton.Estilo.SECUNDARIO);
        Boton clave = boton("Restablecer contraseña", Iconos.Tipo.LLAVE, Boton.Estilo.SECUNDARIO);
        Boton desactivar = boton("Desactivar", Iconos.Tipo.ELIMINAR, Boton.Estilo.SECUNDARIO);
        nuevo.addActionListener(e -> nuevo());
        editar.addActionListener(e -> editar());
        clave.addActionListener(e -> clave());
        desactivar.addActionListener(e -> desactivar());
        add(barra(null, UI.filaDerecha(8, desactivar, clave, editar, nuevo)), BorderLayout.NORTH);
        Tarjeta t = new Tarjeta(new BorderLayout());
        t.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));
        t.add(tabla.enScroll(), BorderLayout.CENTER);
        add(t, BorderLayout.CENTER);

        Tarjeta ayuda = new Tarjeta(new BorderLayout(12, 0)).colores(Tema.PRIMARIO_SUAVE, null);
        ayuda.add(new javax.swing.JLabel(Iconos.de(Iconos.Tipo.CANDADO, 22, Tema.PRIMARIO)), BorderLayout.WEST);
        ayuda.add(UI.texto("<html><b>Administrador</b>: todo, incluida la gestión de usuarios.  <b>Coordinador</b>: registra "
                + "y modifica horarios y catálogos.  <b>Consulta</b>: solo visualiza y genera reportes. Las contraseñas "
                + "se guardan cifradas con SHA-256.</html>", 13f, Tema.TEXTO), BorderLayout.CENTER);
        add(ayuda, BorderLayout.SOUTH);
    }

    @Override public String titulo() { return "Usuarios del sistema"; }
    @Override public String descripcion() { return "Cuentas de acceso, perfiles y estado (solo Administrador)"; }
    @Override public Iconos.Tipo icono() { return Iconos.Tipo.USUARIOS; }

    @Override
    public void refrescar() {
        ejecutar(() -> {
            tabla.mostrar(UsuarioDAO.listar());
            ventana.estado(tabla.getRowCount() + " cuentas  ·  sp_usuario_listar");
        });
    }

    private void nuevo() {
        JTextField u = UI.campo(15);
        JTextField n = UI.campo(30);
        JPasswordField c = UI.campoClave(15);
        JComboBox<String> rol = UI.combo(ROLES);
        rol.setSelectedItem("Consulta");
        if (new Formulario(this, "Nuevo usuario", null, "Crear usuario")
                .campo("Usuario", u, "3 a 30 caracteres: letras minúsculas, números, punto o guion bajo")
                .campo("Nombre completo", n)
                .campo("Contraseña inicial", c, "Mínimo 6 caracteres")
                .campo("Perfil", rol)
                .mostrar(() -> UsuarioDAO.insertar(u.getText(), new String(c.getPassword()), n.getText(),
                        (String) rol.getSelectedItem()))) {
            refrescar();
        }
    }

    private void editar() {
        if (!haySeleccion(tabla, "un usuario")) {
            return;
        }
        Resultado r = tabla.resultado();
        int f = tabla.filaSeleccionada();
        String u = r.texto(f, 0);
        JTextField n = UI.campo(30);
        n.setText(r.texto(f, 1));
        JComboBox<String> rol = UI.combo(ROLES);
        rol.setSelectedItem(r.texto(f, 2));
        JCheckBox activa = UI.casilla("Cuenta activa");
        activa.setSelected("Activa".equals(r.texto(f, 3)));
        if (new Formulario(this, "Modificar usuario «" + u + "»", null, "Guardar cambios")
                .campo("Nombre completo", n)
                .campo("Perfil", rol)
                .campo("Estado", activa)
                .mostrar(() -> UsuarioDAO.actualizar(u, n.getText(), (String) rol.getSelectedItem(), activa.isSelected()))) {
            refrescar();
        }
    }

    private void clave() {
        if (!haySeleccion(tabla, "un usuario")) {
            return;
        }
        String u = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
        JPasswordField c = UI.campoClave(15);
        if (new Formulario(this, "Restablecer contraseña de «" + u + "»", null, "Restablecer")
                .campo("Nueva contraseña", c, "Mínimo 6 caracteres")
                .mostrar(() -> UsuarioDAO.cambiarClave(u, new String(c.getPassword())))) {
            Mensajes.exito(this, "Contraseña restablecida", "La contraseña de «" + u + "» fue actualizada.");
        }
    }

    private void desactivar() {
        if (!haySeleccion(tabla, "un usuario")) {
            return;
        }
        String u = tabla.resultado().texto(tabla.filaSeleccionada(), 0);
        if (Mensajes.confirmar(this, "Desactivar usuario", "¿Desactivar la cuenta «" + u + "»? No podrá iniciar sesión "
                + "(borrado lógico; se puede reactivar con Modificar).", "Desactivar")) {
            if (ejecutar(() -> UsuarioDAO.desactivar(u))) {
                refrescar();
            }
        }
    }
}
