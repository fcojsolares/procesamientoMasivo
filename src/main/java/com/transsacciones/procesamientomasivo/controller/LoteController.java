package com.transsacciones.procesamientomasivo.controller;


import com.transsacciones.procesamientomasivo.dto.DetalleErrorDTO;
import com.transsacciones.procesamientomasivo.dto.RespuestaLoteDTO;
import com.transsacciones.procesamientomasivo.service.LoteConsultaService;
import com.transsacciones.procesamientomasivo.service.ProcesadorArchivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Slf4j
@Tag(name = "Lotes de Transacciones", description = "Carga masiva y trazabilidad de archivos de transacciones")
@RestController
@RequestMapping("/api/v1/lotes")
@RequiredArgsConstructor
public class LoteController {

    private final ProcesadorArchivoService procesadorArchivoService;
    private final LoteConsultaService loteConsultaService;

    @Operation(
            summary = "Cargar y procesar un archivo CSV de transacciones (asíncrono)",
            description = """
                    Valida el archivo (formato CSV, no vacío, encabezados completos) y lanza el procesamiento
                    en segundo plano mediante Spring Batch.

                    **Retorna HTTP 202 Accepted de inmediato** con el lote en estado `EN_PROCESO`.
                    Use `GET /{loteId}` para hacer polling y conocer el estado final
                    (`COMPLETADO`, `COMPLETADO_CON_ERRORES` o `FALLIDO`).
                    """)
    @ApiResponse(responseCode = "202",
            description = "Procesamiento iniciado en segundo plano — consulte GET /{loteId} para el estado final",
            content = @Content(schema = @Schema(implementation = RespuestaLoteDTO.class)))
    @ApiResponse(responseCode = "400",
            description = "El archivo es inválido: vacío, formato incorrecto o le faltan encabezados obligatorios")
    @PostMapping(value = "/procesar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RespuestaLoteDTO> procesarArchivo(
            @Parameter(description = "Archivo CSV de transacciones a procesar", required = true)
            @RequestParam("archivo") MultipartFile archivo) {

        log.info("[CONTROLLER] POST /api/v1/lotes/procesar — archivo='{}', tamaño={} bytes",
                archivo.getOriginalFilename(), archivo.getSize());

        RespuestaLoteDTO respuesta = procesadorArchivoService.procesarArchivo(archivo);

        log.info("[CONTROLLER] ✔ Respondiendo 202 Accepted — loteId={}, estado={}",
                respuesta.loteId(), respuesta.estado());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }

    @Operation(summary = "Consultar el estado y resumen de un lote procesado",
            description = "Úselo para hacer polling después de un `POST /procesar`. " +
                    "El estado avanza de `EN_PROCESO` a `COMPLETADO`, `COMPLETADO_CON_ERRORES` o `FALLIDO`.")
    @ApiResponse(responseCode = "200", description = "Estado del lote encontrado")
    @ApiResponse(responseCode = "404", description = "No existe un lote con el id indicado")
    @GetMapping("/{loteId}")
    public ResponseEntity<RespuestaLoteDTO> consultarEstado(
            @Parameter(description = "Identificador del lote de procesamiento") @PathVariable Long loteId) {

        log.debug("[CONTROLLER] GET /api/v1/lotes/{}", loteId);
        RespuestaLoteDTO respuesta = loteConsultaService.consultarEstado(loteId);
        log.debug("[CONTROLLER] ✔ Lote {} — estado={}", loteId, respuesta.estado());
        return ResponseEntity.ok(respuesta);
    }

    @Operation(summary = "Consultar el detalle de errores de un lote procesado")
    @ApiResponse(responseCode = "200", description = "Listado de errores del lote (vacío si no hubo errores)")
    @ApiResponse(responseCode = "404", description = "No existe un lote con el id indicado")
    @GetMapping("/{loteId}/errores")
    public ResponseEntity<List<DetalleErrorDTO>> consultarErrores(
            @Parameter(description = "Identificador del lote de procesamiento") @PathVariable Long loteId) {

        log.debug("[CONTROLLER] GET /api/v1/lotes/{}/errores", loteId);
        List<DetalleErrorDTO> errores = loteConsultaService.consultarErrores(loteId);
        log.debug("[CONTROLLER] ✔ {} errores encontrados para lote {}", errores.size(), loteId);
        return ResponseEntity.ok(errores);
    }
}
