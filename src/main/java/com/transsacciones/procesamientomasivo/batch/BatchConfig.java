package com.transsacciones.procesamientomasivo.batch;

import com.transsacciones.procesamientomasivo.entity.Transaccion;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

@Configuration
public class BatchConfig {

    @Bean
    public JobLauncher asyncJobLauncher(
            JobRepository jobRepository,
            @Value("${procesamiento.batch.thread-pool.core-size:4}") int coreSize,
            @Value("${procesamiento.batch.thread-pool.max-size:8}") int maxSize,
            @Value("${procesamiento.batch.thread-pool.queue-capacity:10}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("batch-job-");
        executor.initialize();

        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(executor);
        return launcher;
    }

    @Bean
    public Job procesarTransaccionesJob(JobRepository jobRepository,
                                        Step procesarTransaccionesStep,
                                        LoteJobListener loteJobListener) {
        return new JobBuilder("procesarTransaccionesJob", jobRepository)
                .listener(loteJobListener)
                .start(procesarTransaccionesStep)
                .build();
    }

    @Bean
    public Step procesarTransaccionesStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          CsvItemReader csvItemReader,
                                          TransaccionItemProcessor processor,
                                          TransaccionItemWriter writer,
                                          @Value("${procesamiento.archivo.tamano-bloque:500}") int tamanoBloque) {
        return new StepBuilder("procesarTransaccionesStep", jobRepository)
                .<RawCsvRow, Transaccion>chunk(tamanoBloque, transactionManager)
                .reader(csvItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }


    @Bean
    @StepScope
    public CsvItemReader csvItemReader(
            @Value("#{jobParameters['tempFile']}") String tempFile) {
        if (tempFile == null || tempFile.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'tempFile' del job es obligatorio.");
        }
        return new CsvItemReader(Path.of(tempFile));
    }
}
