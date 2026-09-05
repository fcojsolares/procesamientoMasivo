package com.transsacciones.procesamientomasivo.service;


import com.transsacciones.procesamientomasivo.dto.DetalleErrorDTO;
import com.transsacciones.procesamientomasivo.dto.RespuestaLoteDTO;
import com.transsacciones.procesamientomasivo.entity.LoteProceso;
import com.transsacciones.procesamientomasivo.exception.LoteNoEncontradoException;
import com.transsacciones.procesamientomasivo.repository.DetalleErrorRepository;
import com.transsacciones.procesamientomasivo.repository.LoteProcesoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoteConsultaService {

    private final LoteProcesoRepository loteProcesoRepository;
    private final DetalleErrorRepository detalleErrorRepository;

    public RespuestaLoteDTO consultarEstado(Long loteId) {
        LoteProceso lote = obtenerLoteOFallar(loteId);
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

    public List<DetalleErrorDTO> consultarErrores(Long loteId) {
        obtenerLoteOFallar(loteId);
        return detalleErrorRepository.buscarPorLoteId(loteId).stream()
                .map(error -> new DetalleErrorDTO(
                        error.getNumeroLinea(),
                        error.getContenidoLinea(),
                        error.getMotivoError()))
                .toList();
    }

    private LoteProceso obtenerLoteOFallar(Long loteId) {
        return loteProcesoRepository.findById(loteId)
                .orElseThrow(() -> new LoteNoEncontradoException(loteId));
    }
}
