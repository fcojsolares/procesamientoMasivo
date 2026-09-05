package com.transsacciones.procesamientomasivo.service;


import com.transsacciones.procesamientomasivo.entity.LoteProceso;
import com.transsacciones.procesamientomasivo.entity.Transaccion;
import com.transsacciones.procesamientomasivo.entity.enums.TipoOperacion;
import com.transsacciones.procesamientomasivo.exception.LineaInvalidaException;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;


@Service
public class ParserTransaccionService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int LONGITUD_MAXIMA_CUENTA = 30;
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999999999.99");


    public Transaccion convertir(CSVRecord registro, LoteProceso lote, Set<String> idsVistosEnElLote) {
        String idTransaccion = obtenerCampoObligatorio(registro, "id_transaccion");
        String cuentaOrigen = obtenerCampoObligatorio(registro, "cuenta_origen");
        String cuentaDestino = obtenerCampoObligatorio(registro, "cuenta_destino");
        String montoTexto = obtenerCampoObligatorio(registro, "monto");
        String fechaHoraTexto = obtenerCampoObligatorio(registro, "fecha_hora");
        String tipoOperacionTexto = obtenerCampoObligatorio(registro, "tipo_operacion");

        validarLongitudCuenta(cuentaOrigen, "cuenta_origen");
        validarLongitudCuenta(cuentaDestino, "cuenta_destino");

        if (cuentaOrigen.equalsIgnoreCase(cuentaDestino)) {
            throw new LineaInvalidaException("La cuenta origen y la cuenta destino no pueden ser iguales.");
        }

        if (!idsVistosEnElLote.add(idTransaccion)) {
            throw new LineaInvalidaException(
                    "El id_transaccion '" + idTransaccion + "' está duplicado dentro del mismo archivo.");
        }

        BigDecimal monto = parsearMonto(montoTexto);
        LocalDateTime fechaHora = parsearFechaHora(fechaHoraTexto);
        TipoOperacion tipoOperacion = parsearTipoOperacion(tipoOperacionTexto);

        return Transaccion.builder()
                .idTransaccion(idTransaccion)
                .cuentaOrigen(cuentaOrigen)
                .cuentaDestino(cuentaDestino)
                .monto(monto)
                .fechaHora(fechaHora)
                .tipoOperacion(tipoOperacion)
                .lote(lote)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private String obtenerCampoObligatorio(CSVRecord registro, String nombreColumna) {
        if (!registro.isMapped(nombreColumna)) {
            throw new LineaInvalidaException("No existe la columna requerida: " + nombreColumna);
        }
        String valor = registro.get(nombreColumna);
        if (valor == null || valor.trim().isEmpty()) {
            throw new LineaInvalidaException("El campo '" + nombreColumna + "' es obligatorio y viene vacío.");
        }
        return valor.trim();
    }

    private void validarLongitudCuenta(String cuenta, String nombreColumna) {
        if (cuenta.length() > LONGITUD_MAXIMA_CUENTA) {
            throw new LineaInvalidaException(
                    "El campo '" + nombreColumna + "' excede la longitud máxima permitida ("
                            + LONGITUD_MAXIMA_CUENTA + ").");
        }
    }

    private BigDecimal parsearMonto(String montoTexto) {
        try {
            BigDecimal monto = new BigDecimal(montoTexto).setScale(2, java.math.RoundingMode.HALF_UP);
            if (monto.compareTo(BigDecimal.ZERO) <= 0) {
                throw new LineaInvalidaException("El campo 'monto' debe ser un valor mayor a cero.");
            }
            if (monto.compareTo(MONTO_MAXIMO) > 0) {
                throw new LineaInvalidaException(
                        "El campo 'monto' excede el máximo permitido (" + MONTO_MAXIMO + "): '" + montoTexto + "'.");
            }
            return monto;
        } catch (NumberFormatException e) {
            throw new LineaInvalidaException("El campo 'monto' no es un valor numérico válido: '" + montoTexto + "'.");
        }
    }

    private LocalDateTime parsearFechaHora(String fechaHoraTexto) {
        try {
            return LocalDateTime.parse(fechaHoraTexto, FORMATO_FECHA);
        } catch (DateTimeParseException e) {
            throw new LineaInvalidaException(
                    "El campo 'fecha_hora' no tiene el formato esperado (yyyy-MM-ddTHH:mm:ss): '"
                            + fechaHoraTexto + "'.");
        }
    }

    private TipoOperacion parsearTipoOperacion(String tipoOperacionTexto) {
        try {
            return TipoOperacion.valueOf(tipoOperacionTexto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LineaInvalidaException(
                    "El campo 'tipo_operacion' contiene un valor no soportado: '" + tipoOperacionTexto + "'.");
        }
    }
}
