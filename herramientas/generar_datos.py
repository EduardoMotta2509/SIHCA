"""
Generador del script de datos de prueba (05_datos_prueba.sql).

Construye un horario semanal COMPLETO y VÁLIDO para los años 2025 y 2026
(22 secciones de Primaria y Secundaria) resolviendo un modelo de
programación con restricciones (OR-Tools CP-SAT) que respeta las mismas
reglas que luego validan los triggers de la base de datos:
  - cada sección cumple exactamente las horas de su plan de estudios,
  - ningún docente ni sección tiene cruces,
  - no se excede el límite de grupos de canchas, laboratorios y salas,
  - Consejo Directivo (lunes) y Pastoral (miércoles) bloquean a sus miembros,
  - las asignaturas de exigencia Alta se ubican de preferencia antes de las 10:40.

El script SQL resultante se carga PASANDO POR LOS TRIGGERS, lo que prueba
que los datos cumplen todas las reglas de negocio.

Uso:  python generar_datos.py ../sql/05_datos_prueba.sql
(Herramienta de desarrollo; el aplicativo no la necesita para funcionar.)
"""
import sys
from ortools.sat.python import cp_model

DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"]
BLOQUES = [("07:40", "08:25"), ("08:25", "09:10"), ("09:10", "09:55"), ("09:55", "10:40"),
           ("11:00", "11:45"), ("11:45", "12:30"),
           ("13:15", "14:00"), ("14:00", "14:45"), ("14:45", "15:30")]
N_BLOQUES = {"Secundaria": 9, "Primaria": 8}
RECREO = ("10:40", "11:00")
ALMUERZO = ("12:30", "13:15")

# ------------------------------------------------------------------ catálogos
GRADOS = [(1, "3 Años", "Inicial"), (2, "4 Años", "Inicial"), (3, "5 Años", "Inicial")] + \
         [(10 + i, f"{i}.° Grado", "Primaria") for i in range(1, 7)] + \
         [(20 + i, f"{i}.° Año", "Secundaria") for i in range(1, 6)]
SECCIONES = [(11, "A", 1), (21, "A", 2), (31, "A", 3), (32, "B", 3)]
for g, _, niv in GRADOS:
    if niv != "Inicial":
        SECCIONES += [(g * 10 + 1, "A", g), (g * 10 + 2, "B", g)]
NIVEL_GRADO = {g: n for g, _, n in GRADOS}
NOMBRE_GRADO = {g: nom for g, nom, _ in GRADOS}

ASIGNATURAS = [
    ("Matemática", "Alta"), ("Comunicación", "Alta"), ("Ciencia y Tecnología", "Alta"),
    ("Inglés", "Media"), ("Ciencias Sociales", "Media"),
    ("Desarrollo Personal, Ciudadanía y Cívica", "Media"), ("Personal Social", "Media"),
    ("Educación para el Trabajo", "Media"), ("Educación Religiosa", "Media"),
    ("Educación Física", "Media"), ("Arte y Cultura - Danza", "Baja"),
    ("Arte y Cultura - Música", "Baja"), ("Computación", "Baja"), ("Tutoría", "Baja"),
    ("Psicomotricidad", "Baja"),
]
EXIGENCIA = dict(ASIGNATURAS)
MAT, COM, CYT, ING, CCSS, DPCC, PS, EPT, REL, EF, DAN, MUS, COMP, TUT = [a for a, _ in ASIGNATURAS[:14]]

PLAN = {
    "Secundaria": {MAT: 7, COM: 6, CYT: 6, ING: 5, CCSS: 4, DPCC: 3, EPT: 3, EF: 3,
                   DAN: 2, MUS: 2, REL: 2, TUT: 2},
    "Primaria": {MAT: 8, COM: 8, ING: 4, CYT: 4, PS: 3, EF: 3, MUS: 2, DAN: 2,
                 REL: 2, TUT: 2, COMP: 2},
}
assert sum(PLAN["Secundaria"].values()) == 45 and sum(PLAN["Primaria"].values()) == 40

