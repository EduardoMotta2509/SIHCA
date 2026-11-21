-- =========================================================================
-- ARCHIVO  : 02_funciones_triggers.sql
-- CONTENIDO: Funciones auxiliares, procedimientos de validación y TRIGGERS
--            de reglas de negocio (BEFORE). Los triggers de auditoría
--            (AFTER) se encuentran al final de este archivo.
-- -------------------------------------------------------------------------
-- Reglas de negocio implementadas (levantadas en la entrevista con la
-- Subdirección, Entregables 01-04):
--   R1. Un periodo CERRADO es histórico: no admite altas, cambios ni bajas.
--   R2. Un bloque de Clase lleva asignatura; Receso/Consejo/Pastoral no.
--   R3. No se supera el límite de grupos simultáneos de un espacio (aforo).
--   R4. Un docente no puede estar en dos actividades a la misma hora.
--   R5. Una sección no puede estar en dos actividades a la misma hora.
--   R6. Solo dicta una asignatura el docente especialista en ella.
--   R7. Una sección solo lleva asignaturas de su plan de estudios y no
--       excede las horas semanales exigidas.
--   R8. La suma del plan de estudios no supera el tope del nivel
--       (Secundaria 45 h, Primaria 40 h, Inicial 30 h).
--   R9. El rol del docente es coherente con la naturaleza del bloque.
--   R10. Solo miembros del Consejo Directivo participan en el Consejo.
--   R11. Docentes, asignaturas y espacios con historial no se eliminan
--        físicamente: se dan de baja (borrado lógico).
--   R12. La jornada escolar va de 07:00 a 17:00.
-- =========================================================================

-- Garantiza que tildes y eñes se interpreten en UTF-8 con cualquier cliente
SET NAMES utf8mb4;

USE bd_horarios_esclavas;

DROP FUNCTION IF EXISTS fn_usuario_actual;
DROP FUNCTION IF EXISTS fn_tope_horas_nivel;
DROP FUNCTION IF EXISTS fn_orden_dia;
DROP PROCEDURE IF EXISTS sp_error;
DROP PROCEDURE IF EXISTS sp_validar_periodo_activo;
DROP PROCEDURE IF EXISTS sp_validar_actividad;
DROP PROCEDURE IF EXISTS sp_validar_docente_actividad;
DROP PROCEDURE IF EXISTS sp_validar_seccion_actividad;

DELIMITER $$

-- -------------------------------------------------------------------------
-- F1. Usuario que realiza la operación. El aplicativo, al iniciar sesión,
--     ejecuta SET @usuario_app = '<usuario>'; fuera del aplicativo se
--     registra el usuario de MySQL.
-- -------------------------------------------------------------------------
CREATE FUNCTION fn_usuario_actual()
RETURNS VARCHAR(80)
NOT DETERMINISTIC
NO SQL
BEGIN
    RETURN COALESCE(@usuario_app, CURRENT_USER());
END$$

-- -------------------------------------------------------------------------
-- F2. Tope semanal de horas pedagógicas por nivel (R8)
-- -------------------------------------------------------------------------
CREATE FUNCTION fn_tope_horas_nivel(p_nivel VARCHAR(20))
RETURNS SMALLINT
DETERMINISTIC
NO SQL
BEGIN
    RETURN CASE p_nivel
        WHEN 'Secundaria' THEN 45
        WHEN 'Primaria'   THEN 40
        ELSE 30
    END;
END$$

