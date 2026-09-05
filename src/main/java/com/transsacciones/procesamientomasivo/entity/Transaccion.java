package com.transsacciones.procesamientomasivo.entity;


import com.transsacciones.procesamientomasivo.entity.enums.TipoOperacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transacciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_transaccion", nullable = false, unique = true)
    private String idTransaccion;

    @Column(name = "cuenta_origen", nullable = false)
    private String cuentaOrigen;

    @Column(name = "cuenta_destino", nullable = false)
    private String cuentaDestino;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tipo_operacion", nullable = false)
    private TipoOperacion tipoOperacion;

    @ManyToOne
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteProceso lote;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