ESPACIOS = [
    ("Cancha Sintética Principal", "Cancha", 2), ("Losa Deportiva de Primaria", "Cancha", 2),
    ("Ambiente de Música", "Ambiente de música", 1), ("Sala de Danza", "Aula", 1),
    ("Centro de Cómputo Secundaria", "Centro de cómputo", 1),
    ("Centro de Cómputo Primaria", "Centro de cómputo", 1),
    ("Laboratorio de Ciencias", "Laboratorio", 1), ("Laboratorio 2 - Química", "Laboratorio", 1),
    ("Patio Principal", "Patio", 2), ("Patio de Primaria", "Patio", 2), ("Patio Techado", "Patio", 2),
    ("Sala de Consejo Directivo", "Aula", 1), ("Sala de Pastoral", "Aula", 1),
]


def aula(sec_id):
    s = next(x for x in SECCIONES if x[0] == sec_id)
    g = s[2]
    niv = NIVEL_GRADO[g]
    if niv == "Inicial":
        return f"Aula {NOMBRE_GRADO[g]} {s[1]}"
    num = g % 10
    return f"Aula {num}{s[1]} {niv}"


for sid, _, g in SECCIONES:
    ESPACIOS.append((aula(sid), "Aula", 1))


def espacio_fijo(nivel, materia):
    """Espacio por defecto de una asignatura (None = aula de la sección)."""
    if nivel == "Secundaria":
        return {EF: "Cancha Sintética Principal", MUS: "Ambiente de Música", DAN: "Sala de Danza",
                EPT: "Centro de Cómputo Secundaria"}.get(materia)
    return {EF: "Losa Deportiva de Primaria", COMP: "Centro de Cómputo Primaria"}.get(materia)


# ------------------------------------------------------------------ docentes
# (DNI, nombre, modalidad, condición, especialidades)
D = {}


def doc(clave, dni, nombre, modalidad, condicion, esp):
    D[clave] = (dni, nombre, modalidad, condicion, esp)


