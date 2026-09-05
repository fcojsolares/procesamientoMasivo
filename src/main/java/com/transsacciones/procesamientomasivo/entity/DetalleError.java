package com.transsacciones.procesamientomasivo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "detalle_errores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "lote_id", nullable = false)
    private LoteProceso lote;

    @Column(name = "numero_linea", nullable = false)
    private Integer numeroLinea;

    @Column(name = "contenido_linea")
    private String contenidoLinea;

    @Column(name = "motivo_error", nullable = false)
    private String motivoError;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