-- -------------------------------------------------------------------------
-- F3. Posición del día en la semana (1 = Lunes ... 5 = Viernes)
-- -------------------------------------------------------------------------
CREATE FUNCTION fn_orden_dia(p_dia VARCHAR(10))
RETURNS TINYINT
DETERMINISTIC
NO SQL
BEGIN
    RETURN FIELD(p_dia, 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes');
END$$

-- -------------------------------------------------------------------------
-- P1. Lanza un error controlado (SQLSTATE 45000). MySQL limita el texto
--     del mensaje a 128 caracteres.
-- -------------------------------------------------------------------------
CREATE PROCEDURE sp_error(IN p_mensaje VARCHAR(500))
BEGIN
    DECLARE v_msg VARCHAR(128);
    SET v_msg = LEFT(p_mensaje, 128);
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
END$$

-- -------------------------------------------------------------------------
-- P2. R1: el periodo debe existir y estar Activo
-- -------------------------------------------------------------------------
CREATE PROCEDURE sp_validar_periodo_activo(IN p_periodo SMALLINT)
BEGIN
    DECLARE v_estado VARCHAR(10);
    SELECT Estado INTO v_estado FROM Periodo_Academico WHERE Anio_Lectivo = p_periodo;
    IF v_estado IS NULL THEN
        CALL sp_error(CONCAT('El periodo ', p_periodo, ' no existe.'));
    ELSEIF v_estado = 'Cerrado' THEN
        CALL sp_error(CONCAT('El periodo ', p_periodo,
            ' está CERRADO: su horario es histórico y no se puede modificar.'));
    END IF;
END$$

-- -------------------------------------------------------------------------
-- P3. Validación de un bloque horario (usada por los triggers de Actividad)
--     R1, R2, R3, R12, asignatura y espacio vigentes; en una modificación
--     además se revisa que docentes y secciones ya asignados no queden
--     con cruces (R4, R5) ni con asignaturas ajenas (R6, R7).
-- -------------------------------------------------------------------------
CREATE PROCEDURE sp_validar_actividad(
    IN p_id INT, IN p_ini TIME, IN p_fin TIME, IN p_dia VARCHAR(10),
    IN p_naturaleza VARCHAR(30), IN p_materia VARCHAR(60),
    IN p_periodo SMALLINT, IN p_espacio VARCHAR(60), IN p_es_modificacion TINYINT)
BEGIN
    DECLARE v_activo TINYINT;
    DECLARE v_limite SMALLINT;
    DECLARE v_ocupados INT DEFAULT 0;
    DECLARE v_nombre VARCHAR(120);

    CALL sp_validar_periodo_activo(p_periodo);

    IF p_ini < '07:00:00' OR p_fin > '17:00:00' THEN
        CALL sp_error('El bloque debe estar dentro de la jornada escolar (07:00 a 17:00).');
    END IF;
    IF p_fin <= p_ini THEN
        CALL sp_error('La hora de fin debe ser posterior a la hora de inicio.');
    END IF;

    -- R2: coherencia naturaleza / asignatura
    IF p_naturaleza = 'Clase' AND p_materia IS NULL THEN
        CALL sp_error('Un bloque de tipo Clase requiere una asignatura.');
    END IF;
    IF p_naturaleza <> 'Clase' AND p_materia IS NOT NULL THEN
        CALL sp_error(CONCAT('Un bloque de tipo ', p_naturaleza, ' no lleva asignatura.'));
    END IF;

    -- Asignatura vigente
    IF p_materia IS NOT NULL THEN
        SET v_activo = NULL;
        SELECT Activo INTO v_activo FROM Asignatura WHERE Nombre_Materia = p_materia;
        IF v_activo IS NULL THEN
            CALL sp_error(CONCAT('La asignatura "', p_materia, '" no existe.'));
        ELSEIF v_activo = 0 THEN
            CALL sp_error(CONCAT('La asignatura "', p_materia, '" está dada de baja.'));
        END IF;
    END IF;

    -- Espacio vigente y aforo (R3)
    SET v_activo = NULL;
    SELECT Activo, Limite_Grupos INTO v_activo, v_limite
      FROM Espacio WHERE Denominacion = p_espacio;
    IF v_activo IS NULL THEN
        CALL sp_error(CONCAT('El espacio "', p_espacio, '" no existe.'));
    ELSEIF v_activo = 0 THEN
        CALL sp_error(CONCAT('El espacio "', p_espacio, '" está dado de baja.'));
    END IF;

    SELECT COUNT(*) INTO v_ocupados
      FROM Actividad a
     WHERE a.Periodo = p_periodo AND a.Dia = p_dia AND a.Espacio = p_espacio
       AND a.Hora_Inicio < p_fin AND a.Hora_Fin > p_ini
       AND a.ID_Actividad <> p_id;
    IF v_ocupados >= v_limite THEN
        CALL sp_error(CONCAT('Aforo excedido: "', p_espacio, '" ya tiene ', v_ocupados,
            ' grupo(s) el ', p_dia, ' a esa hora (límite ', v_limite, ').'));
    END IF;

    IF p_es_modificacion = 1 THEN
        -- R4: docentes ya asignados no deben quedar cruzados
        SET v_nombre = NULL;
        SELECT d.Nombre INTO v_nombre
          FROM Docente_Actividad da
          JOIN Docente d ON d.DNI = da.DNI_Docente
          JOIN Docente_Actividad dx ON dx.DNI_Docente = da.DNI_Docente
                                    AND dx.ID_Actividad <> da.ID_Actividad
          JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
         WHERE da.ID_Actividad = p_id
           AND x.Periodo = p_periodo AND x.Dia = p_dia
           AND x.Hora_Inicio < p_fin AND x.Hora_Fin > p_ini
         LIMIT 1;
        IF v_nombre IS NOT NULL THEN
            CALL sp_error(CONCAT('Cruce de horario: el docente ', v_nombre,
                ' ya tiene otra actividad en esa franja.'));
        END IF;

        -- R5: secciones ya asignadas no deben quedar cruzadas
        SET v_nombre = NULL;
        SELECT CONCAT(g.Nombre, ' ', s.Letra_Identificadora, ' (', g.Nivel, ')') INTO v_nombre
          FROM Seccion_Periodo sp
          JOIN Seccion s ON s.ID_Seccion = sp.ID_Seccion
          JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
          JOIN Seccion_Periodo sx ON sx.ID_Seccion = sp.ID_Seccion
                                  AND sx.ID_Actividad <> sp.ID_Actividad
          JOIN Actividad x ON x.ID_Actividad = sx.ID_Actividad
         WHERE sp.ID_Actividad = p_id
           AND x.Periodo = p_periodo AND x.Dia = p_dia
           AND x.Hora_Inicio < p_fin AND x.Hora_Fin > p_ini
         LIMIT 1;
        IF v_nombre IS NOT NULL THEN
            CALL sp_error(CONCAT('Cruce de horario: la sección ', v_nombre,
                ' ya tiene otra actividad en esa franja.'));
        END IF;

        -- R6: si es clase, los docentes que dictan deben ser especialistas
        IF p_naturaleza = 'Clase' THEN
            SET v_nombre = NULL;
            SELECT d.Nombre INTO v_nombre
              FROM Docente_Actividad da
              JOIN Docente d ON d.DNI = da.DNI_Docente
             WHERE da.ID_Actividad = p_id
               AND NOT EXISTS (SELECT 1 FROM Especializado_En e
                                WHERE e.DNI_Docente = da.DNI_Docente
                                  AND e.Nombre_Asignatura = p_materia)
             LIMIT 1;
            IF v_nombre IS NOT NULL THEN
                CALL sp_error(CONCAT('El docente ', v_nombre, ' no es especialista en ', p_materia, '.'));
            END IF;
        END IF;
    END IF;
END$$

-- -------------------------------------------------------------------------
-- P4. Validación de la asignación Docente <-> Actividad (R1, R4, R6, R9, R10)
-- -------------------------------------------------------------------------
CREATE PROCEDURE sp_validar_docente_actividad(
    IN p_dni CHAR(8), IN p_id INT, IN p_rol VARCHAR(40))
BEGIN
    DECLARE v_periodo SMALLINT;
    DECLARE v_dia VARCHAR(10);
    DECLARE v_ini TIME;
    DECLARE v_fin TIME;
    DECLARE v_naturaleza VARCHAR(30);
    DECLARE v_materia VARCHAR(60);
    DECLARE v_activo TINYINT;
    DECLARE v_nombre VARCHAR(100);
    DECLARE v_condicion VARCHAR(50);
    DECLARE v_choque VARCHAR(200);

    SELECT Periodo, Dia, Hora_Inicio, Hora_Fin, Naturaleza, Materia_Asignatura
      INTO v_periodo, v_dia, v_ini, v_fin, v_naturaleza, v_materia
      FROM Actividad WHERE ID_Actividad = p_id;
    IF v_periodo IS NULL THEN
        CALL sp_error(CONCAT('La actividad ', p_id, ' no existe.'));
    END IF;

    CALL sp_validar_periodo_activo(v_periodo);

    SELECT Activo, Nombre, Condicion_Especial INTO v_activo, v_nombre, v_condicion
      FROM Docente WHERE DNI = p_dni;
    IF v_activo IS NULL THEN
        CALL sp_error(CONCAT('No existe un docente con DNI ', p_dni, '.'));
    ELSEIF v_activo = 0 THEN
        CALL sp_error(CONCAT('El docente ', v_nombre, ' está dado de baja.'));
    END IF;

    -- R9: rol coherente con la naturaleza del bloque
    IF (v_naturaleza = 'Clase' AND p_rol <> 'Dicta clase')
       OR (v_naturaleza = 'Receso' AND p_rol NOT IN ('Cuida receso', 'Refrigera con estudiantes'))
       OR (v_naturaleza IN ('Consejo', 'Pastoral') AND p_rol <> 'Reunión') THEN
        CALL sp_error(CONCAT('El rol "', p_rol, '" no corresponde a un bloque de tipo ', v_naturaleza, '.'));
    END IF;

    -- R10: Consejo Directivo
    IF v_naturaleza = 'Consejo' AND IFNULL(v_condicion, '') <> 'Miembro del Consejo Directivo' THEN
        CALL sp_error(CONCAT(v_nombre, ' no es miembro del Consejo Directivo.'));
    END IF;

    -- R6: especialidad
    IF v_naturaleza = 'Clase' AND NOT EXISTS (
        SELECT 1 FROM Especializado_En
         WHERE DNI_Docente = p_dni AND Nombre_Asignatura = v_materia) THEN
        CALL sp_error(CONCAT(v_nombre, ' no es especialista en ', v_materia, '.'));
    END IF;

    -- R4: cruce de horario del docente
    SELECT CONCAT(IFNULL(x.Materia_Asignatura, x.Naturaleza), ' (', x.Dia, ' ',
                  TIME_FORMAT(x.Hora_Inicio, '%H:%i'), '-', TIME_FORMAT(x.Hora_Fin, '%H:%i'), ')')
      INTO v_choque
      FROM Docente_Actividad dx
      JOIN Actividad x ON x.ID_Actividad = dx.ID_Actividad
     WHERE dx.DNI_Docente = p_dni AND dx.ID_Actividad <> p_id
       AND x.Periodo = v_periodo AND x.Dia = v_dia
       AND x.Hora_Inicio < v_fin AND x.Hora_Fin > v_ini
     LIMIT 1;
    IF v_choque IS NOT NULL THEN
        CALL sp_error(CONCAT('Cruce: ', v_nombre, ' ya tiene ', v_choque, '.'));
    END IF;
END$$

-- -------------------------------------------------------------------------
-- P5. Validación de la participación Sección <-> Actividad (R1, R5, R7)
-- -------------------------------------------------------------------------
CREATE PROCEDURE sp_validar_seccion_actividad(IN p_id_seccion INT, IN p_id INT)
BEGIN
    DECLARE v_periodo SMALLINT;
    DECLARE v_dia VARCHAR(10);
    DECLARE v_ini TIME;
    DECLARE v_fin TIME;
    DECLARE v_naturaleza VARCHAR(30);
    DECLARE v_materia VARCHAR(60);
    DECLARE v_grado INT;
    DECLARE v_seccion VARCHAR(60);
    DECLARE v_horas SMALLINT;
    DECLARE v_programadas INT;
    DECLARE v_choque VARCHAR(200);

    SELECT Periodo, Dia, Hora_Inicio, Hora_Fin, Naturaleza, Materia_Asignatura
      INTO v_periodo, v_dia, v_ini, v_fin, v_naturaleza, v_materia
      FROM Actividad WHERE ID_Actividad = p_id;
    IF v_periodo IS NULL THEN
        CALL sp_error(CONCAT('La actividad ', p_id, ' no existe.'));
    END IF;

    CALL sp_validar_periodo_activo(v_periodo);

    SELECT s.ID_Grado, CONCAT(g.Nombre, ' ', s.Letra_Identificadora, ' (', g.Nivel, ')')
      INTO v_grado, v_seccion
      FROM Seccion s JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
     WHERE s.ID_Seccion = p_id_seccion;
    IF v_grado IS NULL THEN
        CALL sp_error(CONCAT('La sección ', p_id_seccion, ' no existe.'));
    END IF;

    IF v_naturaleza = 'Consejo' THEN
        CALL sp_error('Las reuniones del Consejo Directivo no involucran secciones de estudiantes.');
    END IF;

    -- R5: cruce de horario de la sección
    SELECT CONCAT(IFNULL(x.Materia_Asignatura, x.Naturaleza), ' (', x.Dia, ' ',
                  TIME_FORMAT(x.Hora_Inicio, '%H:%i'), '-', TIME_FORMAT(x.Hora_Fin, '%H:%i'), ')')
      INTO v_choque
      FROM Seccion_Periodo sx
      JOIN Actividad x ON x.ID_Actividad = sx.ID_Actividad
     WHERE sx.ID_Seccion = p_id_seccion AND sx.ID_Actividad <> p_id
       AND x.Periodo = v_periodo AND x.Dia = v_dia
       AND x.Hora_Inicio < v_fin AND x.Hora_Fin > v_ini
     LIMIT 1;
    IF v_choque IS NOT NULL THEN
        CALL sp_error(CONCAT('Cruce: la sección ', v_seccion, ' ya tiene ', v_choque, '.'));
    END IF;

    -- R7: plan de estudios del grado y tope de horas de la asignatura
    IF v_naturaleza = 'Clase' THEN
        SELECT Horas_Exigidas INTO v_horas
          FROM Grado_Materia
         WHERE ID_Grado = v_grado AND Nombre_Asignatura = v_materia;
        IF v_horas IS NULL THEN
            CALL sp_error(CONCAT(v_materia, ' no forma parte del plan de estudios de ', v_seccion, '.'));
        END IF;

        SELECT COUNT(*) INTO v_programadas
          FROM Seccion_Periodo sx
          JOIN Actividad x ON x.ID_Actividad = sx.ID_Actividad
         WHERE sx.ID_Seccion = p_id_seccion AND sx.ID_Actividad <> p_id
           AND x.Periodo = v_periodo AND x.Naturaleza = 'Clase'
           AND x.Materia_Asignatura = v_materia;
        IF v_programadas >= v_horas THEN
            CALL sp_error(CONCAT('Tope alcanzado: ', v_seccion, ' ya tiene las ', v_horas,
                ' horas semanales de ', v_materia, '.'));
        END IF;
    END IF;
END$$

-- =========================================================================
-- TRIGGERS DE VALIDACIÓN (BEFORE)
-- =========================================================================

-- ----------------------------- Periodo_Academico -------------------------
CREATE TRIGGER trg_periodo_bu BEFORE UPDATE ON Periodo_Academico
FOR EACH ROW
BEGIN
    IF NEW.Anio_Lectivo <> OLD.Anio_Lectivo
       AND EXISTS (SELECT 1 FROM Actividad WHERE Periodo = OLD.Anio_Lectivo) THEN
        CALL sp_error('No se puede cambiar el año de un periodo que ya tiene actividades.');
    END IF;
END$$

CREATE TRIGGER trg_periodo_bd BEFORE DELETE ON Periodo_Academico
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Actividad WHERE Periodo = OLD.Anio_Lectivo) THEN
        CALL sp_error(CONCAT('El periodo ', OLD.Anio_Lectivo,
            ' tiene horario registrado y forma parte del histórico: no se puede eliminar.'));
    END IF;
