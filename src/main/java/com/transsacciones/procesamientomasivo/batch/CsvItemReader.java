package com.transsacciones.procesamientomasivo.batch;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;


@Slf4j
public class CsvItemReader implements ItemReader<RawCsvRow>, ItemStream {

    private static final int NUMERO_LINEA_INICIAL = 2; // línea 1 = encabezado

    private final Path archivoPath;
    private CSVParser parser;
    private Iterator<CSVRecord> iterador;
    private int numeroLineaActual;
    private int totalLeidas;

    public CsvItemReader(Path archivoPath) {
        this.archivoPath = archivoPath;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        log.info("[READER] ► Abriendo archivo CSV para lectura: {}", archivoPath);
        try {
            var lector = new InputStreamReader(Files.newInputStream(archivoPath), StandardCharsets.UTF_8);
            parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build()
                    .parse(lector);
            iterador = parser.iterator();
            numeroLineaActual = NUMERO_LINEA_INICIAL;
            totalLeidas = 0;
            log.info("[READER] ✔ Archivo abierto correctamente. Encabezados detectados: {}",
                    parser.getHeaderNames());
        } catch (IOException e) {
            log.error("[READER] ✖ No se pudo abrir el archivo CSV: {}", archivoPath, e);
            throw new ItemStreamException("No se pudo abrir el archivo CSV: " + archivoPath, e);
        }
    }

    @Override
    public RawCsvRow read() {
        if (iterador == null || !iterador.hasNext()) {
            log.info("[READER] ■ Fin del archivo alcanzado. Total de filas leídas: {}", totalLeidas);
            return null; // señal de fin de datos para Spring Batch
        }
        CSVRecord registro = iterador.next();
        totalLeidas++;

        if (totalLeidas % 500 == 0) {
            log.info("[READER] ··· {} filas leídas hasta ahora (línea física: {})...",
                    totalLeidas, numeroLineaActual);
        }

        return new RawCsvRow(numeroLineaActual++, registro);
    }

    @Override
    public void update(ExecutionContext executionContext) {
        // Sin estado adicional que persistir en el contexto de ejecución
    }

    @Override
    public void close() throws ItemStreamException {
        log.info("[READER] ■ Cerrando reader. Total filas procesadas por el reader: {}", totalLeidas);
        if (parser != null) {
            try {
                parser.close();
                log.debug("[READER] Parser CSV cerrado correctamente.");
            } catch (IOException e) {
                log.warn("[READER] Error al cerrar el parser CSV del archivo {}", archivoPath, e);
            }
        }
    }
}
