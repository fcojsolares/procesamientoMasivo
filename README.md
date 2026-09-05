# Microservicio de Procesamiento Masivo de Transacciones Financieras

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot 3.5](https://img.shields.io/badge/Spring_Boot-3.5.7-green.svg)
![Spring Batch 5](https://img.shields.io/badge/Spring_Batch-5.x-blue.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14%2B-blue.svg)
![Gradle](https://img.shields.io/badge/Gradle-Groovy-brightgreen.svg)

Microservicio desarrollado en **Java 21** y **Spring Boot 3.5** para la ingesta, validación, procesamiento asíncrono y persistencia resiliente de archivos CSV masivos de transacciones financieras.

La arquitectura combina **Spring Batch 5** para la ejecución en lotes por bloques (*chunks*), **Apache Commons CSV** para el parseo eficiente y **Spring Data JPA / Hibernate** sobre **PostgreSQL**, garantizando alta disponibilidad, auditoría completa y **tolerancia a fallos por línea sin abortar el proceso**.

---

## 1. Estrategia para Evitar que el Proceso Falle ante Errores de Datos

Para manejar volúmenes masivos de datos donde existen filas defectuosas o inconsistentes, la aplicación implementa una estrategia de **resiliencia en múltiples capas**:

```
+-----------------------------------------------------------------------------------+
| 1. Validación Fail-Fast  --->  2. Ingesta Asíncrona  --->  3. Aislamiento por Fila |
| (Extensión, Encabezados)      (Respuesta Inmediata)       (Try-Catch + Audit BD)  |
+-----------------------------------------------------------------------------------+
                                                                     |
                                                                     v
+-----------------------------------------------------------------------------------+
| 6. Limpieza & Cierre   <---  5. Estado Final Consistente <--- 4. Descarte con Null  |
| (Delete File Temporal)       (Completado c/ Errores)     (Spring Batch Skip)      |
+-----------------------------------------------------------------------------------+
```

### Principios y Mecanismos de Tolerancia a Fallos:

1. **Validación Estructural Previa (*Fail-Fast*)**
   - El servicio `ValidadorArchivoService` valida el archivo antes de iniciar cualquier procesamiento.
   - Si el archivo está vacío, no tiene extensión `.csv` o no contiene la lista completa de cabeceras requeridas (`id_transaccion,cuenta_origen,cuenta_destino,monto,fecha_hora,tipo_operacion`), la petición es rechazada de inmediato (`400 Bad Request`) previniendo consumo inútil de recursos de procesamiento o almacenamiento.

2. **Carga y Procesamiento Asíncrono**
   - Al recibir un archivo válido, `ProcesadorArchivoService` registra un lote inicial en la tabla `lotes_procesamiento` con estado `EN_PROCESO` y genera un archivo temporal en disco.
   - La ejecución del Job de Spring Batch se delega a un pool de hilos dedicado (`TaskExecutorJobLauncher`), retornando inmediatamente un estado `202 Accepted` al cliente HTTP.

3. **Aislamiento y Validación Fila por Fila (Row-Level Fault Tolerance)**
   - En `TransaccionItemProcessor`, cada fila del CSV (`RawCsvRow`) se valida individualmente mediante `ParserTransaccionService`.
   - Se validan reglas como: campos obligatorios no nulos, formato ISO-8601 de fecha (`yyyy-MM-ddTHH:mm:ss`), tipos de operación válidos (`DEPOSITO`, `RETIRO`, `TRANSFERENCIA`, `PAGO`), `monto` numérico estrictamente positivo (`> 0`), longitud de cuentas ($\le 30$ caracteres), divergencia entre cuenta origen y destino, e **unicidad del `id_transaccion` dentro del lote** (utilizando un conjunto en memoria `idsVistosEnElLote`).

4. **Descarte Silencioso mediante Retorno `null` en Spring Batch**
   - Cuando una fila rompe alguna regla, se captura la excepción `LineaInvalidaException` (o cualquier fallo no planificado).
   - En lugar de propagar el error y forzar un *rollback* de la transacción de base de datos o cancelar el lote completo, el procesador:
     1. Registra el detalle del error en la tabla `detalle_errores` (número de línea, contenido crudo y motivo exacto).
     2. Incrementa el contador de fallidos en el `ExecutionContext` del Step.
     3. **Retorna `null`**. En la especificación de Spring Batch, un retorno de `null` en un `ItemProcessor` indica que el ítem debe ser descartado del pipeline de datos, por lo que **no llega al `ItemWriter`** y permite que las líneas válidas del bloque continuen su persistencia exitosa.

5. **Persistencia por Bloques (*Chunks*) e Aislamiento Transaccional**
   - El Step de Batch procesa la información en bloques configurables (propiedad `procesamiento.archivo.tamano-bloque`, por defecto `500`).
   - Las operaciones de persistencia del estado del lote y registro de errores utilizan transacciones independientes (`@Transactional(propagation = Propagation.REQUIRES_NEW)`), asegurando que los registros de auditoría y errores queden almacenados sin importar el resultado del chunk.

6. **Determinación Consistente del Estado Final del Lote**
   - Al finalizar el Job, `LoteJobListener` recupera los contadores del contexto del Step y asigna el estado definitivo:
     - **`COMPLETADO`**: 100% de registros procesados con éxito.
     - **`COMPLETADO_CON_ERRORES`**: Existen transacciones guardadas en BD y también filas registradas en `detalle_errores`.
     - **`FALLIDO`**: Ningún registro pudo ser procesado o existió una falla catastrófica de infraestructura.

7. **Limpieza Garantizada de Archivos Temporales**
   - En el bloque `finally` de `LoteJobListener`, el sistema borra el archivo temporal del sistema de archivos, garantizando que no existan fugas de almacenamiento en disco.

---

## 2. Stack Técnico

| Componente | Tecnología | Versión / Notas |
|------------|-----------|-----------------|
| **Lenguaje** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.5.7 |
| **Batch Processing** | Spring Batch | 5.x |
| **Persistencia** | Spring Data JPA / Hibernate | Batch inserts ordenados (`batch_size: 500`) |
| **Base de Datos** | PostgreSQL | 14+ |
| **Parseo CSV** | Apache Commons CSV | 1.11.0 |
| **Construcción** | Gradle | DSL Groovy |
| **Documentación API** | OpenAPI 3 / Swagger UI | `springdoc-openapi-starter-webmvc-ui:2.8.17` |

---

## 3. Layout del Archivo CSV de Entrada

El archivo de transacciones debe ser enviado en formato CSV con delimitador de coma (`,`), codificación **UTF-8** y fila de encabezados obligatoria.

### Especificación de Columnas:

| Columna | Tipo de Dato | Requerido | Reglas de Validación |
|---------|--------------|-----------|----------------------|
| `id_transaccion` | Texto | Sí | Identificador único en el origen. No debe duplicarse en el lote. |
| `cuenta_origen` | Texto | Sí | Máximo 30 caracteres. |
| `cuenta_destino` | Texto | Sí | Máximo 30 caracteres. Debe ser distinta a `cuenta_origen`. |
| `monto` | Decimal | Sí | Numérico positivo mayor a cero (`monto > 0`). Máximo 2 decimales. |
| `fecha_hora` | Timestamp | Sí | Formato ISO-8601 local: `yyyy-MM-ddTHH:mm:ss`. |
| `tipo_operacion` | Enum | Sí | Valores permitidos: `DEPOSITO`, `RETIRO`, `TRANSFERENCIA`, `PAGO`. |

Ejemplo de contenido válido:

```csv
id_transaccion,cuenta_origen,cuenta_destino,monto,fecha_hora,tipo_operacion
TXN-0001,1011223344,2022334455,1500.50,2026-01-15T10:30:00,TRANSFERENCIA
TXN-0002,3033445566,4044556677,250.00,2026-01-15T10:31:12,PAGO
```

---

## 4. Modelo de Datos y Auditoría

La base de datos relacional PostgreSQL se compone de tres tablas principales:

```mermaid
erDiagram
    LOTES_PROCESAMIENTO ||--o{ TRANSACCIONES : "contiene válidas"
    LOTES_PROCESAMIENTO ||--o{ DETALLE_ERRORES : "registra fallidas"

    LOTES_PROCESAMIENTO {
        bigint id PK
        string nombre_archivo
        string estado
        int total_registros
        int registros_exitosos
        int registros_fallidos
        timestamp fecha_inicio
        timestamp fecha_fin
    }

    TRANSACCIONES {
        bigint id PK
        string id_transaccion BK
        string cuenta_origen
        string cuenta_destino
        decimal monto
        timestamp fecha_hora
        string tipo_operacion
        bigint lote_id FK
    }

    DETALLE_ERRORES {
        bigint id PK
        bigint lote_id FK
        int numero_linea
        string contenido_linea
        string motivo_error
        timestamp fecha_registro
    }
```

---

## 5. Endpoints de la API REST

### 1. Iniciar Procesamiento de Archivo
- **Ruta**: `POST /api/v1/lotes/procesar`
- **Consumes**: `multipart/form-data` (parámetro `archivo`)
- **Respuesta (`202 Accepted`)**:

```json
{
  "loteId": 1,
  "nombreArchivo": "transacciones_ejemplo.csv",
  "estado": "EN_PROCESO",
  "totalRegistros": 0,
  "registrosExitosos": 0,
  "registrosFallidos": 0,
  "fechaInicio": "2026-09-05T10:00:00",
  "fechaFin": null
}
```

### 2. Consultar Estado de un Lote
- **Ruta**: `GET /api/v1/lotes/{loteId}`
- **Respuesta (`200 OK`)**:

```json
{
  "loteId": 1,
  "nombreArchivo": "transacciones_ejemplo.csv",
  "estado": "COMPLETADO_CON_ERRORES",
  "totalRegistros": 25,
  "registrosExitosos": 17,
  "registrosFallidos": 8,
  "fechaInicio": "2026-09-05T10:00:00",
  "fechaFin": "2026-09-05T10:00:02"
}
```

### 3. Consultar Detalle de Errores de un Lote
- **Ruta**: `GET /api/v1/lotes/{loteId}/errores`
- **Respuesta (`200 OK`)**:

```json
[
  {
    "id": 1,
    "numeroLinea": 4,
    "contenidoLinea": "TXN-0004,1011223344,1011223344,-50.00,2026-01-15T10:35:00,RETIRO",
    "motivoError": "La cuenta origen y la cuenta destino no pueden ser iguales.",
    "fechaRegistro": "2026-09-05T10:00:01"
  },
  {
    "id": 2,
    "numeroLinea": 7,
    "contenidoLinea": "TXN-0007,5055667788,6066778899,abc,2026-01-15T10:40:00,DEPOSITO",
    "motivoError": "El campo 'monto' no es un valor numérico válido: 'abc'.",
    "fechaRegistro": "2026-09-05T10:00:01"
  }
]
```

---

## 6. Guía de Ejecución Local

### Prerrequisitos
- **Java 21** instalado y configurado en el `PATH`.
- **PostgreSQL 14+** corriendo localmente en el puerto `5432` con la base de datos `transacciones_bancarias` creada.

### Pasos para Ejecutar:

1. Configurar credenciales en `src/main/resources/application.yaml` si es necesario:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/transacciones_bancarias
    username: postgres
    password: tu_password
```

2. Ejecutar la aplicación mediante Gradle Wrapper:

```bash
./gradlew bootRun
```

3. Acceder a la documentación Swagger UI:
   - URL: `http://localhost:8080/swagger-ui.html`

---

## 7. Suite de Archivos de Prueba Incorporados

En el directorio `src/main/resources/archivos-ejemplo/` se incluyen archivos CSV listos para verificar todos los escenarios de validación y resiliencia:

| Archivo | Escenario Evaluado | Resultado Esperado |
|---------|-------------------|--------------------|
| `transacciones_ok.csv` | Registros 100% válidos. | Estado `COMPLETADO`. 0 errores. |
| `transacciones_datos_corruptos.csv` | Mezcla de filas válidas, montos negativos, formatos de fecha malos y duplicados. | Estado `COMPLETADO_CON_ERRORES`. Registros fallidos persistidos en `detalle_errores`. |
| `transacciones_vacio.csv` | Archivo de 0 bytes. | Rechazo inmediato `400 Bad Request` (`ArchivoInvalidoException`). |
| `transacciones_encabezado_incompleto.csv` | Falta columna requerida. | Rechazo inmediato `400 Bad Request`. |
| `transacciones_formato_invalido.txt` | Extensión diferente a `.csv`. | Rechazo inmediato `400 Bad Request`. |

Ejemplo de ejecución de prueba con `curl`:

```bash
curl -X POST http://localhost:8080/api/v1/lotes/procesar \
  -F "archivo=@src/main/resources/archivos-ejemplo/transacciones_datos_corruptos.csv"
```

---

## 8. Estructura del Proyecto

```text
procesamientomasivo/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/transsacciones/procesamientomasivo/
        │       ├── batch/
        │       │   ├── BatchConfig.java               # Configuración del Job, Steps y TaskExecutor
        │       │   ├── CsvItemReader.java             # Reader personalizado de Apache Commons CSV (@StepScope)
        │       │   ├── LoteJobListener.java           # Listener para métricas finales y limpieza de disco
        │       │   ├── RawCsvRow.java                 # Record contenedor de la fila cruda y su número de línea
        │       │   ├── TransaccionItemProcessor.java  # Validación, control de errores y retorno null
        │       │   └── TransaccionItemWriter.java     # Writer JPA por bloques
        │       ├── config/                            # Swagger / OpenAPI custom configurations
        │       ├── controller/                        # Endpoints REST (/api/v1/lotes)
        │       ├── dto/                               # DTOs de respuesta y consulta
        │       ├── entity/                            # Entidades JPA (LoteProceso, Transaccion, DetalleError)
        │       ├── exception/                         # Manejo global de excepciones (@ControllerAdvice)
        │       ├── repository/                        # Repositorios de Spring Data JPA
        │       ├── service/                           # Servicios de negocio y validación de archivos
        │       └── ProcesamientomasivoApplication.java
        └── resources/
            ├── application.yaml
            └── archivos-ejemplo/                      # Suite de archivos CSV de prueba
```
