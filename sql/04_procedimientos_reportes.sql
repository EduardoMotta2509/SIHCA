-- =========================================================================
-- ARCHIVO  : 04_procedimientos_reportes.sql
-- CONTENIDO: 13 reportes (consultas) encapsulados en procedimientos.
--            R01 a R11 usan JOIN (consultas multitabla);
--            R12 y R13 son consultas de una sola tabla con agregación.
-- =========================================================================

-- Garantiza que tildes y eñes se interpreten en UTF-8 con cualquier cliente
SET NAMES utf8mb4;

USE bd_horarios_esclavas;

DELIMITER $$

-- -------------------------------------------------------------------------
-- R01. Matriz de horarios por sección (JOIN de 6 tablas)
--      Vista que se publica en EdusoftNet para estudiantes y familias.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_matriz_seccion$$
CREATE PROCEDURE sp_rep_matriz_seccion(IN p_periodo SMALLINT, IN p_id_seccion INT)
BEGIN
    SELECT a.Dia AS `Día`,
           TIME_FORMAT(a.Hora_Inicio, '%H:%i') AS Inicio,
           TIME_FORMAT(a.Hora_Fin, '%H:%i') AS Fin,
           IFNULL(a.Materia_Asignatura, a.Naturaleza) AS `Asignatura o actividad`,
           IFNULL(asig.Nivel_Exigencia, 'No aplica') AS `Exigencia cognitiva`,
           IFNULL(GROUP_CONCAT(d.Nombre ORDER BY d.Nombre SEPARATOR '; '), '(sin docente)') AS `Docente responsable`,
           a.Espacio AS `Ambiente físico`,
           CONCAT(g.Nombre, ' ', s.Letra_Identificadora, ' - ', g.Nivel) AS `Sección`
      FROM Actividad a
     INNER JOIN Seccion_Periodo sp ON sp.ID_Actividad = a.ID_Actividad
     INNER JOIN Seccion s ON s.ID_Seccion = sp.ID_Seccion
     INNER JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
      LEFT JOIN Asignatura asig ON asig.Nombre_Materia = a.Materia_Asignatura
      LEFT JOIN Docente_Actividad da ON da.ID_Actividad = a.ID_Actividad
      LEFT JOIN Docente d ON d.DNI = da.DNI_Docente
     WHERE a.Periodo = p_periodo AND s.ID_Seccion = p_id_seccion
     GROUP BY a.ID_Actividad, a.Dia, a.Hora_Inicio, a.Hora_Fin, a.Materia_Asignatura, a.Naturaleza,
              asig.Nivel_Exigencia, a.Espacio, g.Nombre, s.Letra_Identificadora, g.Nivel
     ORDER BY fn_orden_dia(a.Dia), a.Hora_Inicio;
END$$

-- -------------------------------------------------------------------------
-- R02. Horario personal de un docente (JOIN de 5 tablas)
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_horario_docente$$
CREATE PROCEDURE sp_rep_horario_docente(IN p_periodo SMALLINT, IN p_dni CHAR(8))
BEGIN
    SELECT a.Dia AS `Día`,
           TIME_FORMAT(a.Hora_Inicio, '%H:%i') AS Inicio,
           TIME_FORMAT(a.Hora_Fin, '%H:%i') AS Fin,
           IFNULL(a.Materia_Asignatura, a.Naturaleza) AS `Asignatura o actividad`,
           da.Rol_Asumido AS Rol,
           IFNULL(GROUP_CONCAT(CONCAT(g.Nombre, ' ', s.Letra_Identificadora)
                  ORDER BY g.ID_Grado, s.Letra_Identificadora SEPARATOR ', '), '-') AS Secciones,
           a.Espacio AS `Ambiente físico`,
           d.Nombre AS Docente
      FROM Docente d
     INNER JOIN Docente_Actividad da ON da.DNI_Docente = d.DNI
     INNER JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
      LEFT JOIN Seccion_Periodo sp ON sp.ID_Actividad = a.ID_Actividad
      LEFT JOIN Seccion s ON s.ID_Seccion = sp.ID_Seccion
      LEFT JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
     WHERE a.Periodo = p_periodo AND d.DNI = p_dni
     GROUP BY a.ID_Actividad, a.Dia, a.Hora_Inicio, a.Hora_Fin, a.Materia_Asignatura, a.Naturaleza,
              da.Rol_Asumido, a.Espacio, d.Nombre
     ORDER BY fn_orden_dia(a.Dia), a.Hora_Inicio;
