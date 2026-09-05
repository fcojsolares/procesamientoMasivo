package com.transsacciones.procesamientomasivo.batch;


import com.transsacciones.procesamientomasivo.service.LotePersistenciaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;


@Slf4j
@Component
@RequiredArgsConstructor
public class LoteJobListener implements JobExecutionListener {


    public static final String PARAM_LOTE_ID   = "loteId";

    public static final String PARAM_TEMP_FILE = "tempFile";

    private final LotePersistenciaService lotePersistenciaService;



    @Override
    public void beforeJob(JobExecution jobExecution) {
        if (jobExecution.getJobParameters() == null) {
            log.warn("[JOB] El job se ejecutó sin JobParameters. Se ignorará el inicio automático.");
            return;
        }

        Long loteId    = jobExecution.getJobParameters().getLong(PARAM_LOTE_ID);
        String archivo = jobExecution.getJobParameters().getString(PARAM_TEMP_FILE);

        log.info(" [JOB] INICIO DE PROCESAMIENTO BATCH ");
        log.info(" loteId         : {}",      loteId);
        log.info(" jobExecutionId : {}",      jobExecution.getId());
        log.info(" archivo tmp    : {}",      archivo);
        log.info(" inicio         : {}",      LocalDateTime.now());

    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getJobParameters() == null) {
            log.warn("[JOB] El job se ejecutó sin JobParameters; no se actualizará el lote en BD.");
            return;
        }

        Long loteId       = jobExecution.getJobParameters().getLong(PARAM_LOTE_ID);
        String tempFilePath = jobExecution.getJobParameters().getString(PARAM_TEMP_FILE);
        BatchStatus status  = jobExecution.getStatus();

        String duracion = calcularDuracion(jobExecution);

        int totalExitosos = 0;
        int totalFallidos = 0;
        for (StepExecution step : jobExecution.getStepExecutions()) {
            int exitososStep = step.getExecutionContext().getInt(TransaccionItemWriter.KEY_EXITOSOS, 0);
            int fallidosStep = step.getExecutionContext().getInt(TransaccionItemProcessor.KEY_FALLIDOS, 0);
            log.info("[JOB] Step '{}' — exitosos={}, fallidos={}, status={}",
                    step.getStepName(), exitososStep, fallidosStep, step.getStatus());
            totalExitosos += exitososStep;
            totalFallidos += fallidosStep;
        }

        int totalRegistros = totalExitosos + totalFallidos;


        log.info(" [JOB] FIN DE PROCESAMIENTO BATCH ");
        log.info("  loteId          : {}",   loteId);
        log.info("  status Batch    : {}",   status);
        log.info("  totalRegistros  : {}",   totalRegistros);
        log.info("  exitosos        : {}",   totalExitosos);
        log.info("  fallidos        : {}",   totalFallidos);
        log.info(" duración        : {}",   duracion);


        try {
            if (status.isUnsuccessful()) {
                String motivo = jobExecution.getAllFailureExceptions().stream()
                        .findFirst()
                        .map(Throwable::getMessage)
                        .orElse("Error desconocido en el Job de Batch.");
                log.error("[JOB] ✖ Job terminó con fallo para loteId={}. Motivo: {}", loteId, motivo);
                lotePersistenciaService.finalizarLoteConFallo(loteId, motivo);
            } else {
                lotePersistenciaService.finalizarLote(loteId, totalRegistros, totalExitosos, totalFallidos);
                log.info("[JOB] ✔ Lote {} actualizado en BD con estado final.", loteId);
            }
        } finally {
            eliminarArchivoTemporal(tempFilePath);
        }
    }


    private String calcularDuracion(JobExecution jobExecution) {
        if (jobExecution.getStartTime() == null || jobExecution.getEndTime() == null) {
            return "N/A";
        }
        Duration duracion = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
        return String.format("%d min %d seg %d ms",
                duracion.toMinutesPart(), duracion.toSecondsPart(), duracion.toMillisPart());
    }

    private void eliminarArchivoTemporal(String tempFilePath) {
        if (tempFilePath == null) return;
        try {
            boolean eliminado = Files.deleteIfExists(Path.of(tempFilePath));
            if (eliminado) {
                log.info("[JOB] Archivo temporal eliminado: {}", tempFilePath);
            } else {
                log.debug("[JOB] El archivo temporal ya no existía: {}", tempFilePath);
            }
        } catch (Exception e) {
            log.warn("[JOB] No se pudo eliminar el archivo temporal: {}", tempFilePath, e);
        }
    }
}
