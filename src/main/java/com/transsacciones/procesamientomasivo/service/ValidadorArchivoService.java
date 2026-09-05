package com.transsacciones.procesamientomasivo.service;


import com.transsacciones.procesamientomasivo.exception.ArchivoInvalidoException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ValidadorArchivoService {

    @Value("${procesamiento.archivo.extension-permitida}")
    private String extensionPermitida;

    @Value("${procesamiento.archivo.encabezados-esperados}")
    private String encabezadosEsperadosCsv;


    public void validar(MultipartFile archivo) {
        log.info("[VALIDADOR] Iniciando validación del archivo: nombre='{}', tamaño={} bytes",
                archivo != null ? archivo.getOriginalFilename() : "null",
                archivo != null ? archivo.getSize() : 0);

        validarPresenciaYExtension(archivo);
        validarEncabezados(archivo);

        log.info("[VALIDADOR] ✔ Archivo '{}' superó todas las validaciones.",
                archivo.getOriginalFilename());
    }

    private void validarPresenciaYExtension(MultipartFile archivo) {
        log.debug("[VALIDADOR] Verificando presencia y extensión...");

        if (archivo == null || archivo.isEmpty()) {
            log.warn("[VALIDADOR] ✖ Archivo nulo o vacío.");
            throw new ArchivoInvalidoException("El archivo no puede estar vacío.");
        }

        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || !nombreOriginal.toLowerCase(Locale.ROOT).endsWith(extensionPermitida)) {
            log.warn("[VALIDADOR] ✖ Extensión inválida para archivo '{}'. Extensión esperada: {}",
                    nombreOriginal, extensionPermitida);
            throw new ArchivoInvalidoException(
                    "Formato de archivo inválido. Solo se aceptan archivos con extensión " + extensionPermitida);
        }

        log.debug("[VALIDADOR] ✔ Extensión correcta: {}", extensionPermitida);
    }

    private void validarEncabezados(MultipartFile archivo) {
        Set<String> encabezadosEsperados = obtenerEncabezadosEsperados();
        log.debug("[VALIDADOR] Verificando encabezados. Esperados: {}", encabezadosEsperados);

        try (var lector = new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(lector)) {

            Set<String> encabezadosArchivo = parser.getHeaderNames().stream()
                    .map(encabezado -> encabezado.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            log.debug("[VALIDADOR] Encabezados encontrados en el archivo: {}", encabezadosArchivo);

            if (encabezadosArchivo.isEmpty()) {
                log.warn("[VALIDADOR] ✖ El archivo no contiene fila de encabezados.");
                throw new ArchivoInvalidoException("El archivo no contiene una fila de encabezados.");
            }

            List<String> encabezadosFaltantes = encabezadosEsperados.stream()
                    .filter(esperado -> !encabezadosArchivo.contains(esperado))
                    .toList();

            if (!encabezadosFaltantes.isEmpty()) {
                log.warn("[VALIDADOR] ✖ Encabezados faltantes: {}", encabezadosFaltantes);
                throw new ArchivoInvalidoException(
                        "El archivo no contiene todos los encabezados requeridos. Encabezados faltantes: "
                                + String.join(", ", encabezadosFaltantes));
            }

            log.debug("[VALIDADOR] ✔ Todos los encabezados obligatorios están presentes.");

        } catch (IOException e) {
            log.error("[VALIDADOR] ✖ Error de lectura al validar encabezados del archivo '{}'",
                    archivo.getOriginalFilename(), e);
            throw new ArchivoInvalidoException("No fue posible leer el archivo para validar su estructura.");
        }
    }

    private Set<String> obtenerEncabezadosEsperados() {
        return Arrays.stream(encabezadosEsperadosCsv.split(","))
                .map(encabezado -> encabezado.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