END$$

-- ----------------------------- Grado_Escolar -----------------------------
CREATE TRIGGER trg_grado_bi BEFORE INSERT ON Grado_Escolar
FOR EACH ROW
BEGIN
    SET NEW.Nombre = TRIM(NEW.Nombre);
END$$

CREATE TRIGGER trg_grado_bu BEFORE UPDATE ON Grado_Escolar
FOR EACH ROW
BEGIN
    DECLARE v_total INT;
    SET NEW.Nombre = TRIM(NEW.Nombre);
    IF NEW.Nivel <> OLD.Nivel THEN
        SELECT IFNULL(SUM(Horas_Exigidas), 0) INTO v_total
          FROM Grado_Materia WHERE ID_Grado = OLD.ID_Grado;
        IF v_total > fn_tope_horas_nivel(NEW.Nivel) THEN
            CALL sp_error(CONCAT('El plan del grado suma ', v_total, ' h y supera el tope de ',
                NEW.Nivel, ' (', fn_tope_horas_nivel(NEW.Nivel), ' h).'));
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_grado_bd BEFORE DELETE ON Grado_Escolar
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Seccion WHERE ID_Grado = OLD.ID_Grado) THEN
        CALL sp_error(CONCAT('El grado "', OLD.Nombre, '" tiene secciones registradas: elimínelas primero.'));
    END IF;
END$$

-- ----------------------------- Seccion -----------------------------------
CREATE TRIGGER trg_seccion_bi BEFORE INSERT ON Seccion
FOR EACH ROW
BEGIN
    SET NEW.Letra_Identificadora = UPPER(TRIM(NEW.Letra_Identificadora));
END$$

CREATE TRIGGER trg_seccion_bu BEFORE UPDATE ON Seccion
FOR EACH ROW
BEGIN
    SET NEW.Letra_Identificadora = UPPER(TRIM(NEW.Letra_Identificadora));
    IF NEW.ID_Grado <> OLD.ID_Grado
       AND EXISTS (SELECT 1 FROM Seccion_Periodo WHERE ID_Seccion = OLD.ID_Seccion) THEN
        CALL sp_error('La sección ya tiene horario registrado: no se puede mover a otro grado.');
    END IF;
