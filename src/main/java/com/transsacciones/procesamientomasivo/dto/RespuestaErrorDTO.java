package com.transsacciones.procesamientomasivo.dto;

import java.time.LocalDateTime;

public record RespuestaErrorDTO(
        LocalDateTime timestamp,
        int estadoHttp,
        String mensaje,
        String ruta
) {
    public static RespuestaErrorDTO of(int estadoHttp, String mensaje, String ruta) {
        return new RespuestaErrorDTO(LocalDateTime.now(), estadoHttp, mensaje, ruta);
    }
}
