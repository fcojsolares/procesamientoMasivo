package com.transsacciones.procesamientomasivo.exception;


public class LoteNoEncontradoException extends RuntimeException {

    public LoteNoEncontradoException(Long loteId) {
        super("No se encontró el lote de procesamiento con id: " + loteId);
    }
}