END$$

CREATE TRIGGER trg_seccion_bd BEFORE DELETE ON Seccion
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Seccion_Periodo WHERE ID_Seccion = OLD.ID_Seccion) THEN
        CALL sp_error('La sección tiene horario registrado (histórico): no se puede eliminar.');
    END IF;
END$$

-- ----------------------------- Asignatura --------------------------------
CREATE TRIGGER trg_asignatura_bi BEFORE INSERT ON Asignatura
FOR EACH ROW
BEGIN
    SET NEW.Nombre_Materia = TRIM(NEW.Nombre_Materia);
    IF NEW.Nombre_Materia = '' THEN
        CALL sp_error('El nombre de la asignatura es obligatorio.');
    END IF;
END$$

CREATE TRIGGER trg_asignatura_bu BEFORE UPDATE ON Asignatura
FOR EACH ROW
BEGIN
    DECLARE v_bloques INT;
    SET NEW.Nombre_Materia = TRIM(NEW.Nombre_Materia);
    IF OLD.Activo = 1 AND NEW.Activo = 0 THEN
        SELECT COUNT(*) INTO v_bloques
          FROM Actividad a JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
         WHERE a.Materia_Asignatura = OLD.Nombre_Materia AND p.Estado = 'Activo';
        IF v_bloques > 0 THEN
            CALL sp_error(CONCAT('No se puede dar de baja: ', OLD.Nombre_Materia, ' tiene ', v_bloques,
                ' bloque(s) en el periodo activo.'));
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_asignatura_bd BEFORE DELETE ON Asignatura
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Actividad WHERE Materia_Asignatura = OLD.Nombre_Materia)
       OR EXISTS (SELECT 1 FROM Grado_Materia WHERE Nombre_Asignatura = OLD.Nombre_Materia) THEN
        CALL sp_error(CONCAT(OLD.Nombre_Materia,
            ' tiene historial o plan de estudios: use la baja lógica en lugar de eliminarla.'));
    END IF;
END$$

-- ----------------------------- Espacio -----------------------------------
CREATE TRIGGER trg_espacio_bi BEFORE INSERT ON Espacio
FOR EACH ROW
BEGIN
    SET NEW.Denominacion = TRIM(NEW.Denominacion);
    IF NEW.Denominacion = '' THEN
        CALL sp_error('La denominación del espacio es obligatoria.');
    END IF;
END$$

CREATE TRIGGER trg_espacio_bu BEFORE UPDATE ON Espacio
FOR EACH ROW
BEGIN
    DECLARE v_bloques INT;
    DECLARE v_max_simultaneo INT;
    SET NEW.Denominacion = TRIM(NEW.Denominacion);

    IF OLD.Activo = 1 AND NEW.Activo = 0 THEN
        SELECT COUNT(*) INTO v_bloques
          FROM Actividad a JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
         WHERE a.Espacio = OLD.Denominacion AND p.Estado = 'Activo';
        IF v_bloques > 0 THEN
            CALL sp_error(CONCAT('No se puede dar de baja: el espacio tiene ', v_bloques,
                ' bloque(s) en el periodo activo.'));
        END IF;
    END IF;

    IF NEW.Limite_Grupos < OLD.Limite_Grupos THEN
        -- Uso simultáneo máximo vigente en periodos activos
        SELECT IFNULL(MAX(t.simultaneos), 0) INTO v_max_simultaneo FROM (
            SELECT COUNT(*) AS simultaneos
              FROM Actividad a
              JOIN Actividad b ON b.Espacio = a.Espacio AND b.Periodo = a.Periodo AND b.Dia = a.Dia
                              AND b.Hora_Inicio < a.Hora_Fin AND b.Hora_Fin > a.Hora_Inicio
              JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
             WHERE a.Espacio = OLD.Denominacion AND p.Estado = 'Activo'
             GROUP BY a.ID_Actividad) t;
        IF v_max_simultaneo > NEW.Limite_Grupos THEN
            CALL sp_error(CONCAT('El espacio ya se usa con ', v_max_simultaneo,
                ' grupos simultáneos; el límite no puede ser menor.'));
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_espacio_bd BEFORE DELETE ON Espacio
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Actividad WHERE Espacio = OLD.Denominacion) THEN
        CALL sp_error(CONCAT('"', OLD.Denominacion,
            '" tiene historial de uso: use la baja lógica en lugar de eliminarlo.'));
    END IF;
END$$

-- ----------------------------- Docente -----------------------------------
CREATE TRIGGER trg_docente_bi BEFORE INSERT ON Docente
FOR EACH ROW
BEGIN
    SET NEW.DNI = TRIM(NEW.DNI);
    SET NEW.Nombre = TRIM(NEW.Nombre);
    IF CHAR_LENGTH(NEW.Nombre) < 5 THEN
        CALL sp_error('Ingrese los apellidos y nombres completos del docente.');
    END IF;
END$$

CREATE TRIGGER trg_docente_bu BEFORE UPDATE ON Docente
FOR EACH ROW
BEGIN
    DECLARE v_bloques INT;
    SET NEW.Nombre = TRIM(NEW.Nombre);
    IF CHAR_LENGTH(NEW.Nombre) < 5 THEN
        CALL sp_error('Ingrese los apellidos y nombres completos del docente.');
    END IF;
    IF OLD.Activo = 1 AND NEW.Activo = 0 THEN
        SELECT COUNT(*) INTO v_bloques
          FROM Docente_Actividad da
          JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
          JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
         WHERE da.DNI_Docente = OLD.DNI AND p.Estado = 'Activo';
        IF v_bloques > 0 THEN
            CALL sp_error(CONCAT('No se puede dar de baja: el docente tiene ', v_bloques,
                ' bloque(s) asignados en el periodo activo. Reasígnelos primero.'));
        END IF;
    END IF;
    -- Si deja de ser miembro del Consejo, no puede seguir en reuniones del Consejo activas
    IF IFNULL(OLD.Condicion_Especial, '') = 'Miembro del Consejo Directivo'
       AND IFNULL(NEW.Condicion_Especial, '') <> 'Miembro del Consejo Directivo'
       AND EXISTS (SELECT 1 FROM Docente_Actividad da
                     JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
                     JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
                    WHERE da.DNI_Docente = OLD.DNI AND a.Naturaleza = 'Consejo'
                      AND p.Estado = 'Activo') THEN
        CALL sp_error('El docente está asignado a reuniones del Consejo en el periodo activo.');
    END IF;
END$$

CREATE TRIGGER trg_docente_bd BEFORE DELETE ON Docente
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Docente_Actividad WHERE DNI_Docente = OLD.DNI) THEN
        CALL sp_error(CONCAT(OLD.Nombre,
            ' tiene historial de carga horaria: use la baja lógica en lugar de eliminarlo.'));
    END IF;
END$$

