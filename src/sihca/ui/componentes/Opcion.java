package sihca.ui.componentes;

/** Elemento de un combo: guarda un valor (clave) y el texto que se muestra. */
public final class Opcion {

    private final Object valor;
    private final String texto;

    public Opcion(Object valor, String texto) {
        this.valor = valor;
        this.texto = texto;
    }

    public Object valor() {
        return valor;
    }

    public String texto() {
        return texto;
    }

    @Override
    public String toString() {
        return texto;
    }
}
