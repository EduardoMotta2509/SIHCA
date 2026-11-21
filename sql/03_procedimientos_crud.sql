-- =========================================================================
-- ARCHIVO  : 03_procedimientos_crud.sql
-- CONTENIDO: Procedimientos almacenados que usa el aplicativo para
--            INSERTAR, MODIFICAR, ELIMINAR y LISTAR registros.
--            El aplicativo NUNCA ejecuta INSERT/UPDATE/DELETE directos:
--            siempre invoca estos procedimientos (CALL), y los triggers
--            del archivo 02 validan las reglas de negocio y auditan.
-- Convenciones:
--   sp_<entidad>_listar | _insertar | _actualizar | _eliminar | _reactivar
--   Parámetros con prefijo p_, variables locales con prefijo v_.
--   Eliminar = borrado físico si el registro no tiene historial;
--              borrado lógico (Activo = 0) si ya tiene historial.
-- =========================================================================

-- Garantiza que tildes y eñes se interpreten en UTF-8 con cualquier cliente
SET NAMES utf8mb4;

USE bd_horarios_esclavas;

DELIMITER $$

-- =========================================================================
-- 0. SESIÓN Y PERMISOS
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_verificar_permiso$$
-- Si la llamada viene del aplicativo (@rol_app definido) se comprueba el rol.
-- Llamadas directas desde MySQL Workbench (sin sesión del aplicativo) se
-- consideran del administrador de la base de datos.
CREATE PROCEDURE sp_verificar_permiso(IN p_nivel VARCHAR(15))
BEGIN
    IF @rol_app IS NOT NULL THEN
        IF p_nivel = 'escritura' AND @rol_app NOT IN ('Administrador', 'Coordinador') THEN
            CALL sp_error('Su perfil (Consulta) solo permite ver información y reportes.');
        ELSEIF p_nivel = 'admin' AND @rol_app <> 'Administrador' THEN
            CALL sp_error('Esta opción está reservada al perfil Administrador.');
        END IF;
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_login$$
CREATE PROCEDURE sp_login(IN p_usuario VARCHAR(30), IN p_clave VARCHAR(100))
BEGIN
    DECLARE v_usuario VARCHAR(30);
    DECLARE v_nombre VARCHAR(100);
    DECLARE v_rol VARCHAR(20);

    SELECT Usuario, Nombre_Completo, Rol INTO v_usuario, v_nombre, v_rol
      FROM Usuario_Sistema
     WHERE Usuario = LOWER(TRIM(p_usuario))
       AND Clave_Hash = SHA2(p_clave, 256)
       AND Activo = 1;

    IF v_usuario IS NULL THEN
        CALL sp_error('Usuario o contraseña incorrectos, o la cuenta está desactivada.');
    END IF;

    -- Variables de sesión que usan los triggers de auditoría y los permisos
    SET @usuario_app = v_usuario;
    SET @rol_app = v_rol;

    UPDATE Usuario_Sistema SET Ultimo_Acceso = NOW() WHERE Usuario = v_usuario;

    SELECT v_usuario AS Usuario, v_nombre AS Nombre, v_rol AS Rol;
END$$

DROP PROCEDURE IF EXISTS sp_logout$$
CREATE PROCEDURE sp_logout()
BEGIN
    SET @usuario_app = NULL;
    SET @rol_app = NULL;
END$$

-- =========================================================================
-- 1. PERIODO ACADÉMICO
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_periodo_listar$$
CREATE PROCEDURE sp_periodo_listar()
BEGIN
    SELECT p.Anio_Lectivo AS `Año lectivo`,
           p.Estado,
           (SELECT COUNT(*) FROM Actividad a WHERE a.Periodo = p.Anio_Lectivo) AS `Bloques programados`,
           (SELECT COUNT(DISTINCT sp.ID_Seccion)
              FROM Actividad a JOIN Seccion_Periodo sp ON sp.ID_Actividad = a.ID_Actividad
             WHERE a.Periodo = p.Anio_Lectivo) AS `Secciones con horario`
      FROM Periodo_Academico p
     ORDER BY p.Anio_Lectivo DESC;
END$$

DROP PROCEDURE IF EXISTS sp_periodo_insertar$$
CREATE PROCEDURE sp_periodo_insertar(IN p_anio SMALLINT, IN p_estado VARCHAR(10))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Periodo_Academico WHERE Anio_Lectivo = p_anio) THEN
        CALL sp_error(CONCAT('El periodo ', p_anio, ' ya está registrado.'));
    END IF;
    INSERT INTO Periodo_Academico (Anio_Lectivo, Estado) VALUES (p_anio, IFNULL(p_estado, 'Activo'));
END$$

DROP PROCEDURE IF EXISTS sp_periodo_actualizar$$
CREATE PROCEDURE sp_periodo_actualizar(IN p_anio SMALLINT, IN p_estado VARCHAR(10))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Periodo_Academico WHERE Anio_Lectivo = p_anio) THEN
        CALL sp_error(CONCAT('El periodo ', p_anio, ' no existe.'));
    END IF;
    UPDATE Periodo_Academico SET Estado = p_estado WHERE Anio_Lectivo = p_anio;
END$$

DROP PROCEDURE IF EXISTS sp_periodo_eliminar$$
CREATE PROCEDURE sp_periodo_eliminar(IN p_anio SMALLINT)
BEGIN
    CALL sp_verificar_permiso('admin');
    DELETE FROM Periodo_Academico WHERE Anio_Lectivo = p_anio;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error(CONCAT('El periodo ', p_anio, ' no existe.'));
    END IF;
END$$

-- Reutiliza el horario de un año anterior como punto de partida del nuevo
-- año (aprovecha el registro histórico). Copia bloques y secciones; los
-- docentes solo se copian si siguen vigentes y son especialistas.
DROP PROCEDURE IF EXISTS sp_periodo_replicar_horario$$
CREATE PROCEDURE sp_periodo_replicar_horario(
    IN p_origen SMALLINT, IN p_destino SMALLINT,
    OUT p_bloques INT, OUT p_sin_docente INT)