-- ----------------------------- Grado_Materia (Plan de estudios) ----------
CREATE TRIGGER trg_grado_materia_bi BEFORE INSERT ON Grado_Materia
FOR EACH ROW
BEGIN
    DECLARE v_total INT;
    DECLARE v_nivel VARCHAR(20);
    SELECT Nivel INTO v_nivel FROM Grado_Escolar WHERE ID_Grado = NEW.ID_Grado;
    IF v_nivel IS NULL THEN
        CALL sp_error(CONCAT('El grado ', NEW.ID_Grado, ' no existe.'));
    END IF;
    SELECT IFNULL(SUM(Horas_Exigidas), 0) INTO v_total
      FROM Grado_Materia WHERE ID_Grado = NEW.ID_Grado;
    IF v_total + NEW.Horas_Exigidas > fn_tope_horas_nivel(v_nivel) THEN
        CALL sp_error(CONCAT('El plan quedaría con ', v_total + NEW.Horas_Exigidas,
            ' h semanales; el tope de ', v_nivel, ' es ', fn_tope_horas_nivel(v_nivel), ' h.'));
    END IF;
END$$

CREATE TRIGGER trg_grado_materia_bu BEFORE UPDATE ON Grado_Materia
FOR EACH ROW
BEGIN
    DECLARE v_total INT;
    DECLARE v_nivel VARCHAR(20);
    DECLARE v_max_programadas INT;

    IF NEW.ID_Grado <> OLD.ID_Grado OR NEW.Nombre_Asignatura <> OLD.Nombre_Asignatura THEN
        CALL sp_error('Para cambiar el grado o la asignatura, elimine la fila y regístrela de nuevo.');
    END IF;

    SELECT Nivel INTO v_nivel FROM Grado_Escolar WHERE ID_Grado = NEW.ID_Grado;
    SELECT IFNULL(SUM(Horas_Exigidas), 0) INTO v_total
      FROM Grado_Materia
     WHERE ID_Grado = NEW.ID_Grado AND Nombre_Asignatura <> OLD.Nombre_Asignatura;
    IF v_total + NEW.Horas_Exigidas > fn_tope_horas_nivel(v_nivel) THEN
        CALL sp_error(CONCAT('El plan quedaría con ', v_total + NEW.Horas_Exigidas,
            ' h semanales; el tope de ', v_nivel, ' es ', fn_tope_horas_nivel(v_nivel), ' h.'));
    END IF;

    -- No reducir por debajo de lo ya programado en el periodo activo
    SELECT IFNULL(MAX(t.bloques), 0) INTO v_max_programadas FROM (
        SELECT COUNT(*) AS bloques
          FROM Seccion s
          JOIN Seccion_Periodo sp ON sp.ID_Seccion = s.ID_Seccion
          JOIN Actividad a ON a.ID_Actividad = sp.ID_Actividad
          JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
         WHERE s.ID_Grado = NEW.ID_Grado AND p.Estado = 'Activo'
           AND a.Naturaleza = 'Clase' AND a.Materia_Asignatura = NEW.Nombre_Asignatura
         GROUP BY s.ID_Seccion, a.Periodo) t;
    IF NEW.Horas_Exigidas < v_max_programadas THEN
        CALL sp_error(CONCAT('Ya hay secciones con ', v_max_programadas,
            ' bloques programados de ', NEW.Nombre_Asignatura, '; libérelos antes de reducir las horas.'));
    END IF;
END$$

CREATE TRIGGER trg_grado_materia_bd BEFORE DELETE ON Grado_Materia
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1
                 FROM Seccion s
                 JOIN Seccion_Periodo sp ON sp.ID_Seccion = s.ID_Seccion
                 JOIN Actividad a ON a.ID_Actividad = sp.ID_Actividad
                 JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
                WHERE s.ID_Grado = OLD.ID_Grado AND p.Estado = 'Activo'
                  AND a.Materia_Asignatura = OLD.Nombre_Asignatura) THEN
        CALL sp_error(CONCAT(OLD.Nombre_Asignatura,
            ' tiene clases programadas para este grado en el periodo activo.'));
    END IF;
END$$

-- ----------------------------- Especializado_En --------------------------
CREATE TRIGGER trg_especialidad_bi BEFORE INSERT ON Especializado_En
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM Docente WHERE DNI = NEW.DNI_Docente AND Activo = 0) THEN
        CALL sp_error('No se pueden asignar especialidades a un docente dado de baja.');
    END IF;
END$$

CREATE TRIGGER trg_especialidad_bd BEFORE DELETE ON Especializado_En
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1
                 FROM Docente_Actividad da
                 JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
                 JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
                WHERE da.DNI_Docente = OLD.DNI_Docente AND p.Estado = 'Activo'
                  AND a.Materia_Asignatura = OLD.Nombre_Asignatura) THEN
        CALL sp_error(CONCAT('El docente dicta ', OLD.Nombre_Asignatura,
            ' en el periodo activo: reasigne esas clases antes de quitar la especialidad.'));
    END IF;
END$$

-- ----------------------------- Actividad ---------------------------------
CREATE TRIGGER trg_actividad_bi BEFORE INSERT ON Actividad
FOR EACH ROW
BEGIN
    IF NEW.Naturaleza <> 'Clase' THEN
        SET NEW.Materia_Asignatura = NULLIF(TRIM(NEW.Materia_Asignatura), '');
    END IF;
    CALL sp_validar_actividad(NEW.ID_Actividad, NEW.Hora_Inicio, NEW.Hora_Fin, NEW.Dia,
        NEW.Naturaleza, NEW.Materia_Asignatura, NEW.Periodo, NEW.Espacio, 0);
END$$

CREATE TRIGGER trg_actividad_bu BEFORE UPDATE ON Actividad
FOR EACH ROW
BEGIN
    IF NEW.ID_Actividad <> OLD.ID_Actividad THEN
        CALL sp_error('El identificador de una actividad no se puede modificar.');
    END IF;
    -- El periodo de origen también debe estar activo (no se altera el histórico)
    CALL sp_validar_periodo_activo(OLD.Periodo);
    -- Un bloque de Clase sin asignatura solo puede venir de un ON DELETE SET NULL,
    -- que la baja lógica impide; se valida igualmente.
    CALL sp_validar_actividad(NEW.ID_Actividad, NEW.Hora_Inicio, NEW.Hora_Fin, NEW.Dia,
        NEW.Naturaleza, NEW.Materia_Asignatura, NEW.Periodo, NEW.Espacio, 1);
    -- Si cambia la naturaleza, los roles asignados deben seguir siendo coherentes
    IF NEW.Naturaleza <> OLD.Naturaleza
       AND EXISTS (SELECT 1 FROM Docente_Actividad WHERE ID_Actividad = OLD.ID_Actividad) THEN
        CALL sp_error('Quite primero a los docentes asignados para cambiar el tipo de bloque.');
    END IF;
END$$

CREATE TRIGGER trg_actividad_bd BEFORE DELETE ON Actividad
FOR EACH ROW
BEGIN
    CALL sp_validar_periodo_activo(OLD.Periodo);
END$$

-- ----------------------------- Seccion_Periodo ---------------------------
CREATE TRIGGER trg_seccion_periodo_bi BEFORE INSERT ON Seccion_Periodo
FOR EACH ROW
BEGIN
    CALL sp_validar_seccion_actividad(NEW.ID_Seccion, NEW.ID_Actividad);
END$$

CREATE TRIGGER trg_seccion_periodo_bu BEFORE UPDATE ON Seccion_Periodo
FOR EACH ROW
BEGIN
    CALL sp_error('Para cambiar la sección de una actividad, quítela y agréguela de nuevo.');
END$$

