// =====================================================================
// CRUD de la tabla content (primevideodb)
// =====================================================================
const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

let pagina = 0, totalPaginas = 1;
const resaltar = new Set();

// Identificador de este navegador: viaja en X-Cliente y vuelve en los eventos del WebSocket
const clienteId = 'cliente-' + Math.random().toString(36).slice(2, 6);
const conCliente = (opciones = {}) => ({ ...opciones, headers: { ...(opciones.headers || {}), 'X-Cliente': clienteId } });

async function cargar() {
  const params = new URLSearchParams({ pagina, tamanio: 12, buscar: $('#buscar').value, tipo: $('#tipo').value });
  const r = await fetch('api/content?' + params);
  if (!r.ok) return;
  const p = await r.json();
  totalPaginas = Math.max(1, p.totalPaginas);
  $('#total').textContent = `${p.totalElementos.toLocaleString('es')} registros`;
  $('#pag-info').textContent = `Página ${p.pagina + 1} de ${totalPaginas}`;
  $('#actualizado').textContent = 'cargado: ' + new Date().toLocaleTimeString('es');
  $('#filas').innerHTML = p.contenido.map((c) => `
    <tr class="${resaltar.has(c.id) ? 'resaltada' : ''}">
      <td>${c.id}</td>
      <td>${esc(c.sourceId)}</td>
      <td><span class="tipo ${esc(c.contentType)}">${esc(c.contentType)}</span></td>
      <td class="titulo">${esc(c.title)}</td>
      <td>${c.releaseYear ?? '-'}</td>
      <td>${esc(c.rating ?? '-')}</td>
      <td>${esc(c.durationText ?? '-')}</td>
      <td>${c.dateAdded ?? '-'}</td>
      <td>
        <button class="mini sec" data-editar="${c.id}">Editar</button>
        <button class="mini peligro" data-eliminar="${c.id}">Eliminar</button>
      </td>
    </tr>`).join('');
  resaltar.clear();
}

let temporizador;
function recargar() {
  clearTimeout(temporizador);
  temporizador = setTimeout(cargar, 200);
}

$('#buscar').addEventListener('input', () => { pagina = 0; recargar(); });
$('#tipo').addEventListener('change', () => { pagina = 0; cargar(); });
$('#btn-recargar').onclick = cargar;
$('#pag-ant').onclick = () => { if (pagina > 0) { pagina--; cargar(); } };
$('#pag-sig').onclick = () => { if (pagina < totalPaginas - 1) { pagina++; cargar(); } };

// ---------- Registrar / editar ----------
function abrir(c) {
  const f = $('#form');
  f.reset();
  $('#error').textContent = '';
  $('#dlg-titulo').textContent = c ? `Editar contenido #${c.id}` : 'Nuevo contenido';
  if (c) {
    for (const k of ['id', 'title', 'contentType', 'sourceId', 'releaseYear', 'rating', 'dateAdded', 'durationValue', 'description']) {
      f[k].value = c[k] ?? '';
    }
    f.durationUnit.value = c.durationUnit ?? 'MINUTES';
  }
  $('#dlg').showModal();
}

$('#btn-nuevo').onclick = () => abrir(null);
$('#btn-cancelar').onclick = () => $('#dlg').close();

$('#filas').addEventListener('click', async (ev) => {
  const b = ev.target.closest('button');
  if (!b) return;
  if (b.dataset.editar) {
    abrir(await (await fetch('api/content/' + b.dataset.editar)).json());
  }
  if (b.dataset.eliminar && confirm(`¿Eliminar el contenido #${b.dataset.eliminar}?`)) {
    const r = await fetch('api/content/' + b.dataset.eliminar, conCliente({ method: 'DELETE' }));
    if (r.ok) cargar();
  }
});

$('#form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const datos = Object.fromEntries(new FormData(ev.target));
  const id = datos.id;
  delete datos.id;
  for (const k in datos) if (datos[k] === '') datos[k] = null;
  const r = await fetch('api/content' + (id ? '/' + id : ''), conCliente({
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(datos),
  }));
  if (r.ok) {
    const c = await r.json();
    resaltar.add(c.id);
    $('#dlg').close();
    cargar();
  } else {
    const e = await r.json().catch(() => ({}));
    $('#error').textContent = e.errores ? Object.entries(e.errores).map(([k, v]) => `${k}: ${v}`).join(' · ') : (e.detail || 'Error ' + r.status);
  }
});

cargar();

// =====================================================================
// Etapa 2: WebSocket (STOMP). Los cambios hechos por cualquier cliente
// llegan aqui sin recargar la pagina.
// =====================================================================
$('#yo').textContent = 'cliente: ' + clienteId;
const wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + location.pathname.replace(/[^/]*$/, '') + 'ws';
const stomp = new StompJs.Client({ brokerURL: wsUrl, reconnectDelay: 3000 });

