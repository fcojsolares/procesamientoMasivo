package com.transsacciones.procesamientomasivo.exception;


import com.transsacciones.procesamientomasivo.dto.RespuestaErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ArchivoInvalidoException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarArchivoInvalido(ArchivoInvalidoException ex,
                                                                    HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(RespuestaErrorDTO.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(LoteNoEncontradoException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarLoteNoEncontrado(LoteNoEncontradoException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(RespuestaErrorDTO.of(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RespuestaErrorDTO> manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException ex,
                                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(RespuestaErrorDTO.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        "El archivo supera el tamaño máximo permitido.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaErrorDTO> manejarErrorGeneral(Exception ex, HttpServletRequest request) {
        return ResponseEntity.internalServerError()
                .body(RespuestaErrorDTO.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Ocurrió un error inesperado al procesar la solicitud: " + ex.getMessage(),
                        request.getRequestURI()));
    }
}
