package com.transsacciones.procesamientomasivo.service;


import com.transsacciones.procesamientomasivo.batch.LoteJobListener;
import com.transsacciones.procesamientomasivo.dto.RespuestaLoteDTO;
import com.transsacciones.procesamientomasivo.entity.LoteProceso;
import com.transsacciones.procesamientomasivo.exception.ArchivoInvalidoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesadorArchivoService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ValidadorArchivoService validadorArchivoService;
    private final LotePersistenciaService lotePersistenciaService;
    private final JobLauncher asyncJobLauncher;
    private final Job procesarTransaccionesJob;


    public RespuestaLoteDTO procesarArchivo(MultipartFile archivo) {
        String nombreOriginal = archivo.getOriginalFilename();
        log.info("══════════════════════════════════════════════════════════");
        log.info("[SERVICIO] Solicitud de procesamiento recibida para: '{}'", nombreOriginal);
        log.info("[SERVICIO] Tamaño del archivo: {} bytes", archivo.getSize());

        // 1. Validar estructura del archivo
        log.info("[SERVICIO] ► Paso 1/4 — Validando archivo (extensión, encabezados)...");
        validadorArchivoService.validar(archivo);
        log.info("[SERVICIO] ✔ Archivo válido.");

        // 2. Copiar a archivo temporal en disco
        log.info("[SERVICIO] ► Paso 2/4 — Guardando archivo en ubicación temporal...");
        Path archivoTemporal = copiarArchivoTemporal(archivo);
        log.info("[SERVICIO] ✔ Archivo temporal creado: {}", archivoTemporal);

        // 3. Crear lote inicial en BD
        log.info("[SERVICIO] ► Paso 3/4 — Creando registro de lote en BD (estado: EN_PROCESO)...");
        LoteProceso lote = lotePersistenciaService.crearLoteInicial(nombreOriginal);
        log.info("[SERVICIO] ✔ Lote creado con id={}", lote.getId());

        // 4. Lanzar Job de Batch de forma asíncrona
        log.info("[SERVICIO] ► Paso 4/4 — Lanzando Job de Spring Batch en segundo plano...");
        lanzarJobAsincrono(lote.getId(), archivoTemporal);

        log.info("[SERVICIO] ✔ Job lanzado en background. El cliente puede consultar el estado en GET /api/v1/lotes/{}",
                lote.getId());


        return lotePersistenciaService.mapearARespuestaInicial(lote);
    }


    private Path copiarArchivoTemporal(MultipartFile archivo) {
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
            String nombreTemp = "txn_" + timestamp + "_" + archivo.getOriginalFilename();
            Path destino = Files.createTempFile("batch_", "_" + nombreTemp);
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            log.debug("[SERVICIO] Archivo temporal creado en: {} ({} bytes)", destino, Files.size(destino));
            return destino;
        } catch (IOException e) {
            log.error("[SERVICIO] ✖ Error al copiar el archivo a ubicación temporal", e);
            throw new ArchivoInvalidoException("No se pudo almacenar el archivo temporalmente: " + e.getMessage());
        }
    }


    private void lanzarJobAsincrono(Long loteId, Path archivoTemporal) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong(LoteJobListener.PARAM_LOTE_ID, loteId)
                    .addString(LoteJobListener.PARAM_TEMP_FILE, archivoTemporal.toAbsolutePath().toString())
                    // timestamp para garantizar que cada ejecución sea única en los metadatos de Batch
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            log.debug("[SERVICIO] JobParameters — loteId={}, tempFile={}", loteId, archivoTemporal);
            asyncJobLauncher.run(procesarTransaccionesJob, params);
            log.info("[SERVICIO] ✔ Job enviado al pool de hilos de Batch (loteId={}). Retornando 202...", loteId);
        } catch (Exception e) {
            log.error("[SERVICIO] ✖ No se pudo lanzar el Job de Batch para loteId={}", loteId, e);
            lotePersistenciaService.finalizarLoteConFallo(loteId,
                    "No se pudo iniciar el Job de procesamiento: " + e.getMessage());
            throw new ArchivoInvalidoException("Error al iniciar el procesamiento del archivo: " + e.getMessage());
        }
    }
}
