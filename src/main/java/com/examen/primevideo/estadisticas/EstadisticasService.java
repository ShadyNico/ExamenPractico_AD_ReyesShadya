package com.examen.primevideo.estadisticas;

import com.examen.primevideo.estadisticas.Resultado.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * Etapa 3: procesamiento concurrente del catalogo.
 *
 *  - Un hilo COORDINADOR recibe el trabajo, asi la peticion HTTP responde de inmediato (202).
 *  - El catalogo se divide en N bloques y un pool de hilos TRABAJADORES los procesa en paralelo.
 *    Cada hilo calcula estadisticas de su bloque y, para cada contenido, busca el contenido
 *    mas parecido de TODO el catalogo (similitud de Jaccard por generos, directores y reparto:
 *    ~93 millones de comparaciones, trabajo intensivo de CPU).
 *  - El progreso y el resultado se envian por WebSocket (/topic/estadisticas).
 *
 * Para comparar, procesarSinHilos() hace lo mismo de forma secuencial en el hilo de la peticion.
 */
@Service
public class EstadisticasService {

    private static final Logger log = LoggerFactory.getLogger(EstadisticasService.class);
    public static final int MAX_HILOS = 16;

    private final CatalogoRepository repo;
    private final SimpMessagingTemplate ws;
    private final ExecutorService coordinador = Executors.newSingleThreadExecutor(nombrados("coordinador-estadisticas"));
    private final ExecutorService trabajadores = Executors.newFixedThreadPool(MAX_HILOS, nombrados("trabajador-estadisticas"));
    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private volatile Resultado ultimo;

    public EstadisticasService(CatalogoRepository repo, SimpMessagingTemplate ws) {
        this.repo = repo;
        this.ws = ws;
    }

