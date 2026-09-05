package com.transsacciones.procesamientomasivo.batch;


import com.transsacciones.procesamientomasivo.entity.Transaccion;
import com.transsacciones.procesamientomasivo.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class TransaccionItemWriter implements ItemWriter<Transaccion> {


    public static final String KEY_EXITOSOS = "registros.exitosos";

    private final TransaccionRepository transaccionRepository;

    private StepExecution stepExecution;
    private int chunkCount;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        this.chunkCount = 0;
        stepExecution.getExecutionContext().putInt(KEY_EXITOSOS, 0);
        log.info("[WRITER] ► Writer inicializado para loteId={}",
                stepExecution.getJobParameters().getLong(LoteJobListener.PARAM_LOTE_ID));
    }

    @Override
    public void write(Chunk<? extends Transaccion> chunk) {
        if (chunk.isEmpty()) {
            log.debug("[WRITER] Chunk vacío recibido (todas las filas del bloque fueron inválidas), omitiendo escritura.");
            return;
        }

        chunkCount++;
        int exitososAntes = stepExecution.getExecutionContext().getInt(KEY_EXITOSOS, 0);

        log.info("[WRITER] ► Chunk #{} — persistiendo {} transacciones en BD (acumulado previo: {})...",
                chunkCount, chunk.size(), exitososAntes);

        transaccionRepository.saveAll(chunk.getItems());
        incrementarExitosos(chunk.size());

        int exitososDespues = stepExecution.getExecutionContext().getInt(KEY_EXITOSOS, 0);
        log.info("[WRITER] ✔ Chunk #{} persistido. Total exitosos acumulados: {}", chunkCount, exitososDespues);
    }

    private void incrementarExitosos(int cantidad) {
        int exitosos = stepExecution.getExecutionContext().getInt(KEY_EXITOSOS, 0);
        stepExecution.getExecutionContext().putInt(KEY_EXITOSOS, exitosos + cantidad);
    }
}
