package com.transsacciones.procesamientomasivo.repository;


import com.transsacciones.procesamientomasivo.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    boolean existsByIdTransaccion(String idTransaccion);
}