# Docentes del Entregable 04 (se conservan sus DNI)
doc("CHAMBILLA", "45231678", "Chambilla Quispe, María del Carmen", "Jornada completa", "Miembro del Consejo Directivo", [MAT])
doc("HNA_LUCIA", "29451120", "Hna. Lucía del Corazón de Jesús", "Jornada completa", "Hermana de la Congregación", [REL])
doc("PAREDES", "41889201", "Paredes Villanueva, Carlos Alberto", "Jornada completa", "Tutor", [MAT, TUT])
doc("ZEBALLOS", "43991023", "Zeballos Medina, Roberto", "Jornada completa", "Docente", [EF])
doc("VALDIVIA", "70123456", "Valdivia Carpio, Luciana", "Por horas", "Docente", [DAN])
doc("MENDOZA", "46778899", "Mendoza Rivera, Sofía", "Jornada completa", "Tutor", [COM, TUT])
# Secundaria
doc("M2", "42115588", "Gutiérrez Salas, Javier Enrique", "Jornada completa", "Tutor", [MAT, TUT])
doc("M3", "44670231", "Rodríguez Ccama, Patricia Elena", "Jornada completa", "Tutor", [MAT, TUT])
doc("C1", "40981234", "Benavente Lazo, Rocío del Pilar", "Jornada completa", "Tutor", [COM, TUT])
doc("C2", "45512877", "Delgado Pinto, Miguel Ángel", "Jornada completa", "Docente", [COM])
doc("I1", "47023519", "Fernández Cornejo, Andrea Milagros", "Jornada completa", "Tutor", [ING, TUT])
doc("I2_2025", "41239876", "Torres Begazo, Ana Lucía", "Jornada completa", "Docente", [ING])
doc("I2", "72345610", "Huamaní Ccori, Diego Alonso", "Jornada completa", "Docente", [ING])
doc("I3", "46120983", "Linares Portugal, Karen Sofía", "Jornada completa", "Docente", [ING])
doc("I4", "48876012", "Neyra Bustamante, Lucía Fernanda", "Jornada completa", "Docente", [ING])
doc("CT1", "43345091", "Quispe Huarachi, Edwin Raúl", "Jornada completa", "Tutor", [CYT, TUT])
doc("CT2", "44987120", "Málaga Arenas, Gabriela Inés", "Jornada completa", "Tutor", [CYT, TUT])
doc("CT3", "45678321", "Vargas Llosa, Fernando José", "Jornada completa", "Docente", [CYT])
doc("S1", "40456712", "Cáceres Medina, Jorge Luis", "Jornada completa", "Miembro del Consejo Directivo", [CCSS, DPCC])
doc("S2", "43901276", "Zúñiga Herrera, Carmen Rosa", "Jornada completa", "Tutor", [CCSS, DPCC, TUT])
doc("S3", "46345128", "Pacheco Riveros, Luis Alberto", "Jornada completa", "Tutor", [CCSS, DPCC, TUT])
doc("EPT", "47812390", "Ramos Ticona, Óscar Daniel", "Jornada completa", "Docente", [EPT, COMP])
doc("EF2", "45023987", "Mamani Choque, Juan Carlos", "Jornada completa", "Docente", [EF])
doc("EF3", "48123476", "Coaguila Ponce, Diana Paola", "Jornada completa", "Docente", [EF])
doc("D2", "71234589", "Rivera Alvarado, Melissa", "Por horas", "Docente", [DAN])
doc("MU1", "44123567", "Salinas Begazo, Ricardo Martín", "Jornada completa", "Docente", [MUS])
doc("MU2", "73456128", "Cornejo Vera, Alessandra", "Por horas", "Docente", [MUS])
doc("HNA2", "30123458", "Hna. María Teresa Cárdenas", "Jornada completa", "Hermana de la Congregación", [REL])
doc("REL3", "42789123", "Ortiz Lazarte, Pedro Pablo", "Por horas", "Docente", [REL])
doc("COMP_2025", "40234561", "Salas Quispe, Jorge Luis", "Por horas", "Docente", [COMP])
doc("COMP", "74561230", "Apaza Mamani, Fiorella", "Por horas", "Docente", [COMP])
# Tutores de Primaria (dictan Mat, Com, CyT, Personal Social y Tutoría en su sección)
TUTORES_PRIM = [
    ("P11A", "41567823", "Chávez Rondón, Silvia Mercedes", "Miembro del Consejo Directivo"),
    ("P11B", "42678934", "Herrera Núñez, Mónica Patricia", "Tutor"),
    ("P12A", "43789045", "Tejada Rivera, Liliana", "Tutor"),
    ("P12B", "44890156", "Bustamante Paz, Ruth Noemí", "Tutor"),
    ("P13A", "45901267", "Flores Mendoza, Gloria Isabel", "Tutor"),
    ("P13B", "46012378", "Ponce de León, Verónica", "Tutor"),
    ("P14A", "47123489", "Alarcón Díaz, Teresa Jesús", "Tutor"),
    ("P14B", "48234590", "Lazo Cáceres, Yesenia", "Tutor"),
    ("P15A", "40345601", "Valencia Torres, Rosa Elvira", "Tutor"),
    ("P15B", "41456712", "Prado Quiroz, Jessica Milagros", "Tutor"),
    ("P16A", "42567823", "Medina Gallegos, Elizabeth", "Tutor"),
    ("P16B", "43678934", "Vela Carpio, Juan Manuel", "Tutor"),
]
for clave, dni, nombre, cond in TUTORES_PRIM:
    doc(clave, dni, nombre, "Jornada completa", cond, [MAT, COM, CYT, PS, TUT])
# Docentes registrados sin carga (para probar altas, bajas y la consulta de disponibles)
doc("SUPLENTE", "75123698", "Arenas Zevallos, Claudia Beatriz", "Por horas", "Docente", [MAT, CYT])
doc("SUPLENTE2", "76234519", "Chirinos Lazo, Marco Antonio", "Por horas", "Docente", [COM, ING])

DE_BAJA_FINAL = {"I2_2025", "COMP_2025"}
CONSEJO = [k for k, v in D.items() if v[3] == "Miembro del Consejo Directivo"]
HERMANAS = [k for k, v in D.items() if v[3] == "Hermana de la Congregación"]


def sid(grado, letra):
    return grado * 10 + (1 if letra == "A" else 2)


SEC_SEC = [sid(g, l) for g in range(21, 26) for l in "AB"]
SEC_PRI = [sid(g, l) for g in range(11, 17) for l in "AB"]


