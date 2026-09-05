package com.transsacciones.procesamientomasivo.entity.enums;

/**
 * Estados posibles del ciclo de vida de un lote de procesamiento de archivo.
 */
public enum EstadoLote {
    EN_PROCESO,
    COMPLETADO,
    COMPLETADO_CON_ERRORES,
    FALLIDO
}