BEGIN
    DECLARE v_base INT;
    DECLARE v_min INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    CALL sp_verificar_permiso('escritura');
    IF p_origen = p_destino THEN
        CALL sp_error('El periodo de origen y el de destino deben ser distintos.');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM Actividad WHERE Periodo = p_origen) THEN
        CALL sp_error(CONCAT('El periodo ', p_origen, ' no tiene horario para copiar.'));
    END IF;
    CALL sp_validar_periodo_activo(p_destino);
    IF EXISTS (SELECT 1 FROM Actividad WHERE Periodo = p_destino) THEN
        CALL sp_error(CONCAT('El periodo ', p_destino, ' ya tiene horario; la copia solo se hace sobre un periodo vacío.'));
    END IF;

    START TRANSACTION;

    SELECT IFNULL(MAX(ID_Actividad), 1000) INTO v_base FROM Actividad;
    SELECT MIN(ID_Actividad) INTO v_min FROM Actividad WHERE Periodo = p_origen;

    INSERT INTO Actividad (ID_Actividad, Hora_Inicio, Hora_Fin, Dia, Naturaleza,
                           Materia_Asignatura, Periodo, Espacio)
    SELECT a.ID_Actividad - v_min + v_base + 1, a.Hora_Inicio, a.Hora_Fin, a.Dia, a.Naturaleza,
           a.Materia_Asignatura, p_destino, a.Espacio
      FROM Actividad a
     WHERE a.Periodo = p_origen
     ORDER BY a.ID_Actividad;
    SET p_bloques = ROW_COUNT();

    INSERT INTO Seccion_Periodo (ID_Actividad, ID_Seccion)
    SELECT sp.ID_Actividad - v_min + v_base + 1, sp.ID_Seccion
      FROM Seccion_Periodo sp
      JOIN Actividad a ON a.ID_Actividad = sp.ID_Actividad
     WHERE a.Periodo = p_origen
     ORDER BY sp.ID_Actividad, sp.ID_Seccion;

    INSERT INTO Docente_Actividad (DNI_Docente, ID_Actividad, Rol_Asumido)
    SELECT da.DNI_Docente, da.ID_Actividad - v_min + v_base + 1, da.Rol_Asumido
      FROM Docente_Actividad da
      JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
      JOIN Docente d ON d.DNI = da.DNI_Docente
     WHERE a.Periodo = p_origen
       AND d.Activo = 1
       AND (a.Naturaleza <> 'Clase' OR EXISTS (
              SELECT 1 FROM Especializado_En e
               WHERE e.DNI_Docente = da.DNI_Docente
                 AND e.Nombre_Asignatura = a.Materia_Asignatura))
       AND (a.Naturaleza <> 'Consejo' OR d.Condicion_Especial = 'Miembro del Consejo Directivo')
     ORDER BY da.ID_Actividad, da.DNI_Docente;

    SELECT COUNT(*) INTO p_sin_docente
      FROM Actividad a
     WHERE a.Periodo = p_destino
       AND NOT EXISTS (SELECT 1 FROM Docente_Actividad da WHERE da.ID_Actividad = a.ID_Actividad);

    COMMIT;
END$$

-- =========================================================================
-- 2. GRADO ESCOLAR
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_grado_listar$$
CREATE PROCEDURE sp_grado_listar()
BEGIN
    SELECT g.ID_Grado AS ID,
           g.Nombre AS Grado,
           g.Nivel,
           (SELECT COUNT(*) FROM Seccion s WHERE s.ID_Grado = g.ID_Grado) AS Secciones,
           (SELECT IFNULL(SUM(gm.Horas_Exigidas), 0) FROM Grado_Materia gm
             WHERE gm.ID_Grado = g.ID_Grado) AS `Horas del plan`,
           fn_tope_horas_nivel(g.Nivel) AS `Tope del nivel`
      FROM Grado_Escolar g
     ORDER BY FIELD(g.Nivel, 'Inicial', 'Primaria', 'Secundaria'), g.ID_Grado;
END$$

DROP PROCEDURE IF EXISTS sp_grado_insertar$$
CREATE PROCEDURE sp_grado_insertar(IN p_id INT, IN p_nombre VARCHAR(30), IN p_nivel VARCHAR(20))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Grado_Escolar WHERE ID_Grado = p_id) THEN
        CALL sp_error(CONCAT('Ya existe un grado con ID ', p_id, '.'));
    END IF;
    INSERT INTO Grado_Escolar (ID_Grado, Nombre, Nivel) VALUES (p_id, p_nombre, p_nivel);
END$$

DROP PROCEDURE IF EXISTS sp_grado_actualizar$$
CREATE PROCEDURE sp_grado_actualizar(IN p_id INT, IN p_nombre VARCHAR(30), IN p_nivel VARCHAR(20))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Grado_Escolar WHERE ID_Grado = p_id) THEN
        CALL sp_error(CONCAT('No existe el grado con ID ', p_id, '.'));
    END IF;
    UPDATE Grado_Escolar SET Nombre = p_nombre, Nivel = p_nivel WHERE ID_Grado = p_id;
END$$

DROP PROCEDURE IF EXISTS sp_grado_eliminar$$
CREATE PROCEDURE sp_grado_eliminar(IN p_id INT)
BEGIN
    CALL sp_verificar_permiso('admin');
    DELETE FROM Grado_Escolar WHERE ID_Grado = p_id;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error(CONCAT('No existe el grado con ID ', p_id, '.'));
    END IF;
END$$

-- =========================================================================
-- 3. SECCIÓN
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_seccion_listar$$
CREATE PROCEDURE sp_seccion_listar(IN p_id_grado INT)
BEGIN
    SELECT s.ID_Seccion AS ID,
           CONCAT(g.Nombre, ' ', s.Letra_Identificadora, ' - ', g.Nivel) AS `Descripción`,
           g.Nivel,
           g.Nombre AS Grado,
           s.Letra_Identificadora AS `Sección`,
           s.ID_Grado AS `ID grado`,
           (SELECT COUNT(*) FROM Seccion_Periodo sp
              JOIN Actividad a ON a.ID_Actividad = sp.ID_Actividad
              JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
             WHERE sp.ID_Seccion = s.ID_Seccion AND p.Estado = 'Activo') AS `Bloques (periodo activo)`
      FROM Seccion s
      JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
     WHERE p_id_grado IS NULL OR s.ID_Grado = p_id_grado
     ORDER BY FIELD(g.Nivel, 'Inicial', 'Primaria', 'Secundaria'), g.ID_Grado, s.Letra_Identificadora;
