package com.transsacciones.procesamientomasivo.batch;

import org.apache.commons.csv.CSVRecord;

public record RawCsvRow(int numeroLinea, CSVRecord registro) {
}
