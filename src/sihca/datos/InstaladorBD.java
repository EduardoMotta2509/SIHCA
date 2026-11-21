package sihca.datos;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Crea (o recrea) la base de datos ejecutando el script SQL completo
 * (tablas, índices, funciones, triggers, procedimientos y datos de prueba).
 * <p>
 * Interpreta la directiva {@code DELIMITER} del cliente mysql, de modo que el
 * mismo archivo sirve para MySQL Workbench y para este instalador.
 */
public final class InstaladorBD {

    public static final String ARCHIVO_SCRIPT = "script_completo_bd_horarios_esclavas.sql";

    /** Recibe el avance de la instalación. */
    public interface Avance {
        void informar(int actual, int total, String descripcion);
    }

    private InstaladorBD() {
    }

    /** Lee el script desde la carpeta sql/ o, si no está, desde el propio .jar. */
    public static String leerScript() throws IOException {
        File f = new File(RutasApp.carpetaSql(), ARCHIVO_SCRIPT);
        try (InputStream in = f.isFile() ? new FileInputStream(f)
                : InstaladorBD.class.getResourceAsStream("/sql/" + ARCHIVO_SCRIPT)) {
            if (in == null) {
                throw new IOException("No se encontró el script " + ARCHIVO_SCRIPT);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return s.startsWith("﻿") ? s.substring(1) : s;
        }
    }

    /** Ejecuta el script completo sobre el servidor indicado. */
    public static void instalar(ConfigConexion cfg, Avance avance) throws SQLException, IOException {
        String script = leerScript();
        if (!"bd_horarios_esclavas".equals(cfg.getBaseDatos())) {
            script = script.replace("bd_horarios_esclavas", cfg.getBaseDatos());
        }
        List<String> sentencias = dividir(script);
        Conexion.cerrar();
        try (Connection cn = Conexion.abrir(cfg, false); Statement st = cn.createStatement()) {
            for (int i = 0; i < sentencias.size(); i++) {
                String s = sentencias.get(i);
                if (avance != null) {
                    avance.informar(i + 1, sentencias.size(), resumen(s));
                }
                try {
                    st.execute(s);
                } catch (SQLException ex) {
                    throw new SQLException("Error en la sentencia " + (i + 1) + " (" + resumen(s) + "): "
                            + ex.getMessage(), ex.getSQLState(), ex.getErrorCode(), ex);
                }
            }
        }
    }

    /** Descripción corta de una sentencia para la barra de progreso. */
    private static String resumen(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        String[] p = t.split(" ");
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < Math.min(p.length, 4); i++) {
            r.append(p[i]).append(' ');
        }
        return r.toString().trim();
    }

    /**
     * Divide un script en sentencias respetando cadenas, comentarios y la
     * directiva DELIMITER (necesaria para triggers y procedimientos).
     */
    public static List<String> dividir(String script) {
        List<String> res = new ArrayList<>();
        String delim = ";";
        StringBuilder actual = new StringBuilder();
        int i = 0;
        int n = script.length();
        boolean inicioLinea = true;
        while (i < n) {
            char c = script.charAt(i);
            if (inicioLinea) {
                int j = i;
                while (j < n && (script.charAt(j) == ' ' || script.charAt(j) == '\t')) {
                    j++;
                }
                if (script.regionMatches(true, j, "DELIMITER ", 0, 10) && actual.toString().trim().isEmpty()) {
                    int fin = script.indexOf('\n', j);
                    if (fin < 0) {
                        fin = n;
                    }
                    delim = script.substring(j + 10, fin).trim();
                    i = fin + 1;
                    continue;
                }
                inicioLinea = false;
            }
            // Comentarios de línea:  -- texto   o   # texto
            if ((c == '-' && i + 2 < n && script.charAt(i + 1) == '-'
                    && Character.isWhitespace(script.charAt(i + 2))) || c == '#') {
                int fin = script.indexOf('\n', i);
                i = fin < 0 ? n : fin;
                continue;
            }
            // Comentario de bloque
            if (c == '/' && i + 1 < n && script.charAt(i + 1) == '*') {
                int fin = script.indexOf("*/", i + 2);
                i = fin < 0 ? n : fin + 2;
                continue;
            }
            // Cadenas y nombres entre comillas invertidas
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < n) {
                    char d = script.charAt(j);
                    if (d == '\\' && c != '`') {
                        j += 2;
                        continue;
                    }
                    if (d == c) {
                        if (j + 1 < n && script.charAt(j + 1) == c) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                actual.append(script, i, Math.min(j + 1, n));
                i = j + 1;
                continue;
            }
            if (script.startsWith(delim, i)) {
                String s = actual.toString().trim();
                if (!s.isEmpty()) {
                    res.add(s);
                }
                actual.setLength(0);
                i += delim.length();
                continue;
            }
            if (c == '\n') {
                inicioLinea = true;
            }
            actual.append(c);
            i++;
        }
        String s = actual.toString().trim();
        if (!s.isEmpty()) {
            res.add(s);
        }
        return res;
    }
}