def asignaciones(anio):
    """(seccion, materia) -> clave de docente, para cada año."""
    a = {}
    s = SEC_SEC  # 211,212,221,222,231,232,241,242,251,252

    def put(materia, secciones, d):
        for x in secciones:
            a[(x, materia)] = d
    if anio == 2026:
        put(MAT, s[0:3], "PAREDES"); put(MAT, s[3:6], "M2"); put(MAT, s[6:9], "M3"); put(MAT, s[9:], "CHAMBILLA")
        put(COM, s[0:4], "C1"); put(COM, s[4:7], "C2"); put(COM, s[7:], "MENDOZA")
        put(ING, s[0:5], "I1"); put(ING, s[5:], "I2")
        put(CYT, s[0:4], "CT1"); put(CYT, s[4:7], "CT2"); put(CYT, s[7:], "CT3")
        put(CCSS, s[0:4], "S1"); put(CCSS, s[4:8], "S2"); put(CCSS, s[8:], "S3")
        put(DPCC, s[0:2], "S1"); put(DPCC, s[2:4], "S2"); put(DPCC, s[4:], "S3")
        put(EPT, s, "EPT"); put(EF, s[0:4], "ZEBALLOS"); put(EF, s[4:], "EF2")
        put(DAN, s, "VALDIVIA"); put(MUS, s, "MU1"); put(REL, s, "HNA_LUCIA")
        tut = ["PAREDES", "C1", "CT1", "I1", "M2", "S2", "CT2", "MENDOZA", "M3", "S3"]
    else:  # 2025: misma estructura con otra distribución y personal que luego cesó
        put(MAT, s[0:2], "PAREDES"); put(MAT, s[2:5], "M2"); put(MAT, s[5:8], "M3"); put(MAT, s[8:], "CHAMBILLA")
        put(COM, s[0:3], "C1"); put(COM, s[3:7], "C2"); put(COM, s[7:], "MENDOZA")
        put(ING, s[0:4], "I1"); put(ING, s[4:], "I2_2025")
        put(CYT, s[0:3], "CT1"); put(CYT, s[3:6], "CT2"); put(CYT, s[6:], "CT3")
        put(CCSS, s[0:3], "S1"); put(CCSS, s[3:7], "S2"); put(CCSS, s[7:], "S3")
        put(DPCC, s[0:3], "S1"); put(DPCC, s[3:6], "S2"); put(DPCC, s[6:], "S3")
        put(EPT, s, "EPT"); put(EF, s[0:5], "ZEBALLOS"); put(EF, s[5:], "EF2")
        put(DAN, s, "VALDIVIA"); put(MUS, s, "MU1"); put(REL, s, "HNA_LUCIA")
        tut = ["C1", "PAREDES", "M2", "CT1", "I1", "S2", "CT2", "M3", "MENDOZA", "S3"]
    for x, d in zip(s, tut):
        a[(x, TUT)] = d
    p = SEC_PRI
    for i, x in enumerate(p):
        clave = TUTORES_PRIM[i][0]
        for m in (MAT, COM, CYT, PS, TUT):
            a[(x, m)] = clave
    put(ING, p[0:6], "I3"); put(ING, p[6:], "I4")
    if anio == 2026:
        put(EF, p[0:4], "ZEBALLOS"); put(EF, p[4:6], "EF2"); put(EF, p[6:], "EF3")
    else:
        put(EF, p[0:2], "ZEBALLOS"); put(EF, p[2:6], "EF2"); put(EF, p[6:], "EF3")
    put(MUS, p, "MU2"); put(DAN, p, "D2")
    put(REL, p[0:6], "HNA2"); put(REL, p[6:], "REL3")
    put(COMP, p, "COMP" if anio == 2026 else "COMP_2025")
    return a


def nivel_seccion(x):
    g = next(s[2] for s in SECCIONES if s[0] == x)
    return NIVEL_GRADO[g]