stomp.onConnect = () => {
  $('#ws-estado').textContent = 'WebSocket conectado';
  $('#ws-estado').className = 'pill on';
  stomp.subscribe('/topic/content', (m) => alCambio(JSON.parse(m.body)));
  stomp.subscribe('/topic/clientes', (m) => mostrarClientes(JSON.parse(m.body)));
  stomp.subscribe('/topic/estadisticas', (m) => alEstadistica(JSON.parse(m.body)));
  fetch('api/clientes').then((r) => r.json()).then(mostrarClientes);
  cargar();                                            // al (re)conectar se sincroniza el estado
};
stomp.onWebSocketClose = () => {
  $('#ws-estado').textContent = 'WebSocket desconectado';
  $('#ws-estado').className = 'pill off';
};
stomp.activate();

const mostrarClientes = (d) => { $('#clientes').textContent = 'clientes conectados: ' + d.conectados; };

const verbos = { CREADO: 'registró', ACTUALIZADO: 'editó', ELIMINADO: 'eliminó' };

function alCambio(e) {
  const propio = e.cliente === clienteId;
  const quien = propio ? 'Este cliente' : (e.cliente || 'Otro cliente');
  const titulo = e.contenido ? `"${e.contenido.title}"` : '';
  const li = document.createElement('li');
  li.innerHTML = `<span class="tag ${esc(e.accion)}">${esc(e.accion)}</span>
    <span><b>${esc(quien)}</b> ${verbos[e.accion]} el contenido #${e.id} ${esc(titulo)}</span>
    <span class="nota">${new Date(e.timestamp).toLocaleTimeString('es')}</span>`;
  const lista = $('#eventos');
  lista.querySelector('.vacio')?.remove();
  lista.prepend(li);
  if (!propio) avisar(`${quien} ${verbos[e.accion]} #${e.id} ${titulo}`);
  if (e.accion !== 'ELIMINADO') resaltar.add(e.id);
  recargar();
}

let tAviso;
function avisar(texto) {
  const a = $('#aviso');
  a.textContent = '🔔 ' + texto;
  a.classList.add('visible');
  clearTimeout(tAviso);
  tAviso = setTimeout(() => a.classList.remove('visible'), 4000);
}

// =====================================================================
// Etapa 3: procesamiento con hilos. El servidor responde 202 al instante
// y el progreso/resultado llegan por WebSocket (/topic/estadisticas).
// =====================================================================
const fmt = (n) => Number(n).toLocaleString('es');
const seg = (ms) => (ms / 1000).toFixed(2) + ' s';
let sonda = null;

// Mientras se procesa, se consulta el CRUD cada 300 ms para medir si la app sigue respondiendo
function iniciarSonda() {
  const tiempos = [];
  detenerSonda();
  sonda = setInterval(async () => {
    const t0 = performance.now();
    await fetch('api/content?tamanio=5&pagina=' + Math.floor(Math.random() * 50));
    tiempos.push(performance.now() - t0);
    const prom = tiempos.reduce((a, b) => a + b, 0) / tiempos.length;
    $('#est-sonda').textContent = `CRUD durante el procesamiento: ${tiempos.length} consultas a /api/content · ` +
      `promedio ${prom.toFixed(1)} ms · máximo ${Math.max(...tiempos).toFixed(1)} ms (la aplicación sigue respondiendo)`;
  }, 300);
}
function detenerSonda() { clearInterval(sonda); sonda = null; }

function estado(texto, clase) {
  $('#est-estado').innerHTML = texto;
  $('#est-estado').className = 'est-estado ' + (clase || '');
}

$('#btn-hilos').onclick = async () => {
  const hilos = $('#hilos').value;
  $('#est-resultado').classList.add('oculto');
  iniciarSonda();
  const t0 = performance.now();
  const r = await fetch('api/estadisticas?hilos=' + hilos, { method: 'POST' });
  const ms = performance.now() - t0;
  const d = await r.json();
  if (!r.ok) { detenerSonda(); estado('⚠ ' + esc(d.detail || 'Error'), ''); return; }
  estado(`⏳ El servidor respondió <b>HTTP ${r.status}</b> en <b>${ms.toFixed(0)} ms</b> (atendido por <code>${esc(d.hiloPeticion)}</code>).
    El trabajo <code>${esc(d.jobId)}</code> se procesa en segundo plano con <b>${d.hilos} hilos</b>; el progreso llega por WebSocket.`, 'trabajando');
};

$('#btn-sin-hilos').onclick = async () => {
  $('#est-resultado').classList.add('oculto');
  $('#est-progreso').classList.add('oculto');
  $('#btn-sin-hilos').disabled = $('#btn-hilos').disabled = true;
  estado('⏳ Esperando la respuesta del servidor... (versión sin hilos: no hay progreso, la petición queda bloqueada hasta terminar)', 'trabajando');
  iniciarSonda();
  const t0 = performance.now();
  const r = await fetch('api/estadisticas/sin-hilos');
  const ms = performance.now() - t0;
  detenerSonda();
  $('#btn-sin-hilos').disabled = $('#btn-hilos').disabled = false;
  const res = await r.json();
  estado(`✔ Versión <b>sin hilos</b>: la petición HTTP estuvo bloqueada <b>${seg(ms)}</b> hasta recibir el resultado.
    Todo se calculó secuencialmente en <code>${esc(res.hiloCoordinador)}</code> · cálculo ${seg(res.msProcesamiento)}.`, 'ok');
  mostrarResultado(res);
};

