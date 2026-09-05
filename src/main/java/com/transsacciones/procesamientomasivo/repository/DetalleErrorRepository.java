package com.transsacciones.procesamientomasivo.repository;


import com.transsacciones.procesamientomasivo.entity.DetalleError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetalleErrorRepository extends JpaRepository<DetalleError, Long> {

    @Query("SELECT d FROM DetalleError d WHERE d.lote.id = :loteId ORDER BY d.numeroLinea ASC")
    List<DetalleError> buscarPorLoteId(@Param("loteId") Long loteId);
}
