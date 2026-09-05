package com.transsacciones.procesamientomasivo.repository;


import com.transsacciones.procesamientomasivo.entity.LoteProceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoteProcesoRepository extends JpaRepository<LoteProceso, Long> {
}
