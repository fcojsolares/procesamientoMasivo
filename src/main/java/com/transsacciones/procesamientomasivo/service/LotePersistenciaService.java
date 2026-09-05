package com.transsacciones.procesamientomasivo.service;


import com.transsacciones.procesamientomasivo.dto.RespuestaLoteDTO;
import com.transsacciones.procesamientomasivo.entity.LoteProceso;
import com.transsacciones.procesamientomasivo.entity.enums.EstadoLote;
import com.transsacciones.procesamientomasivo.repository.LoteProcesoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class LotePersistenciaService {

    private final LoteProcesoRepository loteProcesoRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoteProceso crearLoteInicial(String nombreArchivo) {
        log.info("[PERSISTENCIA] Creando lote inicial para archivo '{}'", nombreArchivo);
        LoteProceso lote = loteProcesoRepository.save(new LoteProceso(nombreArchivo));
        log.info("[PERSISTENCIA] ✔ Lote creado — id={}, estado={}, fechaInicio={}",
                lote.getId(), lote.getEstado(), lote.getFechaInicio());
        return lote;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RespuestaLoteDTO finalizarLote(Long loteId, int total, int exitosos, int fallidos) {
        log.info("[PERSISTENCIA] Finalizando lote id={} — total={}, exitosos={}, fallidos={}",
                loteId, total, exitosos, fallidos);

        LoteProceso lote = obtenerLoteObligatorio(loteId);
        lote.setTotalRegistros(total);
        lote.setRegistrosExitosos(exitosos);
        lote.setRegistrosFallidos(fallidos);
        lote.setFechaFin(LocalDateTime.now());
        lote.setEstado(determinarEstadoFinal(exitosos, fallidos));

        RespuestaLoteDTO respuesta = mapearARespuesta(loteProcesoRepository.save(lote));
        log.info("[PERSISTENCIA] ✔ Lote id={} finalizado con estado={}. Duración: {} → {}",
                loteId, respuesta.estado(), respuesta.fechaInicio(), respuesta.fechaFin());
        return respuesta;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RespuestaLoteDTO finalizarLoteConFallo(Long loteId, String motivo) {
        log.error("[PERSISTENCIA] ✖ Marcando lote id={} como FALLIDO. Motivo: {}", loteId, motivo);
        LoteProceso lote = obtenerLoteObligatorio(loteId);
        lote.setFechaFin(LocalDateTime.now());
        lote.setEstado(EstadoLote.FALLIDO);
        RespuestaLoteDTO respuesta = mapearARespuesta(loteProcesoRepository.save(lote));
        log.warn("[PERSISTENCIA] Lote id={} marcado como FALLIDO en BD.", loteId);
        return respuesta;
    }


    public RespuestaLoteDTO mapearARespuestaInicial(LoteProceso lote) {
        return mapearARespuesta(lote);
    }


    private LoteProceso obtenerLoteObligatorio(Long loteId) {
        return loteProcesoRepository.findById(loteId)
                .orElseThrow(() -> {
                    log.error("[PERSISTENCIA] ✖ No se encontró el lote id={}", loteId);
                    return new IllegalStateException("No fue posible recuperar el lote id " + loteId);
                });
    }

    private EstadoLote determinarEstadoFinal(int exitosos, int fallidos) {
        if (exitosos == 0) {
            log.warn("[PERSISTENCIA] Ningún registro fue insertado exitosamente — estado: FALLIDO");
            return EstadoLote.FALLIDO;
        }
        EstadoLote estado = fallidos == 0 ? EstadoLote.COMPLETADO : EstadoLote.COMPLETADO_CON_ERRORES;
        log.info("[PERSISTENCIA] Estado determinado: {}", estado);
        return estado;
    }

    private RespuestaLoteDTO mapearARespuesta(LoteProceso lote) {
        return new RespuestaLoteDTO(
                lote.getId(),
                lote.getNombreArchivo(),
                lote.getEstado(),
                lote.getTotalRegistros(),
                lote.getRegistrosExitosos(),
                lote.getRegistrosFallidos(),
                lote.getFechaInicio(),
                lote.getFechaFin()
        );
    }
}
