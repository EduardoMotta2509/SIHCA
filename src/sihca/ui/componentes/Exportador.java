package sihca.ui.componentes;

import java.awt.Component;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import sihca.datos.Resultado;

/** Exportación de resultados a CSV (compatible con Excel) e impresión de tablas. */
public final class Exportador {

    private Exportador() {
    }

    /** Guarda el resultado como CSV separado por punto y coma, en UTF-8 con BOM (Excel en español). */
    public static void csv(Component padre, Resultado r, String nombreSugerido) {
        if (r == null || r.filas() == 0) {
            Mensajes.advertencia(padre, "Nada que exportar", "Primero genere un resultado con datos.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar a Excel (CSV)");
        fc.setFileFilter(new FileNameExtensionFilter("Archivo CSV (Excel)", "csv"));
        fc.setSelectedFile(new File(nombreSugerido.replaceAll("[^\\p{L}0-9_ -]", "") + ".csv"));
        if (fc.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) {
            f = new File(f.getParentFile(), f.getName() + ".csv");
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write('﻿');
            for (int c = 0; c < r.columnas(); c++) {
                w.write((c > 0 ? ";" : "") + celda(r.columna(c)));
            }
            w.write("\r\n");
            for (int i = 0; i < r.filas(); i++) {
                for (int c = 0; c < r.columnas(); c++) {
                    w.write((c > 0 ? ";" : "") + celda(r.texto(i, c)));
                }
                w.write("\r\n");
            }
            Mensajes.exito(padre, "Archivo exportado", "Se guardaron " + r.filas() + " filas en:\n" + f.getAbsolutePath());
        } catch (IOException ex) {
            Mensajes.error(padre, "No se pudo exportar", ex.getMessage());
        }
    }

    private static String celda(String s) {
        String v = s == null ? "" : s;
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Imprime la tabla (permite elegir "Microsoft Print to PDF" para obtener un PDF). */
    public static void imprimir(Component padre, JTable tabla, String titulo) {
        if (tabla.getRowCount() == 0) {
            Mensajes.advertencia(padre, "Nada que imprimir", "Primero genere un resultado con datos.");
            return;
        }
        try {
            tabla.print(JTable.PrintMode.FIT_WIDTH, new MessageFormat(titulo),
                    new MessageFormat("SIHCA - Colegio Esclavas del Sagrado Corazón de Jesús   |   Página {0}"));
        } catch (PrinterException ex) {
            Mensajes.error(padre, "No se pudo imprimir", ex.getMessage());
        }
    }
}
