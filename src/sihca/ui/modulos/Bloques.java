package sihca.ui.modulos;

/** Franjas horarias estándar de la jornada escolar (bloques pedagógicos de 45 minutos). */
final class Bloques {

    static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};

    /** {inicio, fin, descripción} */
    static final String[][] CLASES = {
        {"07:40", "08:25", "1.ª hora"}, {"08:25", "09:10", "2.ª hora"}, {"09:10", "09:55", "3.ª hora"},
        {"09:55", "10:40", "4.ª hora"}, {"11:00", "11:45", "5.ª hora"}, {"11:45", "12:30", "6.ª hora"},
        {"13:15", "14:00", "7.ª hora"}, {"14:00", "14:45", "8.ª hora"}, {"14:45", "15:30", "9.ª hora"},
    };

    static final String[][] NO_LECTIVOS = {
        {"10:40", "11:00", "Recreo"}, {"12:30", "13:15", "Almuerzo"},
    };

    private Bloques() {
    }
}