def resolver(anio, semilla):
    asig = asignaciones(anio)
    m = cp_model.CpModel()
    x = {}
    lab = {}
    secciones = SEC_SEC + SEC_PRI
    for s in secciones:
        niv = nivel_seccion(s)
        nb = N_BLOQUES[niv]
        for mat, h in PLAN[niv].items():
            assert (s, mat) in asig, (s, mat)
            for d in range(5):
                for b in range(nb):
                    x[s, mat, d, b] = m.NewBoolVar("")
                m.Add(sum(x[s, mat, d, b] for b in range(nb)) <= 2)
            m.Add(sum(x[s, mat, d, b] for d in range(5) for b in range(nb)) == h)
        for d in range(5):
            for b in range(nb):
                m.AddExactlyOne(x[s, mat, d, b] for mat in PLAN[niv])
        if niv == "Secundaria":  # 1 hora semanal de CyT en laboratorio
            for d in range(5):
                for b in range(nb):
                    lab[s, d, b] = m.NewBoolVar("")
                    m.AddImplication(lab[s, d, b], x[s, CYT, d, b])
            m.Add(sum(lab[s, d, b] for d in range(5) for b in range(nb)) == 1)
    # docentes: sin cruces y bloqueos de Consejo (lunes b0-b1) y Pastoral (miércoles b4-b5)
    por_doc = {}
    for (s, mat), dk in asig.items():
        por_doc.setdefault(dk, []).append((s, mat))
    for dk, items in por_doc.items():
        for d in range(5):
            for b in range(9):
                vs = [x[s, mat, d, b] for s, mat in items if (s, mat, d, b) in x]
                if not vs:
                    continue
                bloqueado = (dk in CONSEJO and d == 0 and b in (0, 1)) or \
                            (dk in HERMANAS and d == 2 and b in (4, 5))
                if bloqueado:
                    for v in vs:
                        m.Add(v == 0)
                else:
                    m.AddAtMostOne(vs)
    # espacios compartidos
    lim = {e: l for e, _, l in ESPACIOS}
    for d in range(5):
        for b in range(9):
            uso = {}
            for s in secciones:
                niv = nivel_seccion(s)
                for mat in PLAN[niv]:
                    e = espacio_fijo(niv, mat)
                    if e and (s, mat, d, b) in x:
                        uso.setdefault(e, []).append(x[s, mat, d, b])
            for e, vs in uso.items():
                m.Add(sum(vs) <= lim[e])
            labs = [lab[s, d, b] for s in SEC_SEC if (s, d, b) in lab]
            if anio == 2025:  # dos laboratorios: 1.°-2.° en Ciencias, 3.°-5.° en Química
                m.Add(sum(lab[s, d, b] for s in SEC_SEC[:4]) <= 1)
                m.Add(sum(lab[s, d, b] for s in SEC_SEC[4:]) <= 1)
            else:
                m.Add(sum(labs) <= 1)
    # objetivo: materias de exigencia Alta antes del recreo (bloques 0-3)
    obj = []
    for (s, mat, d, b), v in x.items():
        if EXIGENCIA[mat] == "Alta" and b <= 3:
            obj.append(3 * v)
        if EXIGENCIA[mat] == "Baja" and b >= 6:
            obj.append(v)
    m.Maximize(sum(obj))
    sol = cp_model.CpSolver()
    sol.parameters.max_time_in_seconds = 60
    sol.parameters.num_workers = 8
    sol.parameters.random_seed = semilla
    st = sol.Solve(m)
    assert st in (cp_model.OPTIMAL, cp_model.FEASIBLE), sol.StatusName(st)
    print(f"{anio}: {sol.StatusName(st)} objetivo={sol.ObjectiveValue()}", file=sys.stderr)
    res = []
    for (s, mat, d, b), v in x.items():
        if sol.Value(v):
            niv = nivel_seccion(s)
            e = espacio_fijo(niv, mat) or aula(s)
            if niv == "Secundaria" and mat == CYT and sol.Value(lab[s, d, b]):
                if anio == 2025 and s in SEC_SEC[4:]:
                    e = "Laboratorio 2 - Química"
                else:
                    e = "Laboratorio de Ciencias"
            res.append((d, b, s, mat, e, D[asig[s, mat]][0]))
    res.sort()
    return res


def sql_str(v):
    if v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def insert(tabla, cols, filas, lote=150):
    out = []
    for i in range(0, len(filas), lote):
        parte = filas[i:i + lote]
        out.append(f"INSERT INTO {tabla} ({', '.join(cols)}) VALUES\n" +
                   ",\n".join("(" + ", ".join(sql_str(v) for v in f) + ")" for f in parte) + ";")
    return "\n".join(out)