CREATE TRIGGER trg_seccion_periodo_bd BEFORE DELETE ON Seccion_Periodo
FOR EACH ROW
BEGIN
    DECLARE v_periodo SMALLINT;
    SELECT Periodo INTO v_periodo FROM Actividad WHERE ID_Actividad = OLD.ID_Actividad;
    IF v_periodo IS NOT NULL THEN
        CALL sp_validar_periodo_activo(v_periodo);
    END IF;
END$$

-- ----------------------------- Docente_Actividad -------------------------
CREATE TRIGGER trg_docente_actividad_bi BEFORE INSERT ON Docente_Actividad
FOR EACH ROW
BEGIN
    CALL sp_validar_docente_actividad(NEW.DNI_Docente, NEW.ID_Actividad, NEW.Rol_Asumido);
END$$

CREATE TRIGGER trg_docente_actividad_bu BEFORE UPDATE ON Docente_Actividad
FOR EACH ROW
BEGIN
    IF NEW.ID_Actividad <> OLD.ID_Actividad THEN
        CALL sp_error('Para mover al docente a otra actividad, quítelo y asígnelo de nuevo.');
    END IF;
    CALL sp_validar_docente_actividad(NEW.DNI_Docente, NEW.ID_Actividad, NEW.Rol_Asumido);
END$$

CREATE TRIGGER trg_docente_actividad_bd BEFORE DELETE ON Docente_Actividad
FOR EACH ROW
BEGIN
    DECLARE v_periodo SMALLINT;
    SELECT Periodo INTO v_periodo FROM Actividad WHERE ID_Actividad = OLD.ID_Actividad;
    IF v_periodo IS NOT NULL THEN
        CALL sp_validar_periodo_activo(v_periodo);
    END IF;
END$$

-- ----------------------------- Usuario_Sistema ---------------------------
CREATE TRIGGER trg_usuario_bi BEFORE INSERT ON Usuario_Sistema
FOR EACH ROW
BEGIN
    SET NEW.Usuario = LOWER(TRIM(NEW.Usuario));
    IF NEW.Usuario NOT REGEXP '^[a-z0-9._]{3,30}$' THEN
        CALL sp_error('El usuario debe tener de 3 a 30 caracteres: letras, números, punto o guion bajo.');
    END IF;
END$$

CREATE TRIGGER trg_usuario_bu BEFORE UPDATE ON Usuario_Sistema
FOR EACH ROW
BEGIN
    IF OLD.Rol = 'Administrador' AND OLD.Activo = 1
       AND (NEW.Rol <> 'Administrador' OR NEW.Activo = 0)
       AND (SELECT COUNT(*) FROM Usuario_Sistema
             WHERE Rol = 'Administrador' AND Activo = 1) <= 1 THEN
        CALL sp_error('Debe existir al menos un Administrador activo.');
    END IF;
END$$

CREATE TRIGGER trg_usuario_bd BEFORE DELETE ON Usuario_Sistema
FOR EACH ROW
BEGIN
    CALL sp_error('Las cuentas no se eliminan físicamente: desactívelas (baja lógica).');
END$$

CREATE TRIGGER trg_bitacora_bu BEFORE UPDATE ON Bitacora
FOR EACH ROW
BEGIN
    CALL sp_error('La bitácora de auditoría es de solo lectura.');
END$$

CREATE TRIGGER trg_bitacora_bd BEFORE DELETE ON Bitacora
FOR EACH ROW
BEGIN
    CALL sp_error('La bitácora de auditoría es de solo lectura.');
END$$

DELIMITER ;

-- =========================================================================
-- TRIGGERS DE AUDITORÍA (AFTER INSERT / UPDATE / DELETE)
-- Registran en Bitacora quién cambió qué, cuándo, y los valores antes y
-- después del cambio (en formato JSON). En Usuario_Sistema nunca se
-- registra el hash de la contraseña.
-- =========================================================================
DELIMITER $$

CREATE TRIGGER trg_periodo_ai AFTER INSERT ON Periodo_Academico
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Periodo_Academico', 'INSERT', CONCAT('Anio=', NEW.Anio_Lectivo),
            NULL,
            JSON_OBJECT('Anio_Lectivo', NEW.Anio_Lectivo, 'Estado', NEW.Estado));
END$$

CREATE TRIGGER trg_periodo_au AFTER UPDATE ON Periodo_Academico
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Periodo_Academico', 'UPDATE', CONCAT('Anio=', NEW.Anio_Lectivo),
            JSON_OBJECT('Anio_Lectivo', OLD.Anio_Lectivo, 'Estado', OLD.Estado),
            JSON_OBJECT('Anio_Lectivo', NEW.Anio_Lectivo, 'Estado', NEW.Estado));
END$$

CREATE TRIGGER trg_periodo_ad AFTER DELETE ON Periodo_Academico
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Periodo_Academico', 'DELETE', CONCAT('Anio=', OLD.Anio_Lectivo),
            JSON_OBJECT('Anio_Lectivo', OLD.Anio_Lectivo, 'Estado', OLD.Estado),
            NULL);
END$$

CREATE TRIGGER trg_grado_ai AFTER INSERT ON Grado_Escolar
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Grado_Escolar', 'INSERT', CONCAT('ID_Grado=', NEW.ID_Grado),
            NULL,
            JSON_OBJECT('ID_Grado', NEW.ID_Grado, 'Nombre', NEW.Nombre, 'Nivel', NEW.Nivel));
END$$

CREATE TRIGGER trg_grado_au AFTER UPDATE ON Grado_Escolar
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Grado_Escolar', 'UPDATE', CONCAT('ID_Grado=', NEW.ID_Grado),
            JSON_OBJECT('ID_Grado', OLD.ID_Grado, 'Nombre', OLD.Nombre, 'Nivel', OLD.Nivel),
            JSON_OBJECT('ID_Grado', NEW.ID_Grado, 'Nombre', NEW.Nombre, 'Nivel', NEW.Nivel));
END$$

CREATE TRIGGER trg_grado_ad AFTER DELETE ON Grado_Escolar
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Grado_Escolar', 'DELETE', CONCAT('ID_Grado=', OLD.ID_Grado),
            JSON_OBJECT('ID_Grado', OLD.ID_Grado, 'Nombre', OLD.Nombre, 'Nivel', OLD.Nivel),
            NULL);
END$$

CREATE TRIGGER trg_seccion_ai AFTER INSERT ON Seccion
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Seccion', 'INSERT', CONCAT('ID_Seccion=', NEW.ID_Seccion),
            NULL,
            JSON_OBJECT('ID_Seccion', NEW.ID_Seccion, 'Letra_Identificadora', NEW.Letra_Identificadora, 'ID_Grado', NEW.ID_Grado));
END$$

CREATE TRIGGER trg_seccion_au AFTER UPDATE ON Seccion
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Seccion', 'UPDATE', CONCAT('ID_Seccion=', NEW.ID_Seccion),
            JSON_OBJECT('ID_Seccion', OLD.ID_Seccion, 'Letra_Identificadora', OLD.Letra_Identificadora, 'ID_Grado', OLD.ID_Grado),
            JSON_OBJECT('ID_Seccion', NEW.ID_Seccion, 'Letra_Identificadora', NEW.Letra_Identificadora, 'ID_Grado', NEW.ID_Grado));
END$$

CREATE TRIGGER trg_seccion_ad AFTER DELETE ON Seccion
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Seccion', 'DELETE', CONCAT('ID_Seccion=', OLD.ID_Seccion),
            JSON_OBJECT('ID_Seccion', OLD.ID_Seccion, 'Letra_Identificadora', OLD.Letra_Identificadora, 'ID_Grado', OLD.ID_Grado),
            NULL);