function alEstadistica(m) {
  if (m.tipo === 'INICIADO') {
    $('#btn-hilos').disabled = $('#btn-sin-hilos').disabled = true;
    $('#est-progreso').classList.remove('oculto');
    $('#est-hilos').innerHTML = Array.from({ length: m.hilos }, (_, i) =>
      `<div class="hilo">trabajador ${i + 1} <span id="h-${i}">0%</span><div class="barra"><div id="hb-${i}"></div></div></div>`).join('');
    $('#est-barra').style.width = '0%';
  }
  if (m.tipo === 'PROGRESO') {
    $('#est-barra').style.width = m.porcentaje + '%';
    m.porHilo.forEach((p, i) => {
      const t = document.getElementById('h-' + i), b = document.getElementById('hb-' + i);
      if (t) { t.textContent = p + '%'; b.style.width = p + '%'; }
    });
    if (!$('#est-estado').innerHTML.includes('HTTP')) {
      estado(`⏳ Procesando en segundo plano (otro cliente lo inició): ${m.porcentaje}% · ${fmt(m.procesados)} de ${fmt(m.total)}`, 'trabajando');
    }
  }
  if (m.tipo === 'COMPLETADO') {
    detenerSonda();
    $('#btn-hilos').disabled = $('#btn-sin-hilos').disabled = false;
    const r = m.resultado;
    estado(`✔ Procesamiento <b>con ${r.hilos} hilos</b> terminado: cálculo en <b>${seg(r.msProcesamiento)}</b>
      (carga de datos ${seg(r.msCarga)}, total ${seg(r.msTotal)}) · coordinado por <code>${esc(r.hiloCoordinador)}</code>.`, 'ok');
    mostrarResultado(r);
  }
  if (m.tipo === 'ERROR') {
    detenerSonda();
    $('#btn-hilos').disabled = $('#btn-sin-hilos').disabled = false;
    estado('⚠ Error: ' + esc(m.mensaje));
  }
}

function barras(items, max, valor, etiqueta) {
  return items.map((it) => `<div class="fila-barra"><span title="${esc(etiqueta(it))}">${esc(etiqueta(it))}</span>
    <div class="b" style="width:${(100 * valor(it) / max).toFixed(1)}%"></div><span>${fmt(valor(it))}</span></div>`).join('');
}

function mostrarResultado(r) {
  $('#est-resultado').classList.remove('oculto');
  $('#r-total').textContent = fmt(r.totalContenidos);
  $('#r-peliculas').textContent = fmt(r.porTipo.MOVIE || 0);
  $('#r-series').textContent = fmt(r.porTipo.TV_SHOW || 0);
  $('#r-min').textContent = r.duracionPromedioPeliculasMin + ' min';
  $('#r-temp').textContent = r.temporadasPromedioSeries;
  $('#r-comp').textContent = fmt(r.comparaciones);
  $('#r-generos').innerHTML = barras(r.porGenero, r.porGenero[0]?.cantidad || 1, (g) => g.cantidad, (g) => g.nombre);
  const maxAnio = Math.max(1, ...r.porAnio.map((a) => a.peliculas + a.series));
  $('#r-anios').innerHTML = r.porAnio.map((a) => `<div class="fila-barra"><span>${a.anio}</span>
    <div class="doble"><div class="b" style="width:${(100 * a.peliculas / maxAnio).toFixed(1)}%" title="películas"></div>
    <div class="b s" style="width:${(100 * a.series / maxAnio).toFixed(1)}%" title="series"></div></div>
    <span>${a.peliculas}/${a.series}</span></div>`).join('') + '<p class="nota">azul: películas · ámbar: series</p>';
  $('#r-similares').innerHTML = r.similares.map((s) => `<tr><td class="titulo">#${s.idA} ${esc(s.tituloA)}</td>
    <td class="titulo">#${s.idB} ${esc(s.tituloB)}</td><td>${(s.similitud * 100).toFixed(1)}%</td><td>${s.elementosComunes}</td></tr>`).join('');
  $('#r-detalle').innerHTML = r.detalleHilos.map((h) => `<tr><td><code>${esc(h.hilo)}</code></td><td>${fmt(h.procesados)}</td>
    <td>${fmt(h.comparaciones)}</td><td>${seg(h.ms)}</td></tr>`).join('');
}

fetch('api/estadisticas/ultimo').then((r) => (r.status === 200 ? r.json() : null)).then((r) => { if (r) mostrarResultado(r); });