def recesos_y_bloqueos(anio):
    """Bloques no lectivos: recreos, almuerzos, Consejo y Pastoral, con su personal."""
    act = []  # (dia, ini, fin, naturaleza, espacio, [secciones], [(dni, rol)])
    # personal de vigilancia: docentes de jornada completa sin cargo de Consejo ni Hermanas
    activos = [k for k in D if k not in DE_BAJA_FINAL and not k.startswith("SUPLENTE")] \
        if anio == 2026 else [k for k in D if k not in ("I2", "COMP") and not k.startswith("SUPLENTE")]
    vig = [k for k in activos if D[k][2] == "Jornada completa" and k not in CONSEJO and k not in HERMANAS]
    vig.sort(key=lambda k: D[k][0])
    rot = 0 if anio == 2026 else 7
    usados_ayer = set()
    for d in range(5):
        hoy = []
        disponibles = [k for k in vig if k not in usados_ayer]
        disponibles = disponibles[rot % len(disponibles):] + disponibles[:rot % len(disponibles)]
        rot += 3

        def tomar(n):
            r = disponibles[:n]
            del disponibles[:n]
            hoy.extend(r)
            return r
        act.append((d, *RECREO, "Receso", "Patio Principal", SEC_SEC,
                    [(D[k][0], "Cuida receso") for k in tomar(2)]))
        act.append((d, *RECREO, "Receso", "Patio de Primaria", SEC_PRI,
                    [(D[k][0], "Cuida receso") for k in tomar(2)]))
        act.append((d, *ALMUERZO, "Receso", "Patio Principal", SEC_SEC,
                    [(D[k][0], r) for k, r in zip(tomar(2), ["Refrigera con estudiantes", "Cuida receso"])]))
        act.append((d, *ALMUERZO, "Receso", "Patio Techado", SEC_PRI,
                    [(D[k][0], r) for k, r in zip(tomar(3), ["Refrigera con estudiantes",
                                                            "Refrigera con estudiantes", "Cuida receso"])]))
        usados_ayer = set(hoy)
    act.append((0, "07:40", "09:10", "Consejo", "Sala de Consejo Directivo", [],
                [(D[k][0], "Reunión") for k in CONSEJO]))
    act.append((2, "11:00", "12:30", "Pastoral", "Sala de Pastoral", [],
                [(D[k][0], "Reunión") for k in HERMANAS]))
    return act


