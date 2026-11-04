package com.examen.primevideo.estadisticas;

import java.util.*;

/**
 * Lo que calcula UN hilo sobre su bloque de contenidos. Cada hilo tiene su propia
 * instancia (no se comparte), por eso no necesita sincronizacion; al final se combinan.
 */
class ResultadoParcial {

    final String hilo;
    int procesados;
    long comparaciones;
    long milisegundos;

    final Map<String, Integer> porTipo = new TreeMap<>();
    final Map<Integer, Integer> porGenero = new HashMap<>();
    final Map<Integer, int[]> porAnio = new TreeMap<>();          // anio -> [peliculas, series]
    final Map<String, Integer> porRating = new HashMap<>();
    long minutosPeliculas;
    int peliculasConDuracion;
    long temporadasSeries;
    int seriesConDuracion;
    final List<Par> paresSimilares = new ArrayList<>();          // mejor pareja de cada contenido

    record Par(int a, int b, double similitud, int comunes) {
    }

    ResultadoParcial(String hilo) {
        this.hilo = hilo;
    }
}
