package sihca.datos;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Copia en memoria de un conjunto de resultados devuelto por un procedimiento
 * almacenado (nombres de columna + filas). Se desconecta del ResultSet para
 * poder mostrarse en tablas, exportarse o recorrerse libremente.
 */
public final class Resultado {

    private final String[] columnas;
    private final Class<?>[] tipos;
    private final List<Object[]> filas;

    private Resultado(String[] columnas, Class<?>[] tipos, List<Object[]> filas) {
        this.columnas = columnas;
        this.tipos = tipos;
        this.filas = filas;
    }

    public static Resultado vacio() {
        return new Resultado(new String[0], new Class<?>[0], Collections.emptyList());
    }

    /** Lee todas las filas de un ResultSet. */
    public static Resultado desde(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        String[] cols = new String[n];
        Class<?>[] tipos = new Class<?>[n];
        for (int i = 0; i < n; i++) {
            cols[i] = md.getColumnLabel(i + 1);
            tipos[i] = Object.class;
        }
        List<Object[]> filas = new ArrayList<>();
        while (rs.next()) {
            Object[] f = new Object[n];
            for (int i = 0; i < n; i++) {
                Object v = rs.getObject(i + 1);
                if (v instanceof BigDecimal) {
                    BigDecimal bd = (BigDecimal) v;
                    v = bd.scale() <= 0 ? (Object) bd.longValue() : (Object) bd.doubleValue();
                } else if (v instanceof Integer || v instanceof Short || v instanceof Byte) {
                    v = ((Number) v).longValue();
                } else if (v instanceof Boolean) {
                    v = ((Boolean) v) ? 1L : 0L;
                }
                f[i] = v;
                if (v != null && tipos[i] == Object.class) {
                    tipos[i] = v.getClass();
                }
            }
            filas.add(f);
        }
        return new Resultado(cols, tipos, filas);
    }

    public int filas() { return filas.size(); }
    public int columnas() { return columnas.length; }
    public String columna(int i) { return columnas[i]; }
    public Object valor(int fila, int col) { return filas.get(fila)[col]; }
    public List<Object[]> datos() { return filas; }

    public String texto(int fila, int col) {
        Object v = valor(fila, col);
        return v == null ? "" : v.toString();
    }

    public long numero(int fila, int col) {
        Object v = valor(fila, col);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    /** Modelo de solo lectura para mostrar el resultado en un JTable. */
    public AbstractTableModel modelo() {
        return new AbstractTableModel() {
            @Override public int getRowCount() { return filas.size(); }
            @Override public int getColumnCount() { return columnas.length; }
            @Override public String getColumnName(int c) { return columnas[c]; }
            @Override public Object getValueAt(int r, int c) { return filas.get(r)[c]; }
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                Class<?> t = tipos[c];
                return Number.class.isAssignableFrom(t) ? t : String.class;
            }
        };
    }
}
