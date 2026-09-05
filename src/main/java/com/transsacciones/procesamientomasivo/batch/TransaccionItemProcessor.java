package com.transsacciones.procesamientomasivo.batch;


import com.transsacciones.procesamientomasivo.entity.DetalleError;
import com.transsacciones.procesamientomasivo.entity.LoteProceso;
import com.transsacciones.procesamientomasivo.entity.Transaccion;
import com.transsacciones.procesamientomasivo.exception.LineaInvalidaException;
import com.transsacciones.procesamientomasivo.repository.DetalleErrorRepository;
import com.transsacciones.procesamientomasivo.repository.LoteProcesoRepository;
import com.transsacciones.procesamientomasivo.service.ParserTransaccionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;


@Slf4j
@Component
@RequiredArgsConstructor
public class TransaccionItemProcessor implements ItemProcessor<RawCsvRow, Transaccion> {

    /** Clave del contador de registros fallidos en el ExecutionContext del Step. */
    public static final String KEY_FALLIDOS = "registros.fallidos";

    private final ParserTransaccionService parserTransaccionService;
    private final LoteProcesoRepository loteProcesoRepository;
    private final DetalleErrorRepository detalleErrorRepository;

    private StepExecution stepExecution;
    private LoteProceso lote;
    private final Set<String> idsVistosEnElLote = new HashSet<>();

    /**
     * Inyectado por Batch antes de que comience el Step; permite acceder al
     * {@link org.springframework.batch.item.ExecutionContext} para actualizar contadores
     * y obtener el {@code loteId} de los {@link org.springframework.batch.core.JobParameters}.
     */
    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        stepExecution.getExecutionContext().putInt(KEY_FALLIDOS, 0);
        idsVistosEnElLote.clear();

        org.springframework.batch.core.JobParameters jobParameters = stepExecution.getJobParameters();
        if (jobParameters == null) {
            throw new IllegalStateException("El JobExecution no tiene JobParameters. No se puede procesar el lote.");
        }

        Long loteId = jobParameters.getLong(LoteJobListener.PARAM_LOTE_ID);
        if (loteId == null || loteId <= 0L) {
            throw new IllegalStateException("El parámetro 'loteId' del job es obligatorio y debe ser válido.");
        }

        this.lote = loteProcesoRepository.findById(loteId)
                .orElseThrow(() -> new IllegalStateException("Lote no encontrado: " + loteId));

        log.info("[PROCESSOR] ► Iniciando procesamiento para loteId={}. Archivo: {}",
                loteId, jobParameters.getString(LoteJobListener.PARAM_TEMP_FILE));
    }

    @Override
    public Transaccion process(RawCsvRow fila) {
        log.trace("[PROCESSOR] Procesando línea {}: {}", fila.numeroLinea(),
                String.join(",", fila.registro().toList()));
        try {
            Transaccion transaccion = parserTransaccionService.convertir(fila.registro(), lote, idsVistosEnElLote);
            log.debug("[PROCESSOR] ✔ Línea {} válida — id_transaccion='{}'",
                    fila.numeroLinea(), transaccion.getIdTransaccion());
            return transaccion;
        } catch (LineaInvalidaException ex) {
            log.warn("[PROCESSOR] ✖ Línea {} inválida — Motivo: {}", fila.numeroLinea(), ex.getMessage());
            registrarError(fila, ex.getMessage());
            return null; // Spring Batch filtra los null; la fila no llega al Writer
        } catch (Exception ex) {
            log.warn("[PROCESSOR] ✖ Error inesperado en línea {} — {}",
                    fila.numeroLinea(), ex.getMessage(), ex);
            registrarError(fila, "Error inesperado: " + ex.getMessage());
            return null;
        }
    }

    private void registrarError(RawCsvRow fila, String motivo) {
        incrementarFallidos();
        DetalleError error = DetalleError.builder()
                .lote(lote)
                .numeroLinea(fila.numeroLinea())
                .contenidoLinea(String.join(",", fila.registro().toList()))
                .motivoError(motivo)
                .fechaRegistro(LocalDateTime.now())
                .build();
        detalleErrorRepository.save(error);

        int totalFallidos = stepExecution.getExecutionContext().getInt(KEY_FALLIDOS, 0);
        log.debug("[PROCESSOR] Error registrado en detalle_errores. Total fallidos acumulado: {}", totalFallidos);
    }

    private void incrementarFallidos() {
        int fallidos = stepExecution.getExecutionContext().getInt(KEY_FALLIDOS, 0);
        stepExecution.getExecutionContext().putInt(KEY_FALLIDOS, fallidos + 1);
    }
}