END$$

DROP PROCEDURE IF EXISTS sp_seccion_insertar$$
CREATE PROCEDURE sp_seccion_insertar(IN p_id INT, IN p_letra VARCHAR(2), IN p_id_grado INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Seccion WHERE ID_Seccion = p_id) THEN
        CALL sp_error(CONCAT('Ya existe una sección con ID ', p_id, '.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Seccion WHERE ID_Grado = p_id_grado AND Letra_Identificadora = UPPER(p_letra)) THEN
        CALL sp_error(CONCAT('El grado ya tiene una sección ', UPPER(p_letra), ' (máximo 2 secciones: A y B).'));
    END IF;
    INSERT INTO Seccion (ID_Seccion, Letra_Identificadora, ID_Grado) VALUES (p_id, p_letra, p_id_grado);
END$$

DROP PROCEDURE IF EXISTS sp_seccion_actualizar$$
CREATE PROCEDURE sp_seccion_actualizar(IN p_id INT, IN p_letra VARCHAR(2), IN p_id_grado INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Seccion WHERE ID_Seccion = p_id) THEN
        CALL sp_error(CONCAT('No existe la sección con ID ', p_id, '.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Seccion WHERE ID_Grado = p_id_grado
                AND Letra_Identificadora = UPPER(p_letra) AND ID_Seccion <> p_id) THEN
        CALL sp_error(CONCAT('El grado ya tiene una sección ', UPPER(p_letra), '.'));
    END IF;
    UPDATE Seccion SET Letra_Identificadora = p_letra, ID_Grado = p_id_grado WHERE ID_Seccion = p_id;
END$$

DROP PROCEDURE IF EXISTS sp_seccion_eliminar$$
CREATE PROCEDURE sp_seccion_eliminar(IN p_id INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    DELETE FROM Seccion WHERE ID_Seccion = p_id;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error(CONCAT('No existe la sección con ID ', p_id, '.'));
    END IF;
END$$

-- =========================================================================
-- 4. ASIGNATURA
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_asignatura_listar$$
CREATE PROCEDURE sp_asignatura_listar(IN p_incluir_bajas TINYINT)
BEGIN
    SELECT a.Nombre_Materia AS Asignatura,
           a.Nivel_Exigencia AS `Exigencia cognitiva`,
           IF(a.Activo = 1, 'Vigente', 'De baja') AS Estado,
           (SELECT COUNT(*) FROM Especializado_En e
              JOIN Docente d ON d.DNI = e.DNI_Docente
             WHERE e.Nombre_Asignatura = a.Nombre_Materia AND d.Activo = 1) AS Especialistas,
           (SELECT COUNT(*) FROM Grado_Materia gm WHERE gm.Nombre_Asignatura = a.Nombre_Materia) AS `Grados que la llevan`
      FROM Asignatura a
     WHERE p_incluir_bajas = 1 OR a.Activo = 1
     ORDER BY a.Activo DESC, a.Nombre_Materia;
END$$

DROP PROCEDURE IF EXISTS sp_asignatura_insertar$$
CREATE PROCEDURE sp_asignatura_insertar(IN p_nombre VARCHAR(60), IN p_exigencia VARCHAR(30))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Asignatura WHERE Nombre_Materia = TRIM(p_nombre)) THEN
        CALL sp_error(CONCAT('La asignatura "', TRIM(p_nombre), '" ya existe.'));
    END IF;
    INSERT INTO Asignatura (Nombre_Materia, Nivel_Exigencia) VALUES (p_nombre, p_exigencia);
END$$

-- Permite renombrar: el cambio se propaga a horarios, planes y especialidades
-- gracias a ON UPDATE CASCADE.
DROP PROCEDURE IF EXISTS sp_asignatura_actualizar$$
CREATE PROCEDURE sp_asignatura_actualizar(
    IN p_nombre_actual VARCHAR(60), IN p_nombre_nuevo VARCHAR(60), IN p_exigencia VARCHAR(30))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Asignatura WHERE Nombre_Materia = p_nombre_actual) THEN
        CALL sp_error(CONCAT('La asignatura "', p_nombre_actual, '" no existe.'));
    END IF;
    IF TRIM(p_nombre_nuevo) <> p_nombre_actual
       AND EXISTS (SELECT 1 FROM Asignatura WHERE Nombre_Materia = TRIM(p_nombre_nuevo)) THEN
        CALL sp_error(CONCAT('Ya existe otra asignatura llamada "', TRIM(p_nombre_nuevo), '".'));
    END IF;
    IF TRIM(p_nombre_nuevo) <> p_nombre_actual
       AND EXISTS (SELECT 1 FROM Actividad a JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
                    WHERE a.Materia_Asignatura = p_nombre_actual AND p.Estado = 'Cerrado') THEN
        CALL sp_error('No se puede renombrar: la asignatura figura en horarios de periodos cerrados (histórico).');
    END IF;
    UPDATE Asignatura
       SET Nombre_Materia = p_nombre_nuevo, Nivel_Exigencia = p_exigencia
     WHERE Nombre_Materia = p_nombre_actual;
END$$

DROP PROCEDURE IF EXISTS sp_asignatura_eliminar$$
CREATE PROCEDURE sp_asignatura_eliminar(IN p_nombre VARCHAR(60), OUT p_resultado VARCHAR(120))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Asignatura WHERE Nombre_Materia = p_nombre) THEN
        CALL sp_error(CONCAT('La asignatura "', p_nombre, '" no existe.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Actividad WHERE Materia_Asignatura = p_nombre)
       OR EXISTS (SELECT 1 FROM Grado_Materia WHERE Nombre_Asignatura = p_nombre) THEN
        UPDATE Asignatura SET Activo = 0 WHERE Nombre_Materia = p_nombre;
        SET p_resultado = 'La asignatura tiene historial: se dio de baja (borrado lógico).';
    ELSE
        DELETE FROM Asignatura WHERE Nombre_Materia = p_nombre;
        SET p_resultado = 'La asignatura no tenía historial: se eliminó definitivamente.';
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_asignatura_reactivar$$
CREATE PROCEDURE sp_asignatura_reactivar(IN p_nombre VARCHAR(60))
BEGIN
    CALL sp_verificar_permiso('escritura');
    UPDATE Asignatura SET Activo = 1 WHERE Nombre_Materia = p_nombre;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('La asignatura no existe o ya estaba vigente.');
    END IF;
END$$

-- =========================================================================
-- 5. ESPACIO
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_espacio_listar$$
CREATE PROCEDURE sp_espacio_listar(IN p_incluir_bajas TINYINT)
BEGIN
    SELECT e.Denominacion AS `Denominación`,
           e.Categoria AS `Categoría`,
           e.Limite_Grupos AS `Límite de grupos`,
           IF(e.Activo = 1, 'Disponible', 'De baja') AS Estado,
           (SELECT COUNT(*) FROM Actividad a JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
             WHERE a.Espacio = e.Denominacion AND p.Estado = 'Activo') AS `Bloques (periodo activo)`
      FROM Espacio e
     WHERE p_incluir_bajas = 1 OR e.Activo = 1
     ORDER BY e.Activo DESC, e.Categoria, e.Denominacion;
END$$

DROP PROCEDURE IF EXISTS sp_espacio_insertar$$
CREATE PROCEDURE sp_espacio_insertar(IN p_denominacion VARCHAR(60), IN p_categoria VARCHAR(30), IN p_limite SMALLINT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Espacio WHERE Denominacion = TRIM(p_denominacion)) THEN
        CALL sp_error(CONCAT('El espacio "', TRIM(p_denominacion), '" ya existe.'));
    END IF;
    INSERT INTO Espacio (Denominacion, Categoria, Limite_Grupos) VALUES (p_denominacion, p_categoria, p_limite);
END$$

DROP PROCEDURE IF EXISTS sp_espacio_actualizar$$
CREATE PROCEDURE sp_espacio_actualizar(
    IN p_denominacion_actual VARCHAR(60), IN p_denominacion_nueva VARCHAR(60),
    IN p_categoria VARCHAR(30), IN p_limite SMALLINT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Espacio WHERE Denominacion = p_denominacion_actual) THEN
        CALL sp_error(CONCAT('El espacio "', p_denominacion_actual, '" no existe.'));
    END IF;
    IF TRIM(p_denominacion_nueva) <> p_denominacion_actual
       AND EXISTS (SELECT 1 FROM Espacio WHERE Denominacion = TRIM(p_denominacion_nueva)) THEN
        CALL sp_error(CONCAT('Ya existe otro espacio llamado "', TRIM(p_denominacion_nueva), '".'));
    END IF;
    IF TRIM(p_denominacion_nueva) <> p_denominacion_actual
       AND EXISTS (SELECT 1 FROM Actividad a JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
                    WHERE a.Espacio = p_denominacion_actual AND p.Estado = 'Cerrado') THEN
        CALL sp_error('No se puede renombrar: el espacio figura en horarios de periodos cerrados (histórico).');
    END IF;
    UPDATE Espacio
       SET Denominacion = p_denominacion_nueva, Categoria = p_categoria, Limite_Grupos = p_limite
     WHERE Denominacion = p_denominacion_actual;
END$$

DROP PROCEDURE IF EXISTS sp_espacio_eliminar$$
CREATE PROCEDURE sp_espacio_eliminar(IN p_denominacion VARCHAR(60), OUT p_resultado VARCHAR(120))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Espacio WHERE Denominacion = p_denominacion) THEN
        CALL sp_error(CONCAT('El espacio "', p_denominacion, '" no existe.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Actividad WHERE Espacio = p_denominacion) THEN
        UPDATE Espacio SET Activo = 0 WHERE Denominacion = p_denominacion;
        SET p_resultado = 'El espacio tiene historial de uso: se dio de baja (borrado lógico).';
    ELSE
        DELETE FROM Espacio WHERE Denominacion = p_denominacion;
        SET p_resultado = 'El espacio no tenía historial: se eliminó definitivamente.';
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_espacio_reactivar$$
CREATE PROCEDURE sp_espacio_reactivar(IN p_denominacion VARCHAR(60))
BEGIN
    CALL sp_verificar_permiso('escritura');
    UPDATE Espacio SET Activo = 1 WHERE Denominacion = p_denominacion;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('El espacio no existe o ya estaba disponible.');
    END IF;
END$$

-- =========================================================================
-- 6. DOCENTE Y ESPECIALIDADES
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_docente_listar$$
CREATE PROCEDURE sp_docente_listar(IN p_filtro VARCHAR(100), IN p_incluir_bajas TINYINT)
BEGIN
    SELECT d.DNI,
           d.Nombre AS `Apellidos y nombres`,
           d.Modalidad,
           IFNULL(d.Condicion_Especial, 'Docente') AS `Condición especial`,
           IF(d.Activo = 1, 'Vigente', 'De baja') AS Estado,
           IFNULL((SELECT GROUP_CONCAT(e.Nombre_Asignatura ORDER BY e.Nombre_Asignatura SEPARATOR ', ')
                     FROM Especializado_En e WHERE e.DNI_Docente = d.DNI), '-') AS Especialidades,
           (SELECT COUNT(*) FROM Docente_Actividad da
              JOIN Actividad a ON a.ID_Actividad = da.ID_Actividad
              JOIN Periodo_Academico p ON p.Anio_Lectivo = a.Periodo
             WHERE da.DNI_Docente = d.DNI AND p.Estado = 'Activo') AS `Bloques (periodo activo)`
      FROM Docente d
     WHERE (p_incluir_bajas = 1 OR d.Activo = 1)
       AND (p_filtro IS NULL OR p_filtro = ''
            OR d.Nombre LIKE CONCAT('%', p_filtro, '%')
            OR d.DNI LIKE CONCAT(p_filtro, '%'))
     ORDER BY d.Activo DESC, d.Nombre;
END$$

DROP PROCEDURE IF EXISTS sp_docente_insertar$$
CREATE PROCEDURE sp_docente_insertar(
    IN p_dni CHAR(8), IN p_nombre VARCHAR(100), IN p_modalidad VARCHAR(25), IN p_condicion VARCHAR(50))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF p_dni IS NULL OR TRIM(p_dni) NOT REGEXP '^[0-9]{8}$' THEN
        CALL sp_error('El DNI debe tener exactamente 8 dígitos numéricos.');
    END IF;
    IF EXISTS (SELECT 1 FROM Docente WHERE DNI = TRIM(p_dni)) THEN
        CALL sp_error(CONCAT('Ya existe un docente registrado con DNI ', TRIM(p_dni), '.'));
    END IF;
    INSERT INTO Docente (DNI, Nombre, Modalidad, Condicion_Especial)
    VALUES (p_dni, p_nombre, p_modalidad, p_condicion);
END$$

DROP PROCEDURE IF EXISTS sp_docente_actualizar$$
CREATE PROCEDURE sp_docente_actualizar(
    IN p_dni CHAR(8), IN p_nombre VARCHAR(100), IN p_modalidad VARCHAR(25), IN p_condicion VARCHAR(50))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Docente WHERE DNI = p_dni) THEN
        CALL sp_error(CONCAT('No existe un docente con DNI ', p_dni, '.'));
    END IF;
    UPDATE Docente
       SET Nombre = p_nombre, Modalidad = p_modalidad, Condicion_Especial = p_condicion
     WHERE DNI = p_dni;
END$$

DROP PROCEDURE IF EXISTS sp_docente_eliminar$$
CREATE PROCEDURE sp_docente_eliminar(IN p_dni CHAR(8), OUT p_resultado VARCHAR(120))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Docente WHERE DNI = p_dni) THEN
        CALL sp_error(CONCAT('No existe un docente con DNI ', p_dni, '.'));
    END IF;
    IF EXISTS (SELECT 1 FROM Docente_Actividad WHERE DNI_Docente = p_dni) THEN
        UPDATE Docente SET Activo = 0 WHERE DNI = p_dni;
        SET p_resultado = 'El docente tiene historial de carga horaria: se dio de baja (borrado lógico).';
    ELSE
        DELETE FROM Docente WHERE DNI = p_dni;
        SET p_resultado = 'El docente no tenía historial: se eliminó definitivamente.';
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_docente_reactivar$$
CREATE PROCEDURE sp_docente_reactivar(IN p_dni CHAR(8))
BEGIN
    CALL sp_verificar_permiso('escritura');
    UPDATE Docente SET Activo = 1 WHERE DNI = p_dni;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('El docente no existe o ya estaba vigente.');
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_especialidad_listar$$
CREATE PROCEDURE sp_especialidad_listar(IN p_dni CHAR(8))
BEGIN
    SELECT e.Nombre_Asignatura AS Asignatura,
           a.Nivel_Exigencia AS `Exigencia cognitiva`,
           (SELECT COUNT(*) FROM Docente_Actividad da
              JOIN Actividad x ON x.ID_Actividad = da.ID_Actividad
              JOIN Periodo_Academico p ON p.Anio_Lectivo = x.Periodo
             WHERE da.DNI_Docente = e.DNI_Docente AND x.Materia_Asignatura = e.Nombre_Asignatura
               AND p.Estado = 'Activo') AS `Horas que dicta (periodo activo)`
      FROM Especializado_En e
      JOIN Asignatura a ON a.Nombre_Materia = e.Nombre_Asignatura
     WHERE e.DNI_Docente = p_dni
     ORDER BY e.Nombre_Asignatura;
END$$

DROP PROCEDURE IF EXISTS sp_especialidad_asignar$$
CREATE PROCEDURE sp_especialidad_asignar(IN p_dni CHAR(8), IN p_materia VARCHAR(60))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Especializado_En WHERE DNI_Docente = p_dni AND Nombre_Asignatura = p_materia) THEN
        CALL sp_error('El docente ya tiene registrada esa especialidad.');
    END IF;
    INSERT INTO Especializado_En (Nombre_Asignatura, DNI_Docente) VALUES (p_materia, p_dni);
END$$

DROP PROCEDURE IF EXISTS sp_especialidad_quitar$$
CREATE PROCEDURE sp_especialidad_quitar(IN p_dni CHAR(8), IN p_materia VARCHAR(60))
BEGIN
    CALL sp_verificar_permiso('escritura');
    DELETE FROM Especializado_En WHERE DNI_Docente = p_dni AND Nombre_Asignatura = p_materia;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('El docente no tenía esa especialidad.');
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_docentes_por_materia$$
CREATE PROCEDURE sp_docentes_por_materia(IN p_materia VARCHAR(60))
BEGIN
    SELECT d.DNI, d.Nombre AS `Apellidos y nombres`, d.Modalidad
      FROM Docente d
      JOIN Especializado_En e ON e.DNI_Docente = d.DNI
     WHERE e.Nombre_Asignatura = p_materia AND d.Activo = 1
     ORDER BY d.Nombre;
END$$

-- =========================================================================
-- 7. PLAN DE ESTUDIOS (Grado_Materia)
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_plan_listar$$
CREATE PROCEDURE sp_plan_listar(IN p_id_grado INT)
BEGIN
    SELECT gm.Nombre_Asignatura AS Asignatura,
           a.Nivel_Exigencia AS `Exigencia cognitiva`,
           gm.Horas_Exigidas AS `Horas semanales`,
           ROUND(gm.Horas_Exigidas * 100 / fn_tope_horas_nivel(g.Nivel), 1) AS `% de la semana`
      FROM Grado_Materia gm
      JOIN Asignatura a ON a.Nombre_Materia = gm.Nombre_Asignatura
      JOIN Grado_Escolar g ON g.ID_Grado = gm.ID_Grado
     WHERE gm.ID_Grado = p_id_grado
     ORDER BY gm.Horas_Exigidas DESC, gm.Nombre_Asignatura;
END$$

DROP PROCEDURE IF EXISTS sp_plan_insertar$$
CREATE PROCEDURE sp_plan_insertar(IN p_materia VARCHAR(60), IN p_id_grado INT, IN p_horas SMALLINT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Grado_Materia WHERE Nombre_Asignatura = p_materia AND ID_Grado = p_id_grado) THEN
        CALL sp_error('La asignatura ya figura en el plan de ese grado; modifique sus horas.');
    END IF;
    IF EXISTS (SELECT 1 FROM Asignatura WHERE Nombre_Materia = p_materia AND Activo = 0) THEN
        CALL sp_error('No se puede agregar al plan una asignatura dada de baja.');
    END IF;
    INSERT INTO Grado_Materia (Nombre_Asignatura, ID_Grado, Horas_Exigidas) VALUES (p_materia, p_id_grado, p_horas);
END$$

DROP PROCEDURE IF EXISTS sp_plan_actualizar$$
CREATE PROCEDURE sp_plan_actualizar(IN p_materia VARCHAR(60), IN p_id_grado INT, IN p_horas SMALLINT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Grado_Materia WHERE Nombre_Asignatura = p_materia AND ID_Grado = p_id_grado) THEN
        CALL sp_error('Esa asignatura no figura en el plan del grado.');
    END IF;
    UPDATE Grado_Materia SET Horas_Exigidas = p_horas
     WHERE Nombre_Asignatura = p_materia AND ID_Grado = p_id_grado;
END$$

DROP PROCEDURE IF EXISTS sp_plan_eliminar$$
CREATE PROCEDURE sp_plan_eliminar(IN p_materia VARCHAR(60), IN p_id_grado INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    DELETE FROM Grado_Materia WHERE Nombre_Asignatura = p_materia AND ID_Grado = p_id_grado;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('Esa asignatura no figura en el plan del grado.');
    END IF;
END$$

-- =========================================================================
-- 8. ACTIVIDAD (bloques horarios) Y SUS PARTICIPANTES
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_actividad_listar$$
CREATE PROCEDURE sp_actividad_listar(
    IN p_periodo SMALLINT, IN p_dia VARCHAR(10), IN p_naturaleza VARCHAR(30),
    IN p_id_seccion INT, IN p_dni CHAR(8))
BEGIN
    SELECT a.ID_Actividad AS ID,
           a.Dia AS `Día`,
           TIME_FORMAT(a.Hora_Inicio, '%H:%i') AS Inicio,
           TIME_FORMAT(a.Hora_Fin, '%H:%i') AS Fin,
           a.Naturaleza AS Tipo,
           IFNULL(a.Materia_Asignatura, '-') AS Asignatura,
           a.Espacio,
           IFNULL((SELECT GROUP_CONCAT(CONCAT(g.Nombre, ' ', s.Letra_Identificadora)
                          ORDER BY g.ID_Grado, s.Letra_Identificadora SEPARATOR ', ')
                     FROM Seccion_Periodo sp
                     JOIN Seccion s ON s.ID_Seccion = sp.ID_Seccion
                     JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
                    WHERE sp.ID_Actividad = a.ID_Actividad), '-') AS Secciones,
           IFNULL((SELECT GROUP_CONCAT(d.Nombre ORDER BY d.Nombre SEPARATOR '; ')
                     FROM Docente_Actividad da JOIN Docente d ON d.DNI = da.DNI_Docente
                    WHERE da.ID_Actividad = a.ID_Actividad), '(sin docente)') AS Docentes
      FROM Actividad a
     WHERE a.Periodo = p_periodo
       AND (p_dia IS NULL OR a.Dia = p_dia)
       AND (p_naturaleza IS NULL OR a.Naturaleza = p_naturaleza)
       AND (p_id_seccion IS NULL OR EXISTS (SELECT 1 FROM Seccion_Periodo sp
                                             WHERE sp.ID_Actividad = a.ID_Actividad
                                               AND sp.ID_Seccion = p_id_seccion))
       AND (p_dni IS NULL OR EXISTS (SELECT 1 FROM Docente_Actividad da
                                      WHERE da.ID_Actividad = a.ID_Actividad
                                        AND da.DNI_Docente = p_dni))
     ORDER BY fn_orden_dia(a.Dia), a.Hora_Inicio, a.ID_Actividad;
END$$

DROP PROCEDURE IF EXISTS sp_actividad_insertar$$
CREATE PROCEDURE sp_actividad_insertar(
    IN p_hora_inicio TIME, IN p_hora_fin TIME, IN p_dia VARCHAR(10), IN p_naturaleza VARCHAR(30),
    IN p_materia VARCHAR(60), IN p_periodo SMALLINT, IN p_espacio VARCHAR(60), OUT p_id INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    SELECT IFNULL(MAX(ID_Actividad), 1000) + 1 INTO p_id FROM Actividad;
    INSERT INTO Actividad (ID_Actividad, Hora_Inicio, Hora_Fin, Dia, Naturaleza,
                           Materia_Asignatura, Periodo, Espacio)
    VALUES (p_id, p_hora_inicio, p_hora_fin, p_dia, p_naturaleza,
            NULLIF(TRIM(p_materia), ''), p_periodo, p_espacio);
END$$

DROP PROCEDURE IF EXISTS sp_actividad_actualizar$$
CREATE PROCEDURE sp_actividad_actualizar(
    IN p_id INT, IN p_hora_inicio TIME, IN p_hora_fin TIME, IN p_dia VARCHAR(10),
    IN p_naturaleza VARCHAR(30), IN p_materia VARCHAR(60), IN p_periodo SMALLINT, IN p_espacio VARCHAR(60))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Actividad WHERE ID_Actividad = p_id) THEN
        CALL sp_error(CONCAT('La actividad ', p_id, ' no existe.'));
    END IF;
    UPDATE Actividad
       SET Hora_Inicio = p_hora_inicio, Hora_Fin = p_hora_fin, Dia = p_dia,
           Naturaleza = p_naturaleza, Materia_Asignatura = NULLIF(TRIM(p_materia), ''),
           Periodo = p_periodo, Espacio = p_espacio
     WHERE ID_Actividad = p_id;
END$$

-- Borra el bloque junto con sus participantes, de forma explícita para que
-- cada borrado quede registrado por los triggers de auditoría.
DROP PROCEDURE IF EXISTS sp_actividad_eliminar$$
CREATE PROCEDURE sp_actividad_eliminar(IN p_id INT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    CALL sp_verificar_permiso('escritura');
    IF NOT EXISTS (SELECT 1 FROM Actividad WHERE ID_Actividad = p_id) THEN
        CALL sp_error(CONCAT('La actividad ', p_id, ' no existe.'));
    END IF;
    START TRANSACTION;
    DELETE FROM Docente_Actividad WHERE ID_Actividad = p_id;
    DELETE FROM Seccion_Periodo WHERE ID_Actividad = p_id;
    DELETE FROM Actividad WHERE ID_Actividad = p_id;
    COMMIT;
END$$

DROP PROCEDURE IF EXISTS sp_actividad_secciones$$
CREATE PROCEDURE sp_actividad_secciones(IN p_id INT)
BEGIN
    SELECT s.ID_Seccion AS ID,
           CONCAT(g.Nombre, ' ', s.Letra_Identificadora, ' - ', g.Nivel) AS `Sección`
      FROM Seccion_Periodo sp
      JOIN Seccion s ON s.ID_Seccion = sp.ID_Seccion
      JOIN Grado_Escolar g ON g.ID_Grado = s.ID_Grado
     WHERE sp.ID_Actividad = p_id
     ORDER BY g.ID_Grado, s.Letra_Identificadora;
END$$

DROP PROCEDURE IF EXISTS sp_actividad_docentes$$
CREATE PROCEDURE sp_actividad_docentes(IN p_id INT)
BEGIN
    SELECT d.DNI, d.Nombre AS `Apellidos y nombres`, da.Rol_Asumido AS Rol
      FROM Docente_Actividad da
      JOIN Docente d ON d.DNI = da.DNI_Docente
     WHERE da.ID_Actividad = p_id
     ORDER BY d.Nombre;
END$$

DROP PROCEDURE IF EXISTS sp_actividad_agregar_seccion$$
CREATE PROCEDURE sp_actividad_agregar_seccion(IN p_id INT, IN p_id_seccion INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Seccion_Periodo WHERE ID_Actividad = p_id AND ID_Seccion = p_id_seccion) THEN
        CALL sp_error('La sección ya participa en esta actividad.');
    END IF;
    INSERT INTO Seccion_Periodo (ID_Actividad, ID_Seccion) VALUES (p_id, p_id_seccion);
END$$

DROP PROCEDURE IF EXISTS sp_actividad_quitar_seccion$$
CREATE PROCEDURE sp_actividad_quitar_seccion(IN p_id INT, IN p_id_seccion INT)
BEGIN
    CALL sp_verificar_permiso('escritura');
    DELETE FROM Seccion_Periodo WHERE ID_Actividad = p_id AND ID_Seccion = p_id_seccion;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('La sección no participaba en esta actividad.');
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_actividad_asignar_docente$$
CREATE PROCEDURE sp_actividad_asignar_docente(IN p_id INT, IN p_dni CHAR(8), IN p_rol VARCHAR(40))
BEGIN
    CALL sp_verificar_permiso('escritura');
    IF EXISTS (SELECT 1 FROM Docente_Actividad WHERE ID_Actividad = p_id AND DNI_Docente = p_dni) THEN
        UPDATE Docente_Actividad SET Rol_Asumido = p_rol WHERE ID_Actividad = p_id AND DNI_Docente = p_dni;
    ELSE
        INSERT INTO Docente_Actividad (DNI_Docente, ID_Actividad, Rol_Asumido) VALUES (p_dni, p_id, p_rol);
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_actividad_quitar_docente$$
CREATE PROCEDURE sp_actividad_quitar_docente(IN p_id INT, IN p_dni CHAR(8))
BEGIN
    CALL sp_verificar_permiso('escritura');
    DELETE FROM Docente_Actividad WHERE ID_Actividad = p_id AND DNI_Docente = p_dni;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('El docente no estaba asignado a esta actividad.');
    END IF;
END$$

-- Alta atómica de un bloque de clase completo (bloque + sección + docente).
-- Si cualquier regla falla, no queda nada registrado (ROLLBACK).
DROP PROCEDURE IF EXISTS sp_programar_clase$$
CREATE PROCEDURE sp_programar_clase(
    IN p_periodo SMALLINT, IN p_dia VARCHAR(10), IN p_hora_inicio TIME, IN p_hora_fin TIME,
    IN p_materia VARCHAR(60), IN p_espacio VARCHAR(60), IN p_id_seccion INT, IN p_dni CHAR(8),
    OUT p_id INT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    CALL sp_verificar_permiso('escritura');
    START TRANSACTION;
    SELECT IFNULL(MAX(ID_Actividad), 1000) + 1 INTO p_id FROM Actividad FOR UPDATE;
    INSERT INTO Actividad (ID_Actividad, Hora_Inicio, Hora_Fin, Dia, Naturaleza,
                           Materia_Asignatura, Periodo, Espacio)
    VALUES (p_id, p_hora_inicio, p_hora_fin, p_dia, 'Clase', p_materia, p_periodo, p_espacio);
    INSERT INTO Seccion_Periodo (ID_Actividad, ID_Seccion) VALUES (p_id, p_id_seccion);
    IF p_dni IS NOT NULL AND p_dni <> '' THEN
        INSERT INTO Docente_Actividad (DNI_Docente, ID_Actividad, Rol_Asumido)
        VALUES (p_dni, p_id, 'Dicta clase');
    END IF;
    COMMIT;
END$$

-- =========================================================================
-- 9. USUARIOS DEL APLICATIVO (solo Administrador)
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_usuario_listar$$
CREATE PROCEDURE sp_usuario_listar()
BEGIN
    CALL sp_verificar_permiso('admin');
    SELECT Usuario, Nombre_Completo AS `Nombre completo`, Rol,
           IF(Activo = 1, 'Activa', 'Desactivada') AS Estado,
           IFNULL(DATE_FORMAT(Ultimo_Acceso, '%d/%m/%Y %H:%i'), 'Nunca') AS `Último acceso`
      FROM Usuario_Sistema
     ORDER BY Activo DESC, FIELD(Rol, 'Administrador', 'Coordinador', 'Consulta'), Usuario;
END$$

DROP PROCEDURE IF EXISTS sp_usuario_insertar$$
CREATE PROCEDURE sp_usuario_insertar(
    IN p_usuario VARCHAR(30), IN p_clave VARCHAR(100), IN p_nombre VARCHAR(100), IN p_rol VARCHAR(20))
BEGIN
    CALL sp_verificar_permiso('admin');
    IF CHAR_LENGTH(IFNULL(p_clave, '')) < 6 THEN
        CALL sp_error('La contraseña debe tener al menos 6 caracteres.');
    END IF;
    IF EXISTS (SELECT 1 FROM Usuario_Sistema WHERE Usuario = LOWER(TRIM(p_usuario))) THEN
        CALL sp_error('Ese nombre de usuario ya está en uso.');
    END IF;
    INSERT INTO Usuario_Sistema (Usuario, Clave_Hash, Nombre_Completo, Rol)
    VALUES (p_usuario, SHA2(p_clave, 256), TRIM(p_nombre), p_rol);
END$$

DROP PROCEDURE IF EXISTS sp_usuario_actualizar$$
CREATE PROCEDURE sp_usuario_actualizar(
    IN p_usuario VARCHAR(30), IN p_nombre VARCHAR(100), IN p_rol VARCHAR(20), IN p_activo TINYINT)
BEGIN
    CALL sp_verificar_permiso('admin');
    IF NOT EXISTS (SELECT 1 FROM Usuario_Sistema WHERE Usuario = p_usuario) THEN
        CALL sp_error('El usuario no existe.');
    END IF;
    UPDATE Usuario_Sistema
       SET Nombre_Completo = TRIM(p_nombre), Rol = p_rol, Activo = p_activo
     WHERE Usuario = p_usuario;
END$$

DROP PROCEDURE IF EXISTS sp_usuario_cambiar_clave$$
CREATE PROCEDURE sp_usuario_cambiar_clave(IN p_usuario VARCHAR(30), IN p_clave_nueva VARCHAR(100))
BEGIN
    -- Cada usuario puede cambiar su propia clave; el Administrador, la de cualquiera
    IF @usuario_app IS NOT NULL AND @usuario_app <> p_usuario THEN
        CALL sp_verificar_permiso('admin');
    END IF;
    IF CHAR_LENGTH(IFNULL(p_clave_nueva, '')) < 6 THEN
        CALL sp_error('La contraseña debe tener al menos 6 caracteres.');
    END IF;
    UPDATE Usuario_Sistema SET Clave_Hash = SHA2(p_clave_nueva, 256) WHERE Usuario = p_usuario;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('El usuario no existe.');
    END IF;
END$$

DROP PROCEDURE IF EXISTS sp_usuario_eliminar$$
CREATE PROCEDURE sp_usuario_eliminar(IN p_usuario VARCHAR(30))
BEGIN
    CALL sp_verificar_permiso('admin');
    IF p_usuario = @usuario_app THEN
        CALL sp_error('No puede desactivar la cuenta con la que inició sesión.');
    END IF;
    UPDATE Usuario_Sistema SET Activo = 0 WHERE Usuario = p_usuario AND Activo = 1;
    IF ROW_COUNT() = 0 THEN
        CALL sp_error('El usuario no existe o ya estaba desactivado.');
    END IF;
END$$

-- =========================================================================
-- 10. BITÁCORA Y PANEL DE INICIO
-- =========================================================================

DROP PROCEDURE IF EXISTS sp_bitacora_listar$$
CREATE PROCEDURE sp_bitacora_listar(
    IN p_desde DATE, IN p_hasta DATE, IN p_tabla VARCHAR(40), IN p_usuario VARCHAR(80))
BEGIN
    SELECT b.ID_Bitacora AS `N.°`,
           DATE_FORMAT(b.Fecha_Hora, '%d/%m/%Y %H:%i:%s') AS `Fecha y hora`,
           b.Usuario,
           b.Tabla,
           b.Operacion AS `Operación`,
           b.Clave_Registro AS Registro,
           IFNULL(CAST(b.Datos_Anteriores AS CHAR), '') AS `Antes`,
           IFNULL(CAST(b.Datos_Nuevos AS CHAR), '') AS `Después`
      FROM Bitacora b
     WHERE b.Fecha_Hora >= IFNULL(p_desde, '2000-01-01')
       AND b.Fecha_Hora < DATE_ADD(IFNULL(p_hasta, CURDATE()), INTERVAL 1 DAY)
       AND (p_tabla IS NULL OR p_tabla = '' OR b.Tabla = p_tabla)
       AND (p_usuario IS NULL OR p_usuario = '' OR b.Usuario LIKE CONCAT('%', p_usuario, '%'))
     ORDER BY b.ID_Bitacora DESC
     LIMIT 1000;
END$$

DROP PROCEDURE IF EXISTS sp_resumen_inicio$$
CREATE PROCEDURE sp_resumen_inicio(IN p_periodo SMALLINT)
BEGIN
    SELECT
      (SELECT COUNT(*) FROM Docente WHERE Activo = 1) AS docentes,
      (SELECT COUNT(*) FROM Seccion) AS secciones,
      (SELECT COUNT(*) FROM Espacio WHERE Activo = 1) AS espacios,
      (SELECT COUNT(*) FROM Asignatura WHERE Activo = 1) AS asignaturas,
      (SELECT COUNT(*) FROM Actividad WHERE Periodo = p_periodo) AS bloques,
      (SELECT COUNT(*) FROM Actividad WHERE Periodo = p_periodo AND Naturaleza = 'Clase') AS clases,
      (SELECT COUNT(*) FROM Actividad a WHERE a.Periodo = p_periodo
          AND NOT EXISTS (SELECT 1 FROM Docente_Actividad da WHERE da.ID_Actividad = a.ID_Actividad)) AS sin_docente,
      (SELECT IFNULL(ROUND(100 * SUM(LEAST(t.prog, t.req)) / NULLIF(SUM(t.req), 0), 1), 0)
         FROM (SELECT gm.Horas_Exigidas AS req,
                      (SELECT COUNT(*) FROM Seccion_Periodo sp
                         JOIN Actividad a ON a.ID_Actividad = sp.ID_Actividad
                        WHERE sp.ID_Seccion = s.ID_Seccion AND a.Periodo = p_periodo
                          AND a.Materia_Asignatura = gm.Nombre_Asignatura) AS prog
                 FROM Seccion s
                 JOIN Grado_Materia gm ON gm.ID_Grado = s.ID_Grado
                WHERE EXISTS (SELECT 1 FROM Seccion_Periodo sp2
                                JOIN Actividad a2 ON a2.ID_Actividad = sp2.ID_Actividad
                               WHERE sp2.ID_Seccion = s.ID_Seccion AND a2.Periodo = p_periodo)) t
      ) AS cumplimiento_plan,
      (SELECT COUNT(*) FROM Bitacora WHERE DATE(Fecha_Hora) = CURDATE()) AS cambios_hoy;
END$$

DELIMITER ;
