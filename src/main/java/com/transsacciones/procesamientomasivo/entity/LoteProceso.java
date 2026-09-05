package com.transsacciones.procesamientomasivo.entity;


import com.transsacciones.procesamientomasivo.entity.enums.EstadoLote;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "lotes_procesamiento")
@Getter
@Setter
@NoArgsConstructor
public class LoteProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "total_registros")
    private Integer totalRegistros;

    @Column(name = "registros_exitosos")
    private Integer registrosExitosos;

    @Column(name = "registros_fallidos")
    private Integer registrosFallidos;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoLote estado;

    public LoteProceso(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
        this.totalRegistros = 0;
        this.registrosExitosos = 0;
        this.registrosFallidos = 0;
        this.fechaInicio = LocalDateTime.now();
        this.estado = EstadoLote.EN_PROCESO;
    }
}
