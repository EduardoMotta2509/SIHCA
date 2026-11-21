-- =========================================================================
-- PROYECTO : Sistema de Asignación de Horarios y Carga Académica (SIHCA)
-- INSTITUCIÓN: Colegio Particular Esclavas del Sagrado Corazón de Jesús
-- CURSO    : GDI - Grupo 09
-- MOTOR    : MySQL 8.0+ (InnoDB - Organización primaria B+-Tree, páginas 16 KB)
-- ARCHIVO  : 01_creacion_bd.sql  -> Estructura física (tablas e índices)
-- -------------------------------------------------------------------------
-- Cambios respecto al Entregable 04 (marcados con [E5] en el código):
--   * Columna Activo en Docente, Asignatura y Espacio para el BORRADO LÓGICO
--     (se conserva el registro histórico de horarios de años anteriores).
--   * Tabla Usuario_Sistema: credenciales y roles del aplicativo cliente.
--   * Tabla Bitacora: auditoría de toda inserción, modificación y borrado,
--     alimentada exclusivamente por triggers.
-- =========================================================================

-- Garantiza que tildes y eñes se interpreten en UTF-8 con cualquier cliente
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS bd_horarios_esclavas;
CREATE DATABASE bd_horarios_esclavas
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_spanish_ci;

USE bd_horarios_esclavas;

-- =========================================================================
-- 1. TABLAS BASE (7 entidades fuertes)
-- =========================================================================

-- 1.1 Grado_Escolar (fichero físico: grado_escolar.ibd)
CREATE TABLE Grado_Escolar (
    ID_Grado INT NOT NULL COMMENT 'Identificador único del grado escolar',
    Nombre VARCHAR(30) NOT NULL COMMENT 'Denominación del grado (ej. 1.° Año)',
    Nivel VARCHAR(20) NOT NULL COMMENT 'Nivel educativo: Inicial, Primaria o Secundaria',
    CONSTRAINT pk_grado_escolar PRIMARY KEY (ID_Grado),
    CONSTRAINT chk_grado_id CHECK (ID_Grado > 0),
    CONSTRAINT chk_grado_nivel CHECK (Nivel IN ('Inicial', 'Primaria', 'Secundaria'))
) ENGINE=InnoDB COMMENT='Grados escolares y nivel educativo al que pertenecen';

-- 1.2 Docente (fichero físico: docente.ibd)
CREATE TABLE Docente (
    DNI CHAR(8) NOT NULL COMMENT 'Documento Nacional de Identidad (8 dígitos fijos)',
    Nombre VARCHAR(100) NOT NULL COMMENT 'Apellidos y nombres completos del docente',
    Modalidad VARCHAR(25) NOT NULL DEFAULT 'Jornada completa' COMMENT 'Jornada completa o Por horas',
    Condicion_Especial VARCHAR(50) NULL DEFAULT 'Docente' COMMENT 'Rol que condiciona prioridad de horario',
    Activo TINYINT(1) NOT NULL DEFAULT 1 COMMENT '[E5] Borrado lógico: 1 = vigente, 0 = dado de baja',
    CONSTRAINT pk_docente PRIMARY KEY (DNI),
    CONSTRAINT chk_docente_dni CHECK (DNI REGEXP '^[0-9]{8}$'),
    CONSTRAINT chk_docente_modalidad CHECK (Modalidad IN ('Jornada completa', 'Por horas')),
    CONSTRAINT chk_docente_condicion CHECK (
        Condicion_Especial IS NULL OR
        Condicion_Especial IN ('Docente', 'Tutor', 'Miembro del Consejo Directivo', 'Hermana de la Congregación')
    ),
    CONSTRAINT chk_docente_activo CHECK (Activo IN (0, 1))
) ENGINE=InnoDB COMMENT='Personal docente y sus condiciones de contratación';

-- 1.3 Asignatura (fichero físico: asignatura.ibd)
CREATE TABLE Asignatura (
    Nombre_Materia VARCHAR(60) NOT NULL COMMENT 'Nombre oficial del área curricular o asignatura',
    Nivel_Exigencia VARCHAR(30) NOT NULL DEFAULT 'Media' COMMENT 'Exigencia cognitiva para ubicación horaria',
    Activo TINYINT(1) NOT NULL DEFAULT 1 COMMENT '[E5] Borrado lógico: 1 = vigente, 0 = dada de baja',
    CONSTRAINT pk_asignatura PRIMARY KEY (Nombre_Materia),
    CONSTRAINT chk_asignatura_exigencia CHECK (Nivel_Exigencia IN ('Alta', 'Media', 'Baja')),
    CONSTRAINT chk_asignatura_activo CHECK (Activo IN (0, 1))
) ENGINE=InnoDB COMMENT='Materias del plan de estudios y su exigencia cognitiva';

