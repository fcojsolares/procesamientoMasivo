-- =====================================================================================
-- Script DDL - Microservicio de Procesamiento Masivo de Transacciones
-- Motor: PostgreSQL 14+
-- Ejecutar manualmente por consola (psql) contra la base de datos "procesamiento_masivo".
-- =====================================================================================

-- Tipos enumerados nativos de PostgreSQL: garantizan integridad de dominio
-- con menor costo de almacenamiento que un varchar libre.
CREATE TYPE tipo_operacion_enum AS ENUM ('DEPOSITO', 'RETIRO', 'TRANSFERENCIA', 'PAGO');
CREATE TYPE estado_lote_enum AS ENUM ('EN_PROCESO', 'COMPLETADO', 'COMPLETADO_CON_ERRORES', 'FALLIDO');

-- =====================================================================================
-- Tabla de control y auditoría de lotes (archivos) procesados
-- =====================================================================================
CREATE TABLE lotes_procesamiento (
    id                  BIGSERIAL PRIMARY KEY,
    nombre_archivo      VARCHAR(255)        NOT NULL,
    total_registros     INTEGER             NOT NULL DEFAULT 0,
    registros_exitosos  INTEGER             NOT NULL DEFAULT 0,
    registros_fallidos  INTEGER             NOT NULL DEFAULT 0,
    fecha_inicio        TIMESTAMP           NOT NULL DEFAULT now(),
    fecha_fin           TIMESTAMP,
    estado              estado_lote_enum    NOT NULL DEFAULT 'EN_PROCESO',

    CONSTRAINT ck_lotes_registros_no_negativos
        CHECK (total_registros >= 0 AND registros_exitosos >= 0 AND registros_fallidos >= 0)
);

COMMENT ON TABLE lotes_procesamiento IS 'Auditoría y control de cada archivo de transacciones procesado.';

-- Consultas típicas: histórico ordenado por fecha, y filtrado por estado (ej. reintentos de FALLIDO).
CREATE INDEX idx_lotes_fecha_inicio ON lotes_procesamiento (fecha_inicio DESC);
CREATE INDEX idx_lotes_estado ON lotes_procesamiento (estado);

-- =====================================================================================
-- Tabla principal de transacciones (alto volumen de inserción y consulta)
-- =====================================================================================
CREATE TABLE transacciones (
    id                  BIGSERIAL PRIMARY KEY,
    id_transaccion      VARCHAR(50)         NOT NULL,
    cuenta_origen       VARCHAR(30)         NOT NULL,
    cuenta_destino      VARCHAR(30)         NOT NULL,
    monto               NUMERIC(18, 2)      NOT NULL,
    fecha_hora          TIMESTAMP           NOT NULL,
    tipo_operacion      tipo_operacion_enum NOT NULL,
    lote_id             BIGINT              NOT NULL,
    fecha_creacion      TIMESTAMP           NOT NULL DEFAULT now(),

    CONSTRAINT fk_transacciones_lote
        FOREIGN KEY (lote_id) REFERENCES lotes_procesamiento (id),
    CONSTRAINT uq_transacciones_id_transaccion
        UNIQUE (id_transaccion),
    CONSTRAINT ck_transacciones_monto_positivo
        CHECK (monto > 0)
);

COMMENT ON TABLE transacciones IS 'Movimientos financieros válidos, ya normalizados y persistidos.';

-- La unicidad de id_transaccion ya crea un índice único (uq_transacciones_id_transaccion),
-- que además sirve para validar duplicados en tiempo de inserción.
-- Índices estratégicos para las consultas más frecuentes sobre alto volumen:
CREATE INDEX idx_transacciones_lote_id ON transacciones (lote_id);
CREATE INDEX idx_transacciones_cuenta_origen ON transacciones (cuenta_origen);
CREATE INDEX idx_transacciones_cuenta_destino ON transacciones (cuenta_destino);
CREATE INDEX idx_transacciones_fecha_hora ON transacciones (fecha_hora);
CREATE INDEX idx_transacciones_tipo_operacion ON transacciones (tipo_operacion);

-- =====================================================================================
-- Tabla de detalle de errores por línea de archivo
-- =====================================================================================
CREATE TABLE detalle_errores (
    id                  BIGSERIAL PRIMARY KEY,
    lote_id             BIGINT              NOT NULL,
    numero_linea        INTEGER             NOT NULL,
    contenido_linea     TEXT,
    motivo_error        VARCHAR(500)        NOT NULL,
    fecha_registro      TIMESTAMP           NOT NULL DEFAULT now(),

    CONSTRAINT fk_detalle_errores_lote
        FOREIGN KEY (lote_id) REFERENCES lotes_procesamiento (id)
);

COMMENT ON TABLE detalle_errores IS 'Registro línea a línea de los motivos por los que un registro fue rechazado.';

CREATE INDEX idx_detalle_errores_lote_id ON detalle_errores (lote_id);

-- =====================================================================================
-- Fin del script de negocio
-- =====================================================================================

-- =====================================================================================
-- Tablas de metadatos de Spring Batch (requeridas por el framework)
-- Fuente: schema-postgresql.sql oficial de Spring Batch 5.x
-- =====================================================================================

CREATE TABLE BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT       NOT NULL PRIMARY KEY,
    VERSION         BIGINT,
    JOB_NAME        VARCHAR(100) NOT NULL,
    JOB_KEY         VARCHAR(32)  NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

CREATE TABLE BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT        NOT NULL PRIMARY KEY,
    VERSION          BIGINT,
    JOB_INSTANCE_ID  BIGINT        NOT NULL,
    CREATE_TIME      TIMESTAMP     NOT NULL,
    START_TIME       TIMESTAMP     DEFAULT NULL,
    END_TIME         TIMESTAMP     DEFAULT NULL,
    STATUS           VARCHAR(10),
    EXIT_CODE        VARCHAR(2500),
    EXIT_MESSAGE     VARCHAR(2500),
    LAST_UPDATED     TIMESTAMP,
    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
        REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT       NOT NULL,
    PARAMETER_NAME   VARCHAR(100) NOT NULL,
    PARAMETER_TYPE   VARCHAR(100) NOT NULL,
    PARAMETER_VALUE  VARCHAR(2500),
    IDENTIFYING      CHAR(1)      NOT NULL,
    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID  BIGINT       NOT NULL PRIMARY KEY,
    VERSION            BIGINT       NOT NULL,
    STEP_NAME          VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID   BIGINT       NOT NULL,
    CREATE_TIME        TIMESTAMP    NOT NULL,
    START_TIME         TIMESTAMP    DEFAULT NULL,
    END_TIME           TIMESTAMP    DEFAULT NULL,
    STATUS             VARCHAR(10),
    COMMIT_COUNT       BIGINT,
    READ_COUNT         BIGINT,
    FILTER_COUNT       BIGINT,
    WRITE_COUNT        BIGINT,
    READ_SKIP_COUNT    BIGINT,
    WRITE_SKIP_COUNT   BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT     BIGINT,
    EXIT_CODE          VARCHAR(2500),
    EXIT_MESSAGE       VARCHAR(2500),
    LAST_UPDATED       TIMESTAMP,
    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID  BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
        REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID   BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ  MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ            MAXVALUE 9223372036854775807 NO CYCLE;

-- =====================================================================================
-- Fin del script
-- =====================================================================================