def main(salida):
    L = []
    L.append("""-- =========================================================================
-- ARCHIVO  : 05_datos_prueba.sql
-- CONTENIDO: Datos de prueba para demostrar la funcionalidad del aplicativo.
--            Generado con herramientas/generar_datos.py (no editar a mano).
-- Incluye   : 14 grados, 26 secciones, 15 asignaturas, 39 espacios,
--            45 docentes, plan de estudios de Primaria y Secundaria,
--            horario COMPLETO 2025 (periodo cerrado, histórico) y 2026
--            (periodo activo) para 22 secciones, recesos con turnos de
--            vigilancia, Consejo Directivo, Pastoral y 3 usuarios.
-- Todas las filas pasan por los triggers de validación: si alguna regla
-- de negocio se incumpliera, el script se detendría con un error.
-- =========================================================================

SET NAMES utf8mb4;
USE bd_horarios_esclavas;
SET @usuario_app = 'carga_inicial';
SET @rol_app = NULL;
""")
    L.append("-- ---------------------------------------------------------------- Usuarios")
    L.append("""INSERT INTO Usuario_Sistema (Usuario, Clave_Hash, Nombre_Completo, Rol) VALUES
('admin', SHA2('admin123', 256), 'Administrador del sistema', 'Administrador'),
('coordinador', SHA2('coord123', 256), 'Chambilla Quispe, María del Carmen', 'Coordinador'),
('consulta', SHA2('consulta123', 256), 'Docente de consulta', 'Consulta');
""")
    L.append("-- ---------------------------------------------------------------- Periodos")
    L.append("INSERT INTO Periodo_Academico (Anio_Lectivo, Estado) VALUES (2025, 'Activo'), (2026, 'Activo');\n")
    L.append("-- ---------------------------------------------------------------- Grados y secciones")
    L.append(insert("Grado_Escolar", ["ID_Grado", "Nombre", "Nivel"], GRADOS))
    L.append(insert("Seccion", ["ID_Seccion", "Letra_Identificadora", "ID_Grado"], SECCIONES) + "\n")
    L.append("-- ---------------------------------------------------------------- Asignaturas")
    L.append(insert("Asignatura", ["Nombre_Materia", "Nivel_Exigencia"], ASIGNATURAS) + "\n")
    L.append("-- ---------------------------------------------------------------- Espacios")
    L.append(insert("Espacio", ["Denominacion", "Categoria", "Limite_Grupos"], ESPACIOS) + "\n")
    L.append("-- ---------------------------------------------------------------- Docentes")
    L.append(insert("Docente", ["DNI", "Nombre", "Modalidad", "Condicion_Especial"],
                    [(v[0], v[1], v[2], v[3]) for v in D.values()]) + "\n")
    L.append("-- ---------------------------------------------------------------- Especialidades")
    L.append(insert("Especializado_En", ["Nombre_Asignatura", "DNI_Docente"],
                    [(e, v[0]) for v in D.values() for e in v[4]]) + "\n")
    L.append("-- ---------------------------------------------------------------- Plan de estudios")
    filas = []
    for g, _, niv in GRADOS:
        if niv in PLAN:
            filas += [(mat, g, h) for mat, h in PLAN[niv].items()]
    filas += [("Psicomotricidad", g, 4) for g in (1, 2, 3)] + \
             [(COM, g, 6) for g in (1, 2, 3)] + [(MAT, g, 5) for g in (1, 2, 3)]
    L.append(insert("Grado_Materia", ["Nombre_Asignatura", "ID_Grado", "Horas_Exigidas"], filas) + "\n")

    next_id = {2025: 1001, 2026: 5001}
    for anio, semilla in ((2025, 7), (2026, 11)):
        clases = resolver(anio, semilla)
        acts, sps, das = [], [], []
        i = next_id[anio]
        for d, ini, fin, nat, esp, secs, docs in recesos_y_bloqueos(anio):
            acts.append((i, ini + ":00", fin + ":00", DIAS[d], nat, None, anio, esp))
            sps += [(i, s) for s in secs]
            das += [(dni, i, rol) for dni, rol in docs]
            i += 1
        for d, b, s, mat, e, dni in clases:
            ini, fin = BLOQUES[b]
            acts.append((i, ini + ":00", fin + ":00", DIAS[d], "Clase", mat, anio, e))
            sps.append((i, s))
            das.append((dni, i, "Dicta clase"))
            i += 1
        L.append(f"-- ================================================================ Horario {anio}")
        L.append(f"-- {len(acts)} bloques, {len(sps)} participaciones de sección, {len(das)} asignaciones docentes")
        L.append(insert("Actividad", ["ID_Actividad", "Hora_Inicio", "Hora_Fin", "Dia", "Naturaleza",
                                      "Materia_Asignatura", "Periodo", "Espacio"], acts))
        L.append(insert("Seccion_Periodo", ["ID_Actividad", "ID_Seccion"], sps))
        L.append(insert("Docente_Actividad", ["DNI_Docente", "ID_Actividad", "Rol_Asumido"], das) + "\n")
        if anio == 2025:
            L.append("""-- El año 2025 concluyó: se cierra el periodo (queda como histórico de solo lectura)
UPDATE Periodo_Academico SET Estado = 'Cerrado' WHERE Anio_Lectivo = 2025;

-- Personal que cesó al terminar 2025 y laboratorio en remodelación:
-- tienen historial, por lo que se dan de baja (borrado lógico) y no se eliminan.
UPDATE Docente SET Activo = 0 WHERE DNI IN ('41239876', '40234561');
UPDATE Espacio SET Activo = 0 WHERE Denominacion = 'Laboratorio 2 - Química';
""")
    L.append("""-- Fin de la carga inicial
SET @usuario_app = NULL;
""")
    with open(salida, "w", encoding="utf-8") as f:
        f.write("\n".join(L))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "05_datos_prueba.sql")
