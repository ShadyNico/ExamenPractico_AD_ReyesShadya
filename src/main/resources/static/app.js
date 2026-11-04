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