END$$

-- -------------------------------------------------------------------------
-- R03. Cuadro de carga horaria docente: lectiva y no lectiva (JOIN 3 tablas)
--      Horas pedagógicas = bloques; horas reloj = minutos / 60.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_carga_docente$$
CREATE PROCEDURE sp_rep_carga_docente(IN p_periodo SMALLINT)
BEGIN
    SELECT d.DNI,
           d.Nombre AS Docente,
           d.Modalidad,
           IFNULL(d.Condicion_Especial, 'Docente') AS `Condición`,
           SUM(da.Rol_Asumido = 'Dicta clase') AS `Horas de clase`,
           SUM(da.Rol_Asumido = 'Cuida receso') AS `Turnos de receso`,
           SUM(da.Rol_Asumido = 'Refrigera con estudiantes') AS `Turnos de refrigerio`,
           SUM(da.Rol_Asumido = 'Reunión') AS Reuniones,
           COUNT(*) AS `Total de bloques`,
           ROUND(SUM(TIMESTAMPDIFF(MINUTE, a.Hora_Inicio, a.Hora_Fin)) / 60, 1) AS `Horas reloj semanales`
      FROM Docente d
     INNER JOIN Docente_Actividad da ON da.DNI_Docente = d.DNI
     INNER JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
     WHERE a.Periodo = p_periodo
     GROUP BY d.DNI, d.Nombre, d.Modalidad, d.Condicion_Especial
     ORDER BY `Horas de clase` DESC, d.Nombre;
END$$

-- -------------------------------------------------------------------------
-- R04. Cumplimiento del plan de estudios por sección (JOIN 5 tablas)
--      Compara horas exigidas (Grado_Materia) con bloques programados.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_cumplimiento_plan$$
CREATE PROCEDURE sp_rep_cumplimiento_plan(IN p_periodo SMALLINT, IN p_id_grado INT)
BEGIN
    SELECT CONCAT(g.Nombre, ' ', s.Letra_Identificadora) AS `Sección`,
           g.Nivel,
           gm.Nombre_Asignatura AS Asignatura,
           asig.Nivel_Exigencia AS Exigencia,
           gm.Horas_Exigidas AS `Horas del plan`,
           COUNT(a.ID_Actividad) AS `Horas programadas`,
           CASE
               WHEN COUNT(a.ID_Actividad) = gm.Horas_Exigidas THEN 'Completo'
               WHEN COUNT(a.ID_Actividad) < gm.Horas_Exigidas
                   THEN CONCAT('Faltan ', gm.Horas_Exigidas - COUNT(a.ID_Actividad), ' h')
               ELSE 'Excede el plan'
           END AS Estado
      FROM Seccion s
     INNER JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
     INNER JOIN Grado_Materia gm ON gm.ID_Grado = g.ID_Grado
     INNER JOIN Asignatura asig ON asig.Nombre_Materia = gm.Nombre_Asignatura
      LEFT JOIN Seccion_Periodo sp ON sp.ID_Seccion = s.ID_Seccion
      LEFT JOIN Actividad a ON a.ID_Actividad = sp.ID_Actividad
                           AND a.Periodo = p_periodo
                           AND a.Naturaleza = 'Clase'
                           AND a.Materia_Asignatura = gm.Nombre_Asignatura
     WHERE (p_id_grado IS NULL OR g.ID_Grado = p_id_grado)
     GROUP BY s.ID_Seccion, g.ID_Grado, g.Nombre, s.Letra_Identificadora, g.Nivel,
              gm.Nombre_Asignatura, asig.Nivel_Exigencia, gm.Horas_Exigidas
     ORDER BY g.ID_Grado, s.Letra_Identificadora, gm.Horas_Exigidas DESC, gm.Nombre_Asignatura;
END$$