END$$

CREATE TRIGGER trg_asignatura_ai AFTER INSERT ON Asignatura
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Asignatura', 'INSERT', CONCAT('Materia=', NEW.Nombre_Materia),
            NULL,
            JSON_OBJECT('Nombre_Materia', NEW.Nombre_Materia, 'Nivel_Exigencia', NEW.Nivel_Exigencia, 'Activo', NEW.Activo));
END$$

CREATE TRIGGER trg_asignatura_au AFTER UPDATE ON Asignatura
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Asignatura', 'UPDATE', CONCAT('Materia=', NEW.Nombre_Materia),
            JSON_OBJECT('Nombre_Materia', OLD.Nombre_Materia, 'Nivel_Exigencia', OLD.Nivel_Exigencia, 'Activo', OLD.Activo),
            JSON_OBJECT('Nombre_Materia', NEW.Nombre_Materia, 'Nivel_Exigencia', NEW.Nivel_Exigencia, 'Activo', NEW.Activo));
END$$

CREATE TRIGGER trg_asignatura_ad AFTER DELETE ON Asignatura
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Asignatura', 'DELETE', CONCAT('Materia=', OLD.Nombre_Materia),
            JSON_OBJECT('Nombre_Materia', OLD.Nombre_Materia, 'Nivel_Exigencia', OLD.Nivel_Exigencia, 'Activo', OLD.Activo),
            NULL);
END$$

CREATE TRIGGER trg_espacio_ai AFTER INSERT ON Espacio
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Espacio', 'INSERT', CONCAT('Espacio=', NEW.Denominacion),
            NULL,
            JSON_OBJECT('Denominacion', NEW.Denominacion, 'Categoria', NEW.Categoria, 'Limite_Grupos', NEW.Limite_Grupos, 'Activo', NEW.Activo));
END$$

CREATE TRIGGER trg_espacio_au AFTER UPDATE ON Espacio
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Espacio', 'UPDATE', CONCAT('Espacio=', NEW.Denominacion),
            JSON_OBJECT('Denominacion', OLD.Denominacion, 'Categoria', OLD.Categoria, 'Limite_Grupos', OLD.Limite_Grupos, 'Activo', OLD.Activo),
            JSON_OBJECT('Denominacion', NEW.Denominacion, 'Categoria', NEW.Categoria, 'Limite_Grupos', NEW.Limite_Grupos, 'Activo', NEW.Activo));
END$$

CREATE TRIGGER trg_espacio_ad AFTER DELETE ON Espacio
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Espacio', 'DELETE', CONCAT('Espacio=', OLD.Denominacion),
            JSON_OBJECT('Denominacion', OLD.Denominacion, 'Categoria', OLD.Categoria, 'Limite_Grupos', OLD.Limite_Grupos, 'Activo', OLD.Activo),
            NULL);
END$$

CREATE TRIGGER trg_docente_ai AFTER INSERT ON Docente
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Docente', 'INSERT', CONCAT('DNI=', NEW.DNI),
            NULL,
            JSON_OBJECT('DNI', NEW.DNI, 'Nombre', NEW.Nombre, 'Modalidad', NEW.Modalidad, 'Condicion_Especial', NEW.Condicion_Especial, 'Activo', NEW.Activo));
END$$

CREATE TRIGGER trg_docente_au AFTER UPDATE ON Docente
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Docente', 'UPDATE', CONCAT('DNI=', NEW.DNI),
            JSON_OBJECT('DNI', OLD.DNI, 'Nombre', OLD.Nombre, 'Modalidad', OLD.Modalidad, 'Condicion_Especial', OLD.Condicion_Especial, 'Activo', OLD.Activo),
            JSON_OBJECT('DNI', NEW.DNI, 'Nombre', NEW.Nombre, 'Modalidad', NEW.Modalidad, 'Condicion_Especial', NEW.Condicion_Especial, 'Activo', NEW.Activo));
END$$

CREATE TRIGGER trg_docente_ad AFTER DELETE ON Docente
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Docente', 'DELETE', CONCAT('DNI=', OLD.DNI),
            JSON_OBJECT('DNI', OLD.DNI, 'Nombre', OLD.Nombre, 'Modalidad', OLD.Modalidad, 'Condicion_Especial', OLD.Condicion_Especial, 'Activo', OLD.Activo),
            NULL);
END$$

CREATE TRIGGER trg_grado_materia_ai AFTER INSERT ON Grado_Materia
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Grado_Materia', 'INSERT', CONCAT('Grado=', NEW.ID_Grado, '; Materia=', NEW.Nombre_Asignatura),
            NULL,
            JSON_OBJECT('Nombre_Asignatura', NEW.Nombre_Asignatura, 'ID_Grado', NEW.ID_Grado, 'Horas_Exigidas', NEW.Horas_Exigidas));
END$$

CREATE TRIGGER trg_grado_materia_au AFTER UPDATE ON Grado_Materia
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Grado_Materia', 'UPDATE', CONCAT('Grado=', NEW.ID_Grado, '; Materia=', NEW.Nombre_Asignatura),
            JSON_OBJECT('Nombre_Asignatura', OLD.Nombre_Asignatura, 'ID_Grado', OLD.ID_Grado, 'Horas_Exigidas', OLD.Horas_Exigidas),
            JSON_OBJECT('Nombre_Asignatura', NEW.Nombre_Asignatura, 'ID_Grado', NEW.ID_Grado, 'Horas_Exigidas', NEW.Horas_Exigidas));
END$$

CREATE TRIGGER trg_grado_materia_ad AFTER DELETE ON Grado_Materia
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Grado_Materia', 'DELETE', CONCAT('Grado=', OLD.ID_Grado, '; Materia=', OLD.Nombre_Asignatura),
            JSON_OBJECT('Nombre_Asignatura', OLD.Nombre_Asignatura, 'ID_Grado', OLD.ID_Grado, 'Horas_Exigidas', OLD.Horas_Exigidas),
            NULL);
END$$

CREATE TRIGGER trg_especialidad_ai AFTER INSERT ON Especializado_En
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Especializado_En', 'INSERT', CONCAT('DNI=', NEW.DNI_Docente, '; Materia=', NEW.Nombre_Asignatura),
            NULL,
            JSON_OBJECT('Nombre_Asignatura', NEW.Nombre_Asignatura, 'DNI_Docente', NEW.DNI_Docente));
END$$

CREATE TRIGGER trg_especialidad_au AFTER UPDATE ON Especializado_En
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Especializado_En', 'UPDATE', CONCAT('DNI=', NEW.DNI_Docente, '; Materia=', NEW.Nombre_Asignatura),
            JSON_OBJECT('Nombre_Asignatura', OLD.Nombre_Asignatura, 'DNI_Docente', OLD.DNI_Docente),
            JSON_OBJECT('Nombre_Asignatura', NEW.Nombre_Asignatura, 'DNI_Docente', NEW.DNI_Docente));
END$$

CREATE TRIGGER trg_especialidad_ad AFTER DELETE ON Especializado_En
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Especializado_En', 'DELETE', CONCAT('DNI=', OLD.DNI_Docente, '; Materia=', OLD.Nombre_Asignatura),
            JSON_OBJECT('Nombre_Asignatura', OLD.Nombre_Asignatura, 'DNI_Docente', OLD.DNI_Docente),
            NULL);