-- 1.4 Periodo_Academico (fichero físico: periodo_academico.ibd)
CREATE TABLE Periodo_Academico (
    Anio_Lectivo SMALLINT NOT NULL COMMENT 'Año escolar (4 dígitos)',
    Estado VARCHAR(10) NOT NULL DEFAULT 'Activo' COMMENT 'Activo (editable) o Cerrado (histórico de solo lectura)',
    CONSTRAINT pk_periodo_academico PRIMARY KEY (Anio_Lectivo),
    CONSTRAINT chk_periodo_anio CHECK (Anio_Lectivo BETWEEN 2020 AND 2100),
    CONSTRAINT chk_periodo_estado CHECK (Estado IN ('Activo', 'Cerrado'))
) ENGINE=InnoDB COMMENT='Periodos académicos anuales para el registro histórico';

-- 1.5 Seccion (fichero físico: seccion.ibd)
CREATE TABLE Seccion (
    ID_Seccion INT NOT NULL COMMENT 'Identificador único de la sección',
    Letra_Identificadora VARCHAR(2) NOT NULL COMMENT 'Letra paralela dentro del grado (A o B)',
    ID_Grado INT NOT NULL COMMENT 'Grado escolar al que pertenece la sección',
    CONSTRAINT pk_seccion PRIMARY KEY (ID_Seccion),
    CONSTRAINT chk_seccion_id CHECK (ID_Seccion > 0),
    CONSTRAINT chk_seccion_letra CHECK (Letra_Identificadora IN ('A', 'B')),
    CONSTRAINT fk_seccion_grado FOREIGN KEY (ID_Grado)
        REFERENCES Grado_Escolar (ID_Grado)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Secciones de estudiantes (máx. 35 alumnos, hasta 2 por grado)';

-- 1.6 Espacio (fichero físico: espacio.ibd)
CREATE TABLE Espacio (
    Denominacion VARCHAR(60) NOT NULL COMMENT 'Nombre del ambiente físico (ej. Cancha sintética)',
    Categoria VARCHAR(30) NOT NULL COMMENT 'Clasificación de uso del ambiente',
    Limite_Grupos SMALLINT NOT NULL DEFAULT 1 COMMENT 'Máximo de grupos simultáneos permitidos',
    Activo TINYINT(1) NOT NULL DEFAULT 1 COMMENT '[E5] Borrado lógico: 1 = disponible, 0 = dado de baja',
    CONSTRAINT pk_espacio PRIMARY KEY (Denominacion),
    CONSTRAINT chk_espacio_categoria CHECK (
        Categoria IN ('Aula', 'Cancha', 'Ambiente de música', 'Centro de cómputo', 'Laboratorio', 'Patio')
    ),
    CONSTRAINT chk_espacio_limite CHECK (Limite_Grupos > 0),
    CONSTRAINT chk_espacio_activo CHECK (Activo IN (0, 1))
) ENGINE=InnoDB COMMENT='Infraestructura física y control de aforos/cruces simultáneos';

-- 1.7 Actividad (fichero físico: actividad.ibd - núcleo transaccional)
CREATE TABLE Actividad (
    ID_Actividad INT NOT NULL COMMENT 'Identificador único del bloque horario programado',
    Hora_Inicio TIME NOT NULL COMMENT 'Hora de inicio en formato 24h (HH:MM:SS)',
    Hora_Fin TIME NOT NULL COMMENT 'Hora de término en formato 24h (HH:MM:SS)',
    Dia VARCHAR(10) NOT NULL COMMENT 'Día de la semana de lunes a viernes',
    Naturaleza VARCHAR(30) NOT NULL COMMENT 'Tipo de bloque: Clase, Receso, Consejo, Pastoral',
    Materia_Asignatura VARCHAR(60) NULL COMMENT 'Materia dictada (NULL si es Receso, Consejo o Pastoral)',
    Periodo SMALLINT NOT NULL COMMENT 'Año lectivo al que pertenece la actividad',
    Espacio VARCHAR(60) NOT NULL COMMENT 'Ambiente físico asignado',
    CONSTRAINT pk_actividad PRIMARY KEY (ID_Actividad),
    CONSTRAINT chk_actividad_id CHECK (ID_Actividad > 0),
    CONSTRAINT chk_actividad_horas CHECK (Hora_Fin > Hora_Inicio),
    CONSTRAINT chk_actividad_dia CHECK (Dia IN ('Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes')),
    CONSTRAINT chk_actividad_naturaleza CHECK (Naturaleza IN ('Clase', 'Receso', 'Consejo', 'Pastoral')),
    CONSTRAINT fk_actividad_asignatura FOREIGN KEY (Materia_Asignatura)
        REFERENCES Asignatura (Nombre_Materia)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_actividad_periodo FOREIGN KEY (Periodo)
        REFERENCES Periodo_Academico (Anio_Lectivo)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_actividad_espacio FOREIGN KEY (Espacio)
        REFERENCES Espacio (Denominacion)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Bloques horarios programados en la matriz del colegio';

-- =========================================================================
-- 2. TABLAS INTERMEDIAS M:N (claves compuestas)
-- =========================================================================

-- 2.1 Grado_Materia (fichero físico: grado_materia.ibd)
CREATE TABLE Grado_Materia (
    Nombre_Asignatura VARCHAR(60) NOT NULL COMMENT 'Asignatura exigida en el plan de estudios',
    ID_Grado INT NOT NULL COMMENT 'Grado escolar al que se aplica',
    Horas_Exigidas SMALLINT NOT NULL COMMENT 'Horas pedagógicas semanales del plan de estudios',
    CONSTRAINT pk_grado_materia PRIMARY KEY (Nombre_Asignatura, ID_Grado),
    CONSTRAINT chk_gm_horas CHECK (Horas_Exigidas > 0 AND Horas_Exigidas <= 45),
    CONSTRAINT fk_gm_asignatura FOREIGN KEY (Nombre_Asignatura)
        REFERENCES Asignatura (Nombre_Materia)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_gm_grado FOREIGN KEY (ID_Grado)
        REFERENCES Grado_Escolar (ID_Grado)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Horas pedagógicas semanales por asignatura y grado (Plan de Estudios)';

-- 2.2 Especializado_En (fichero físico: especializado_en.ibd)
CREATE TABLE Especializado_En (
    Nombre_Asignatura VARCHAR(60) NOT NULL COMMENT 'Asignatura de especialidad',
    DNI_Docente CHAR(8) NOT NULL COMMENT 'DNI del docente especialista',
    CONSTRAINT pk_especializado_en PRIMARY KEY (Nombre_Asignatura, DNI_Docente),
    CONSTRAINT fk_esp_asignatura FOREIGN KEY (Nombre_Asignatura)
        REFERENCES Asignatura (Nombre_Materia)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_esp_docente FOREIGN KEY (DNI_Docente)
        REFERENCES Docente (DNI)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Catálogo de especialidades docentes por asignatura';

-- 2.3 Seccion_Periodo (fichero físico: seccion_periodo.ibd)
CREATE TABLE Seccion_Periodo (
    ID_Actividad INT NOT NULL COMMENT 'Actividad programada',
    ID_Seccion INT NOT NULL COMMENT 'Sección participante en la actividad',
    CONSTRAINT pk_seccion_periodo PRIMARY KEY (ID_Actividad, ID_Seccion),
    CONSTRAINT fk_sp_actividad FOREIGN KEY (ID_Actividad)
        REFERENCES Actividad (ID_Actividad)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_sp_seccion FOREIGN KEY (ID_Seccion)
        REFERENCES Seccion (ID_Seccion)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Participación de secciones en actividades (Relación INVOLUCRA)';

-- 2.4 Docente_Actividad (fichero físico: docente_actividad.ibd)
CREATE TABLE Docente_Actividad (
    DNI_Docente CHAR(8) NOT NULL COMMENT 'Docente asignado al bloque horario',
    ID_Actividad INT NOT NULL COMMENT 'Actividad que ejecuta el docente',
    Rol_Asumido VARCHAR(40) NOT NULL DEFAULT 'Dicta clase' COMMENT 'Función en el bloque (clase, receso, reunión)',
    CONSTRAINT pk_docente_actividad PRIMARY KEY (DNI_Docente, ID_Actividad),
    CONSTRAINT chk_da_rol CHECK (
        Rol_Asumido IN ('Dicta clase', 'Cuida receso', 'Reunión', 'Refrigera con estudiantes')
    ),
    CONSTRAINT fk_da_docente FOREIGN KEY (DNI_Docente)
        REFERENCES Docente (DNI)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_da_actividad FOREIGN KEY (ID_Actividad)
        REFERENCES Actividad (ID_Actividad)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Carga horaria lectiva y no lectiva ejecutada por docente';

-- =========================================================================
-- 3. TABLAS DE SOPORTE DEL APLICATIVO  [E5]
-- =========================================================================

-- 3.1 Usuario_Sistema (fichero físico: usuario_sistema.ibd)
--     La contraseña nunca se guarda en texto plano: se almacena su hash SHA-256.
CREATE TABLE Usuario_Sistema (
    Usuario VARCHAR(30) NOT NULL COMMENT 'Nombre de inicio de sesión',
    Clave_Hash CHAR(64) NOT NULL COMMENT 'SHA2(contraseña, 256) en hexadecimal',
    Nombre_Completo VARCHAR(100) NOT NULL COMMENT 'Persona responsable de la cuenta',
    Rol VARCHAR(20) NOT NULL DEFAULT 'Consulta' COMMENT 'Administrador, Coordinador o Consulta',
    Activo TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Borrado lógico de la cuenta',
    Ultimo_Acceso DATETIME NULL COMMENT 'Fecha y hora del último inicio de sesión exitoso',
    CONSTRAINT pk_usuario_sistema PRIMARY KEY (Usuario),
    CONSTRAINT chk_usuario_rol CHECK (Rol IN ('Administrador', 'Coordinador', 'Consulta')),
    CONSTRAINT chk_usuario_activo CHECK (Activo IN (0, 1)),
    CONSTRAINT chk_usuario_hash CHECK (CHAR_LENGTH(Clave_Hash) = 64)
) ENGINE=InnoDB COMMENT='[E5] Cuentas de acceso al aplicativo cliente';

-- 3.2 Bitacora (fichero físico: bitacora.ibd)
--     Solo los triggers escriben aquí; el aplicativo únicamente la consulta.
CREATE TABLE Bitacora (
    ID_Bitacora BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Correlativo del evento',
    Fecha_Hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Momento del cambio',
    Usuario VARCHAR(80) NOT NULL COMMENT 'Usuario del aplicativo (o de MySQL si se operó fuera de él)',
    Tabla VARCHAR(40) NOT NULL COMMENT 'Tabla afectada',
    Operacion VARCHAR(10) NOT NULL COMMENT 'INSERT, UPDATE o DELETE',
    Clave_Registro VARCHAR(150) NOT NULL COMMENT 'Clave primaria del registro afectado',
    Datos_Anteriores JSON NULL COMMENT 'Valores antes del cambio (UPDATE/DELETE)',
    Datos_Nuevos JSON NULL COMMENT 'Valores después del cambio (INSERT/UPDATE)',
    CONSTRAINT pk_bitacora PRIMARY KEY (ID_Bitacora),
    CONSTRAINT chk_bitacora_operacion CHECK (Operacion IN ('INSERT', 'UPDATE', 'DELETE'))
) ENGINE=InnoDB COMMENT='[E5] Auditoría de cambios generada por triggers';

-- =========================================================================
-- 4. ÍNDICES SECUNDARIOS (Árboles B+ dinámicos)
-- =========================================================================

-- 4.1 Único multicolumna: evita dos secciones con la misma letra en un grado
CREATE UNIQUE INDEX uq_seccion_grado_letra
    ON Seccion (ID_Grado, Letra_Identificadora) USING BTREE;

-- 4.2 Multiclave: validación de cruces en un espacio físico por día y año
CREATE INDEX idx_actividad_cruce_espacio
    ON Actividad (Periodo, Dia, Espacio, Hora_Inicio, Hora_Fin) USING BTREE;

-- 4.3 Filtro por naturaleza y día (matriz de horarios y recesos)
CREATE INDEX idx_actividad_naturaleza_dia
    ON Actividad (Periodo, Naturaleza, Dia) USING BTREE;

-- 4.4 Búsqueda de docentes por nombre y por condición especial
CREATE INDEX idx_docente_nombre
    ON Docente (Nombre) USING BTREE;

CREATE INDEX idx_docente_condicion
    ON Docente (Condicion_Especial) USING BTREE;

-- 4.5 Carga de un docente según su rol (lectiva vs. receso)
CREATE INDEX idx_docente_actividad_rol
    ON Docente_Actividad (DNI_Docente, Rol_Asumido) USING BTREE;

-- 4.6 [E5] Consultas de la bitácora por rango de fechas y por tabla
CREATE INDEX idx_bitacora_fecha
    ON Bitacora (Fecha_Hora) USING BTREE;

CREATE INDEX idx_bitacora_tabla
    ON Bitacora (Tabla, Fecha_Hora) USING BTREE;

-- 4.7 [E5] Recorrido inverso Sección -> Actividades (detección de cruces de sección)
CREATE INDEX idx_seccion_periodo_seccion
    ON Seccion_Periodo (ID_Seccion, ID_Actividad) USING BTREE;