-- -------------------------------------------------------------------------
-- R05. Validación de aforo en espacios compartidos (autocombinación)
--      Para cada bloque cuenta cuántos grupos usan el mismo espacio en una
--      franja que se superpone, y lo compara con Limite_Grupos.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_aforo_espacios$$
CREATE PROCEDURE sp_rep_aforo_espacios(IN p_periodo SMALLINT)
BEGIN
    SELECT e.Denominacion AS `Espacio físico`,
           e.Categoria AS `Categoría`,
           a.Dia AS `Día`,
           TIME_FORMAT(a.Hora_Inicio, '%H:%i') AS Inicio,
           TIME_FORMAT(a.Hora_Fin, '%H:%i') AS Fin,
           COUNT(DISTINCT b.ID_Actividad) AS `Grupos simultáneos`,
           e.Limite_Grupos AS `Capacidad máxima`,
           GROUP_CONCAT(DISTINCT IFNULL(b.Materia_Asignatura, b.Naturaleza) SEPARATOR ', ') AS Actividades,
           IF(COUNT(DISTINCT b.ID_Actividad) <= e.Limite_Grupos, 'Sin colisión', 'EXCEDE AFORO') AS `Validación`
      FROM Actividad a
     INNER JOIN Espacio e ON e.Denominacion = a.Espacio
     INNER JOIN Actividad b ON b.Espacio = a.Espacio AND b.Periodo = a.Periodo AND b.Dia = a.Dia
                           AND b.Hora_Inicio < a.Hora_Fin AND b.Hora_Fin > a.Hora_Inicio
     WHERE a.Periodo = p_periodo
     GROUP BY e.Denominacion, e.Categoria, a.Dia, a.Hora_Inicio, a.Hora_Fin, e.Limite_Grupos
    HAVING COUNT(DISTINCT b.ID_Actividad) > 1
     ORDER BY `Validación` DESC, e.Denominacion, fn_orden_dia(a.Dia), a.Hora_Inicio;
END$$

-- -------------------------------------------------------------------------
-- R06. Turnos de vigilancia de recesos y refrigerios (JOIN 3 tablas)
--      Señala si el docente repite vigilancia en días consecutivos.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_turnos_receso$$
CREATE PROCEDURE sp_rep_turnos_receso(IN p_periodo SMALLINT)
BEGIN
    SELECT a.Dia AS `Día`,
           TIME_FORMAT(a.Hora_Inicio, '%H:%i') AS Inicio,
           TIME_FORMAT(a.Hora_Fin, '%H:%i') AS Fin,
           TIMESTAMPDIFF(MINUTE, a.Hora_Inicio, a.Hora_Fin) AS `Duración (min)`,
           a.Espacio AS `Zona de cuidado`,
           d.Nombre AS `Docente responsable`,
           da.Rol_Asumido AS `Función`,
           IF(EXISTS (SELECT 1 FROM Docente_Actividad dx
                        JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
                       WHERE dx.DNI_Docente = d.DNI AND x.Periodo = a.Periodo
                         AND x.Naturaleza = 'Receso'
                         AND fn_orden_dia(x.Dia) = fn_orden_dia(a.Dia) - 1),
              'Sí (revisar)', 'No') AS `¿Vigiló el día anterior?`
      FROM Actividad a
     INNER JOIN Docente_Actividad da ON da.ID_Actividad = a.ID_Actividad
     INNER JOIN Docente d ON d.DNI = da.DNI_Docente
     WHERE a.Naturaleza = 'Receso' AND a.Periodo = p_periodo
     ORDER BY fn_orden_dia(a.Dia), a.Hora_Inicio, a.Espacio, da.Rol_Asumido, d.Nombre;
END$$

-- -------------------------------------------------------------------------
-- R07. Bloqueos prioritarios: Consejo Directivo y Pastoral (JOIN 3 tablas)
--      Incluye cuántas clases del responsable chocan con el bloqueo (debe ser 0).
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_bloqueos_prioritarios$$
CREATE PROCEDURE sp_rep_bloqueos_prioritarios(IN p_periodo SMALLINT)
BEGIN
    SELECT a.Naturaleza AS `Tipo de bloqueo`,
           a.Dia AS `Día`,
           TIME_FORMAT(a.Hora_Inicio, '%H:%i') AS Inicio,
           TIME_FORMAT(a.Hora_Fin, '%H:%i') AS Fin,
           d.Nombre AS Responsable,
           IFNULL(d.Condicion_Especial, 'Docente') AS `Cargo institucional`,
           a.Espacio AS `Ambiente asignado`,
           (SELECT COUNT(*) FROM Docente_Actividad dx
              JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
             WHERE dx.DNI_Docente = d.DNI AND x.Periodo = a.Periodo AND x.Dia = a.Dia
               AND x.Naturaleza = 'Clase'
               AND x.Hora_Inicio < a.Hora_Fin AND x.Hora_Fin > a.Hora_Inicio) AS `Clases en conflicto`
      FROM Actividad a
     INNER JOIN Docente_Actividad da ON da.ID_Actividad = a.ID_Actividad
     INNER JOIN Docente d ON d.DNI = da.DNI_Docente
     WHERE a.Naturaleza IN ('Consejo', 'Pastoral') AND a.Periodo = p_periodo
     ORDER BY fn_orden_dia(a.Dia), a.Hora_Inicio, d.Nombre;