    /** Lanza el procesamiento en segundo plano y retorna enseguida. */
    public Map<String, Object> iniciar(int hilos) {
        int n = Math.clamp(hilos, 1, MAX_HILOS);
        if (!enCurso.compareAndSet(false, true)) {
            throw new ResponseStatusException(CONFLICT, "Ya hay un procesamiento en curso");
        }
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] solicitud {} recibida: se delega a {} hilos y se responde 202", Thread.currentThread().getName(), jobId, n);
        coordinador.submit(() -> {
            try {
                ultimo = procesar(jobId, n);
                log.info("[{}] trabajo {} terminado: calculo {} ms con {} hilos", Thread.currentThread().getName(),
                        jobId, ultimo.msProcesamiento(), n);
                publicar(Map.of("tipo", "COMPLETADO", "resultado", ultimo));
            } catch (Exception e) {
                log.error("Error en el procesamiento {}", jobId, e);
                publicar(Map.of("tipo", "ERROR", "jobId", jobId, "mensaje", String.valueOf(e.getMessage())));
            } finally {
                enCurso.set(false);
            }
        });
        publicar(Map.of("tipo", "INICIADO", "jobId", jobId, "hilos", n));
        return Map.of("jobId", jobId, "estado", "EN_PROCESO", "hilos", n,
                "hiloPeticion", Thread.currentThread().getName());
    }

    /** Version SIN hilos: todo se calcula en el hilo de la peticion HTTP, que queda bloqueado. */
    public Resultado procesarSinHilos() {
        long inicio = System.currentTimeMillis();
        Catalogo cat = repo.cargar();
        long msCarga = System.currentTimeMillis() - inicio;
        long t0 = System.currentTimeMillis();
        ResultadoParcial parcial = procesarBloque(cat, 0, cat.tamanio(), new AtomicInteger(), new AtomicIntegerArray(1), 0);
        long msProc = System.currentTimeMillis() - t0;
        Resultado r = combinar("sin-hilos", "SIN_HILOS", 1, cat, List.of(parcial), msCarga, msProc, inicio);
        log.info("[{}] version sin hilos terminada: calculo {} ms (la peticion HTTP estuvo bloqueada todo ese tiempo)",
                Thread.currentThread().getName(), msProc);
        ultimo = r;
        return r;
    }

    public Resultado ultimo() {
        return ultimo;
    }

    // ------------------------------------------------------------------

    private Resultado procesar(String jobId, int hilos) throws Exception {
        long inicio = System.currentTimeMillis();
        Catalogo cat = repo.cargar();
        long msCarga = System.currentTimeMillis() - inicio;

        int total = cat.tamanio();
        int tamBloque = (total + hilos - 1) / hilos;
        AtomicInteger progreso = new AtomicInteger();
        AtomicIntegerArray porBloque = new AtomicIntegerArray(hilos);
        int[] tamanios = new int[hilos];

        long t0 = System.currentTimeMillis();
        List<Future<ResultadoParcial>> futuros = new ArrayList<>();
        for (int b = 0; b < hilos; b++) {
            int desde = Math.min(b * tamBloque, total), hasta = Math.min(desde + tamBloque, total);
            int bloque = b;
            tamanios[b] = hasta - desde;
            futuros.add(trabajadores.submit(() -> procesarBloque(cat, desde, hasta, progreso, porBloque, bloque)));
        }
        // el coordinador informa el avance cada 250 ms mientras los trabajadores calculan
        while (!futuros.stream().allMatch(Future::isDone)) {
            publicarProgreso(jobId, progreso.get(), total, porBloque, tamanios);
            Thread.sleep(250);
        }
        publicarProgreso(jobId, total, total, porBloque, tamanios);

        List<ResultadoParcial> parciales = new ArrayList<>();
        for (Future<ResultadoParcial> f : futuros) {
            parciales.add(f.get());
        }
        long msProc = System.currentTimeMillis() - t0;
        return combinar(jobId, "HILOS", hilos, cat, parciales, msCarga, msProc, inicio);
    }

    /** Trabajo de UN hilo sobre los contenidos [desde, hasta). */
    private ResultadoParcial procesarBloque(Catalogo cat, int desde, int hasta, AtomicInteger progreso,
                                            AtomicIntegerArray porBloque, int bloque) {
        long t0 = System.currentTimeMillis();
        ResultadoParcial r = new ResultadoParcial(Thread.currentThread().getName());
        long[][] rasgos = cat.caracteristicas();
        int n = cat.tamanio();

        for (int i = desde; i < hasta; i++) {
            Catalogo.Fila f = cat.filas().get(i);
            r.porTipo.merge(f.tipo(), 1, Integer::sum);
            for (int g : f.generos()) r.porGenero.merge(g, 1, Integer::sum);
            if (f.anio() != null) {
                int[] par = r.porAnio.computeIfAbsent(f.anio(), k -> new int[2]);
                par["TV_SHOW".equals(f.tipo()) ? 1 : 0]++;
            }
            if (f.rating() != null) r.porRating.merge(f.rating(), 1, Integer::sum);
            if (f.duracion() != null && "MINUTES".equals(f.unidad()) && "MOVIE".equals(f.tipo())) {
                r.minutosPeliculas += f.duracion();
                r.peliculasConDuracion++;
            }
            if (f.duracion() != null && "SEASONS".equals(f.unidad())) {
                r.temporadasSeries += f.duracion();
                r.seriesConDuracion++;
            }

            // contenido mas parecido de todo el catalogo (similitud de Jaccard)
            long[] a = rasgos[i];
            if (a.length >= 3) {
                double mejor = 0;
                int mejorJ = -1, mejoresComunes = 0;
                for (int j = 0; j < n; j++) {
                    long[] b = rasgos[j];
                    if (j == i || b.length < 3) continue;
                    int comunes = interseccion(a, b);
                    r.comparaciones++;
                    if (comunes == 0) continue;
                    double s = (double) comunes / (a.length + b.length - comunes);
                    if (s > mejor || (s == mejor && comunes > mejoresComunes)) {
                        mejor = s;
                        mejorJ = j;
                        mejoresComunes = comunes;
                    }
                }
                if (mejorJ >= 0) {
                    r.paresSimilares.add(new ResultadoParcial.Par(Math.min(i, mejorJ), Math.max(i, mejorJ), mejor, mejoresComunes));
                }
            }
            r.procesados++;
            progreso.incrementAndGet();
            porBloque.incrementAndGet(bloque);
        }
        r.milisegundos = System.currentTimeMillis() - t0;
        log.info("[{}] proceso contenidos {}..{} ({} contenidos, {} comparaciones) en {} ms",
                r.hilo, desde, hasta - 1, r.procesados, r.comparaciones, r.milisegundos);
        return r;
    }

    /** Elementos en comun entre dos arreglos ordenados (merge en O(n + m)). */
    static int interseccion(long[] a, long[] b) {
        int i = 0, j = 0, c = 0;
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { c++; i++; j++; }
            else if (a[i] < b[j]) i++;
            else j++;
        }
        return c;
    }

    private Resultado combinar(String jobId, String modo, int hilos, Catalogo cat, List<ResultadoParcial> parciales,
                               long msCarga, long msProc, long inicio) {
        Map<String, Integer> porTipo = new TreeMap<>();
        Map<Integer, Integer> porGenero = new HashMap<>();
        Map<Integer, int[]> porAnio = new TreeMap<>();
        Map<String, Integer> porRating = new HashMap<>();
        long minutos = 0, temporadas = 0, comparaciones = 0;
        int conMin = 0, conTemp = 0;
        Map<Long, ResultadoParcial.Par> pares = new HashMap<>();
        for (ResultadoParcial p : parciales) {
            p.porTipo.forEach((k, v) -> porTipo.merge(k, v, Integer::sum));
            p.porGenero.forEach((k, v) -> porGenero.merge(k, v, Integer::sum));
            p.porAnio.forEach((k, v) -> {
                int[] t = porAnio.computeIfAbsent(k, x -> new int[2]);
                t[0] += v[0];
                t[1] += v[1];
            });
            p.porRating.forEach((k, v) -> porRating.merge(k, v, Integer::sum));
            minutos += p.minutosPeliculas;
            conMin += p.peliculasConDuracion;
            temporadas += p.temporadasSeries;
            conTemp += p.seriesConDuracion;
            comparaciones += p.comparaciones;
            for (ResultadoParcial.Par par : p.paresSimilares) pares.putIfAbsent((long) par.a() * 100_000 + par.b(), par);
        }

        List<Conteo> generos = porGenero.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()).limit(15)
                .map(e -> new Conteo(cat.generos().getOrDefault(e.getKey(), "?"), e.getValue())).toList();
        int ultimoAnio = porAnio.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<PorAnio> anios = porAnio.entrySet().stream().filter(e -> e.getKey() > ultimoAnio - 20)
                .map(e -> new PorAnio(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        List<Conteo> ratings = porRating.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(10)
                .map(e -> new Conteo(e.getKey(), e.getValue())).toList();
        List<Similar> similares = pares.values().stream()
                .sorted(Comparator.comparingDouble(ResultadoParcial.Par::similitud).reversed()
                        .thenComparing(Comparator.comparingInt(ResultadoParcial.Par::comunes).reversed()))
                .limit(10)
                .map(p -> new Similar(cat.filas().get(p.a()).id(), cat.filas().get(p.a()).titulo(),
                        cat.filas().get(p.b()).id(), cat.filas().get(p.b()).titulo(),
                        Math.round(p.similitud() * 1000) / 1000.0, p.comunes()))
                .toList();
        List<DetalleHilo> detalle = parciales.stream()
                .map(p -> new DetalleHilo(p.hilo, p.procesados, p.comparaciones, p.milisegundos))
                .collect(Collectors.toList());
        long fin = System.currentTimeMillis();
        return new Resultado(jobId, modo, hilos, cat.tamanio(), porTipo, generos, anios, ratings,
                conMin == 0 ? 0 : Math.round(10.0 * minutos / conMin) / 10.0,
                conTemp == 0 ? 0 : Math.round(100.0 * temporadas / conTemp) / 100.0,
                similares, detalle, comparaciones, msCarga, msProc, fin - inicio,
                Thread.currentThread().getName(), inicio, fin);
    }

    private void publicarProgreso(String jobId, int hechos, int total, AtomicIntegerArray porBloque, int[] tamanios) {
        List<Integer> porHilo = new ArrayList<>();
        for (int b = 0; b < tamanios.length; b++) {
            porHilo.add(tamanios[b] == 0 ? 100 : (int) (100L * porBloque.get(b) / tamanios[b]));
        }
        publicar(Map.of("tipo", "PROGRESO", "jobId", jobId, "procesados", hechos, "total", total,
                "porcentaje", total == 0 ? 100 : (int) (100L * hechos / total), "porHilo", porHilo));
    }

    private void publicar(Map<String, Object> mensaje) {
        ws.convertAndSend("/topic/estadisticas", (Object) mensaje);
    }

    private static ThreadFactory nombrados(String prefijo) {
        AtomicInteger n = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefijo + "-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    @PreDestroy
    void cerrar() {
        coordinador.shutdownNow();
        trabajadores.shutdownNow();
    }
}
