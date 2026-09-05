package com.transsacciones.procesamientomasivo.dto;



import com.transsacciones.procesamientomasivo.entity.enums.EstadoLote;

import java.time.LocalDateTime;


public record RespuestaLoteDTO(
        Long loteId,
        String nombreArchivo,
        EstadoLote estado,
        Integer totalRegistros,
        Integer registrosExitosos,
        Integer registrosFallidos,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin
) {
}
