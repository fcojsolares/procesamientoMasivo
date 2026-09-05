package com.transsacciones.procesamientomasivo.dto;


public record DetalleErrorDTO(
        Integer numeroLinea,
        String contenidoLinea,
        String motivoError
) {
}