END$$

-- -------------------------------------------------------------------------
-- R08. Docentes especialistas disponibles en una franja (JOIN + NOT EXISTS)
--      Útil para cubrir reemplazos: muestra primero a los menos cargados.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_docentes_disponibles$$
CREATE PROCEDURE sp_rep_docentes_disponibles(
    IN p_periodo SMALLINT, IN p_dia VARCHAR(10), IN p_hora_inicio TIME, IN p_hora_fin TIME,
    IN p_materia VARCHAR(60))
BEGIN
    SELECT d.DNI,
           d.Nombre AS Docente,
           d.Modalidad,
           IFNULL(d.Condicion_Especial, 'Docente') AS `Condición`,
           e.Nombre_Asignatura AS Especialidad,
           (SELECT COUNT(*) FROM Docente_Actividad dx
              JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
             WHERE dx.DNI_Docente = d.DNI AND x.Periodo = p_periodo
               AND dx.Rol_Asumido = 'Dicta clase') AS `Horas de clase en la semana`,
           (SELECT COUNT(*) FROM Docente_Actividad dx
              JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
             WHERE dx.DNI_Docente = d.DNI AND x.Periodo = p_periodo AND x.Dia = p_dia) AS `Bloques ese día`
      FROM Docente d
     INNER JOIN Especializado_En e ON e.DNI_Docente = d.DNI
     WHERE d.Activo = 1
       AND (p_materia IS NULL OR p_materia = '' OR e.Nombre_Asignatura = p_materia)
       AND NOT EXISTS (SELECT 1 FROM Docente_Actividad dx
                         JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
                        WHERE dx.DNI_Docente = d.DNI AND x.Periodo = p_periodo AND x.Dia = p_dia
                          AND x.Hora_Inicio < p_hora_fin AND x.Hora_Fin > p_hora_inicio)
     ORDER BY `Horas de clase en la semana`, d.Nombre;
END$$

-- -------------------------------------------------------------------------
-- R09. Ubicación de asignaturas de alta exigencia en las primeras horas
--      (JOIN 5 tablas). Criterio pedagógico de la Subdirección: las
--      materias pesadas deben dictarse antes del primer receso (10:40).
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_exigencia_primeras_horas$$
CREATE PROCEDURE sp_rep_exigencia_primeras_horas(IN p_periodo SMALLINT)
BEGIN
    SELECT CONCAT(g.Nombre, ' ', s.Letra_Identificadora) AS `Sección`,
           g.Nivel,
           COUNT(*) AS `Bloques de exigencia alta`,
           SUM(a.Hora_Inicio < '10:40:00') AS `Antes de las 10:40`,
           SUM(a.Hora_Inicio >= '10:40:00') AS `Después de las 10:40`,
           ROUND(100 * SUM(a.Hora_Inicio < '10:40:00') / COUNT(*), 1) AS `% en primeras horas`,
           CASE
               WHEN 100 * SUM(a.Hora_Inicio < '10:40:00') / COUNT(*) >= 60 THEN 'Adecuado'
               WHEN 100 * SUM(a.Hora_Inicio < '10:40:00') / COUNT(*) >= 40 THEN 'Mejorable'
               ELSE 'Reubicar'
           END AS `Evaluación`
      FROM Actividad a
     INNER JOIN Asignatura asig ON asig.Nombre_Materia = a.Materia_Asignatura
     INNER JOIN Seccion_Periodo sp ON sp.ID_Actividad = a.ID_Actividad
     INNER JOIN Seccion s ON s.ID_Seccion = sp.ID_Seccion
     INNER JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
     WHERE a.Periodo = p_periodo AND a.Naturaleza = 'Clase' AND asig.Nivel_Exigencia = 'Alta'
     GROUP BY s.ID_Seccion, g.ID_Grado, g.Nombre, s.Letra_Identificadora, g.Nivel
     ORDER BY `% en primeras horas`, g.ID_Grado, s.Letra_Identificadora;
END$$

