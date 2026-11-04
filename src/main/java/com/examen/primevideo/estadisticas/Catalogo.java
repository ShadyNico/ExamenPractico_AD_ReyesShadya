package com.examen.primevideo.estadisticas;

import java.util.List;
import java.util.Map;

/**
 * Datos leidos de primevideodb para procesar en memoria.
 * caracteristicas[i]: ids ordenados de generos (1xxxxxx), directores (2xxxxxx) y reparto (3xxxxxx)
 * del contenido i; se usan para calcular la similitud de Jaccard entre contenidos.
 */
record Catalogo(List<Fila> filas, long[][] caracteristicas, Map<Integer, String> generos) {

    record Fila(int id, String tipo, String titulo, Integer anio, String rating, Integer duracion,
                String unidad, int[] generos) {
    }

    int tamanio() {
        return filas.size();
    }
}
