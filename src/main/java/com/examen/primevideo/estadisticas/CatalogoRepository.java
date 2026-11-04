package com.examen.primevideo.estadisticas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/** Lee content y sus relaciones (genero, director, reparto) con JDBC en pocas consultas. */
@Repository
class CatalogoRepository {

    private final JdbcTemplate jdbc;

    CatalogoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Catalogo cargar() {
        Map<Integer, String> generos = new HashMap<>();
        jdbc.query("select id, name from genre", rs -> {
            generos.put(rs.getInt(1), rs.getString(2));
        });

        Map<Integer, List<Long>> rasgos = new HashMap<>();
        Map<Integer, List<Integer>> generosPorContenido = new HashMap<>();
        jdbc.query("select content_id, genre_id from content_genre", rs -> {
            int c = rs.getInt(1), g = rs.getInt(2);
            generosPorContenido.computeIfAbsent(c, k -> new ArrayList<>()).add(g);
            rasgos.computeIfAbsent(c, k -> new ArrayList<>()).add(1_000_000L + g);
        });
        jdbc.query("select content_id, person_id, role from content_person", rs -> {
            long prefijo = "DIRECTOR".equals(rs.getString(3)) ? 2_000_000L : 3_000_000L;
            rasgos.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(prefijo * 1_000 + rs.getInt(2));
        });

        List<Catalogo.Fila> filas = new ArrayList<>();
        jdbc.query("""
                select id, content_type, title, release_year, rating, duration_value, duration_unit
                from content order by id
                """, rs -> {
            int id = rs.getInt(1);
            int anio = rs.getInt(4);
            int dur = rs.getInt(6);
            boolean durNula = rs.wasNull();
            filas.add(new Catalogo.Fila(id, rs.getString(2), rs.getString(3), anio == 0 ? null : anio,
                    rs.getString(5), durNula ? null : dur, rs.getString(7),
                    generosPorContenido.getOrDefault(id, List.of()).stream().mapToInt(Integer::intValue).toArray()));
        });

        long[][] caracteristicas = new long[filas.size()][];
        for (int i = 0; i < filas.size(); i++) {
            caracteristicas[i] = rasgos.getOrDefault(filas.get(i).id(), List.of()).stream()
                    .mapToLong(Long::longValue).sorted().distinct().toArray();
        }
        return new Catalogo(filas, caracteristicas, generos);
    }
}