-- -------------------------------------------------------------------------
-- R10. Ocupación semanal de los espacios físicos (LEFT JOIN)
--      Capacidad = límite de grupos x 5 días x jornada (07:40 a 15:30 = 470 min).
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_ocupacion_espacios$$
CREATE PROCEDURE sp_rep_ocupacion_espacios(IN p_periodo SMALLINT)
BEGIN
    SELECT e.Denominacion AS `Espacio físico`,
           e.Categoria AS `Categoría`,
           e.Limite_Grupos AS `Límite de grupos`,
           COUNT(a.ID_Actividad) AS `Bloques programados`,
           IFNULL(SUM(TIMESTAMPDIFF(MINUTE, a.Hora_Inicio, a.Hora_Fin)), 0) AS `Minutos usados`,
           e.Limite_Grupos * 5 * 470 AS `Minutos disponibles`,
           ROUND(100 * IFNULL(SUM(TIMESTAMPDIFF(MINUTE, a.Hora_Inicio, a.Hora_Fin)), 0)
                 / (e.Limite_Grupos * 5 * 470), 1) AS `% de ocupación`
      FROM Espacio e
      LEFT JOIN Actividad a ON a.Espacio = e.Denominacion AND a.Periodo = p_periodo
     WHERE e.Activo = 1
     GROUP BY e.Denominacion, e.Categoria, e.Limite_Grupos
     ORDER BY `% de ocupación` DESC, e.Denominacion;
END$$

-- -------------------------------------------------------------------------
-- R11. Comparativo histórico de carga docente entre dos periodos (JOIN)
--      Aprovecha el registro histórico que antes no existía en el colegio.
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_comparativo_periodos$$
CREATE PROCEDURE sp_rep_comparativo_periodos(IN p_anio_a SMALLINT, IN p_anio_b SMALLINT)
BEGIN
    SELECT d.DNI,
           d.Nombre AS Docente,
           IF(d.Activo = 1, 'Vigente', 'De baja') AS Estado,
           SUM(a.Periodo = p_anio_a AND da.Rol_Asumido = 'Dicta clase') AS `Horas de clase (año A)`,
           SUM(a.Periodo = p_anio_b AND da.Rol_Asumido = 'Dicta clase') AS `Horas de clase (año B)`,
           SUM(a.Periodo = p_anio_b AND da.Rol_Asumido = 'Dicta clase')
             - SUM(a.Periodo = p_anio_a AND da.Rol_Asumido = 'Dicta clase') AS `Variación`,
           SUM(a.Periodo = p_anio_a AND da.Rol_Asumido <> 'Dicta clase') AS `No lectivas (año A)`,
           SUM(a.Periodo = p_anio_b AND da.Rol_Asumido <> 'Dicta clase') AS `No lectivas (año B)`
      FROM Docente d
     INNER JOIN Docente_Actividad da ON da.DNI_Docente = d.DNI
     INNER JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
     WHERE a.Periodo IN (p_anio_a, p_anio_b)
     GROUP BY d.DNI, d.Nombre, d.Activo
     ORDER BY ABS(`Variación`) DESC, d.Nombre;
END$$

-- -------------------------------------------------------------------------
-- R12. Resumen de bloques por naturaleza y día (una sola tabla)
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_resumen_naturaleza$$
CREATE PROCEDURE sp_rep_resumen_naturaleza(IN p_periodo SMALLINT)
BEGIN
    SELECT Naturaleza AS Tipo,
           SUM(Dia = 'Lunes') AS Lunes,
           SUM(Dia = 'Martes') AS Martes,
           SUM(Dia = 'Miércoles') AS `Miércoles`,
           SUM(Dia = 'Jueves') AS Jueves,
           SUM(Dia = 'Viernes') AS Viernes,
           COUNT(*) AS `Total de bloques`,
           ROUND(SUM(TIMESTAMPDIFF(MINUTE, Hora_Inicio, Hora_Fin)) / 60, 1) AS `Horas reloj`
      FROM Actividad
     WHERE Periodo = p_periodo
     GROUP BY Naturaleza
     ORDER BY FIELD(Naturaleza, 'Clase', 'Receso', 'Consejo', 'Pastoral');
END$$

-- -------------------------------------------------------------------------
-- R13. Plantilla docente por modalidad y condición especial (una sola tabla)
-- -------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_rep_plantilla_docente$$
CREATE PROCEDURE sp_rep_plantilla_docente()
BEGIN
    SELECT Modalidad,
           IFNULL(Condicion_Especial, 'Docente') AS `Condición especial`,
           SUM(Activo = 1) AS Vigentes,
           SUM(Activo = 0) AS `De baja`,
           COUNT(*) AS Total
      FROM Docente
     GROUP BY Modalidad, Condicion_Especial
     ORDER BY Modalidad, Total DESC;
END$$

DELIMITER ;