END$$

CREATE TRIGGER trg_actividad_ai AFTER INSERT ON Actividad
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Actividad', 'INSERT', CONCAT('ID_Actividad=', NEW.ID_Actividad),
            NULL,
            JSON_OBJECT('ID_Actividad', NEW.ID_Actividad, 'Hora_Inicio', NEW.Hora_Inicio, 'Hora_Fin', NEW.Hora_Fin, 'Dia', NEW.Dia, 'Naturaleza', NEW.Naturaleza, 'Materia_Asignatura', NEW.Materia_Asignatura, 'Periodo', NEW.Periodo, 'Espacio', NEW.Espacio));
END$$

CREATE TRIGGER trg_actividad_au AFTER UPDATE ON Actividad
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Actividad', 'UPDATE', CONCAT('ID_Actividad=', NEW.ID_Actividad),
            JSON_OBJECT('ID_Actividad', OLD.ID_Actividad, 'Hora_Inicio', OLD.Hora_Inicio, 'Hora_Fin', OLD.Hora_Fin, 'Dia', OLD.Dia, 'Naturaleza', OLD.Naturaleza, 'Materia_Asignatura', OLD.Materia_Asignatura, 'Periodo', OLD.Periodo, 'Espacio', OLD.Espacio),
            JSON_OBJECT('ID_Actividad', NEW.ID_Actividad, 'Hora_Inicio', NEW.Hora_Inicio, 'Hora_Fin', NEW.Hora_Fin, 'Dia', NEW.Dia, 'Naturaleza', NEW.Naturaleza, 'Materia_Asignatura', NEW.Materia_Asignatura, 'Periodo', NEW.Periodo, 'Espacio', NEW.Espacio));
END$$

CREATE TRIGGER trg_actividad_ad AFTER DELETE ON Actividad
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Actividad', 'DELETE', CONCAT('ID_Actividad=', OLD.ID_Actividad),
            JSON_OBJECT('ID_Actividad', OLD.ID_Actividad, 'Hora_Inicio', OLD.Hora_Inicio, 'Hora_Fin', OLD.Hora_Fin, 'Dia', OLD.Dia, 'Naturaleza', OLD.Naturaleza, 'Materia_Asignatura', OLD.Materia_Asignatura, 'Periodo', OLD.Periodo, 'Espacio', OLD.Espacio),
            NULL);
END$$

CREATE TRIGGER trg_seccion_periodo_ai AFTER INSERT ON Seccion_Periodo
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Seccion_Periodo', 'INSERT', CONCAT('ID_Actividad=', NEW.ID_Actividad, '; ID_Seccion=', NEW.ID_Seccion),
            NULL,
            JSON_OBJECT('ID_Actividad', NEW.ID_Actividad, 'ID_Seccion', NEW.ID_Seccion));
END$$

CREATE TRIGGER trg_seccion_periodo_au AFTER UPDATE ON Seccion_Periodo
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Seccion_Periodo', 'UPDATE', CONCAT('ID_Actividad=', NEW.ID_Actividad, '; ID_Seccion=', NEW.ID_Seccion),
            JSON_OBJECT('ID_Actividad', OLD.ID_Actividad, 'ID_Seccion', OLD.ID_Seccion),
            JSON_OBJECT('ID_Actividad', NEW.ID_Actividad, 'ID_Seccion', NEW.ID_Seccion));
END$$

CREATE TRIGGER trg_seccion_periodo_ad AFTER DELETE ON Seccion_Periodo
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Seccion_Periodo', 'DELETE', CONCAT('ID_Actividad=', OLD.ID_Actividad, '; ID_Seccion=', OLD.ID_Seccion),
            JSON_OBJECT('ID_Actividad', OLD.ID_Actividad, 'ID_Seccion', OLD.ID_Seccion),
            NULL);
END$$

CREATE TRIGGER trg_docente_actividad_ai AFTER INSERT ON Docente_Actividad
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Docente_Actividad', 'INSERT', CONCAT('DNI=', NEW.DNI_Docente, '; ID_Actividad=', NEW.ID_Actividad),
            NULL,
            JSON_OBJECT('DNI_Docente', NEW.DNI_Docente, 'ID_Actividad', NEW.ID_Actividad, 'Rol_Asumido', NEW.Rol_Asumido));
END$$

CREATE TRIGGER trg_docente_actividad_au AFTER UPDATE ON Docente_Actividad
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Docente_Actividad', 'UPDATE', CONCAT('DNI=', NEW.DNI_Docente, '; ID_Actividad=', NEW.ID_Actividad),
            JSON_OBJECT('DNI_Docente', OLD.DNI_Docente, 'ID_Actividad', OLD.ID_Actividad, 'Rol_Asumido', OLD.Rol_Asumido),
            JSON_OBJECT('DNI_Docente', NEW.DNI_Docente, 'ID_Actividad', NEW.ID_Actividad, 'Rol_Asumido', NEW.Rol_Asumido));
END$$

CREATE TRIGGER trg_docente_actividad_ad AFTER DELETE ON Docente_Actividad
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Docente_Actividad', 'DELETE', CONCAT('DNI=', OLD.DNI_Docente, '; ID_Actividad=', OLD.ID_Actividad),
            JSON_OBJECT('DNI_Docente', OLD.DNI_Docente, 'ID_Actividad', OLD.ID_Actividad, 'Rol_Asumido', OLD.Rol_Asumido),
            NULL);
END$$

CREATE TRIGGER trg_usuario_ai AFTER INSERT ON Usuario_Sistema
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Usuario_Sistema', 'INSERT', CONCAT('Usuario=', NEW.Usuario),
            NULL,
            JSON_OBJECT('Usuario', NEW.Usuario, 'Nombre_Completo', NEW.Nombre_Completo, 'Rol', NEW.Rol, 'Activo', NEW.Activo, 'Ultimo_Acceso', NEW.Ultimo_Acceso));
END$$

CREATE TRIGGER trg_usuario_au AFTER UPDATE ON Usuario_Sistema
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Usuario_Sistema', 'UPDATE', CONCAT('Usuario=', NEW.Usuario),
            JSON_OBJECT('Usuario', OLD.Usuario, 'Nombre_Completo', OLD.Nombre_Completo, 'Rol', OLD.Rol, 'Activo', OLD.Activo, 'Ultimo_Acceso', OLD.Ultimo_Acceso),
            JSON_OBJECT('Usuario', NEW.Usuario, 'Nombre_Completo', NEW.Nombre_Completo, 'Rol', NEW.Rol, 'Activo', NEW.Activo, 'Ultimo_Acceso', NEW.Ultimo_Acceso));
END$$

CREATE TRIGGER trg_usuario_ad AFTER DELETE ON Usuario_Sistema
FOR EACH ROW
BEGIN
    INSERT INTO Bitacora (Usuario, Tabla, Operacion, Clave_Registro, Datos_Anteriores, Datos_Nuevos)
    VALUES (fn_usuario_actual(), 'Usuario_Sistema', 'DELETE', CONCAT('Usuario=', OLD.Usuario),
            JSON_OBJECT('Usuario', OLD.Usuario, 'Nombre_Completo', OLD.Nombre_Completo, 'Rol', OLD.Rol, 'Activo', OLD.Activo, 'Ultimo_Acceso', OLD.Ultimo_Acceso),
            NULL);
END$$

DELIMITER ;
