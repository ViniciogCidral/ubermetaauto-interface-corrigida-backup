const state = { paused: false, started: false, decision: null, offer: 0, overlayClosed: false, shiftPlanned: false, permissionsReady: false, shift: null };
const decisions = {
  take: { label: 'PEGAR', text: 'Meta atingida · R$ 84,30/h', notification: 'PEGAR · R$ 18,70 · 31 min' },
  maybe: { label: 'TALVEZ', text: 'Perto da meta · R$ 63,10/h', notification: 'TALVEZ · R$ 18,70 · 31 min' },
  pass: { label: 'PASSAR', text: 'Abaixo da meta · R$ 36,19/h', notification: 'PASSAR · R$ 18,70 · 31 min' }
};
const preview = document.querySelector('#decisionPreview');
const overlay = document.querySelector('#overlayDemo');
const overlayTitle = document.querySelector('#overlayTitle');
const overlayText = document.querySelector('#overlayText');
const notification = document.querySelector('#notificationDemo');
const notificationText = document.querySelector('#notificationText');

function setDecision(type) {
  const item = decisions[type];
  state.decision = type;
  [preview, overlay, notification].forEach(el => el.classList.remove('take','maybe','pass'));
  [preview, overlay, notification].forEach(el => el.classList.add(type));
  minimizedOverlay.classList.remove('take', 'maybe', 'pass');
  minimizedOverlay.classList.add(type);
  preview.innerHTML = `<span>${item.text}</span><b>${item.label}</b>`;
  overlayTitle.textContent = item.label;
  overlayText.textContent = 'R$ 18,70 · 31 min · 10,8 km · 4,95 ★';
  notificationText.textContent = item.notification;
}
document.querySelectorAll('[data-decision]').forEach(button => button.addEventListener('click', () => setDecision(button.dataset.decision)));

function switchScreen(name) {
  document.querySelectorAll('.screen').forEach(screen => screen.classList.toggle('active', screen.id === name));
  document.querySelectorAll('[data-screen]').forEach(button => button.classList.toggle('active', button.dataset.screen === name));
}
document.querySelectorAll('[data-screen]').forEach(button => button.addEventListener('click', () => switchScreen(button.dataset.screen)));

const startButton = document.querySelector('#startButton');
const startMessage = document.querySelector('#startMessage');
function startOrResume() {
  if (!state.started) {
    if (!state.shiftPlanned) {
      startMessage.textContent = 'Antes de iniciar: planeje seu turno em ☰ › PLANEJAR TURNO.';
      document.querySelector('#cycleMessage').textContent = 'faltou planejar o turno';
      return;
    }
    if (!state.permissionsReady) {
      startMessage.textContent = 'Antes de iniciar: confirme as permissões em ☰ › PERMISSÕES.';
      document.querySelector('#cycleMessage').textContent = 'faltou confirmar permissões';
      return;
    }
    state.started = true;
    state.overlayClosed = false;
    document.querySelector('#cycleLabel').innerHTML = 'CICLO 1 · AGUARDANDO PRIMEIRA CORRIDA';
    document.querySelector('#revenue').textContent = '0,00';
    document.querySelector('#cycleMessage').textContent = 'captura ativa · esperando corrida aceita';
    document.querySelector('#timer').textContent = '--:--';
    document.querySelector('#progressFill').style.width = '0%';
    document.querySelector('#rideActions').classList.add('visible');
    restoreTag();
    setPaused(false);
    startMessage.textContent = 'Tudo pronto. O ciclo inicia automaticamente na primeira corrida aceita.';
    return;
  }
  if (state.paused || state.overlayClosed) {
    state.overlayClosed = false;
    restoreTag();
    setPaused(false);
    startMessage.textContent = 'Turno retomado. Overlay reaberto.';
  } else {
    setPaused(true);
    startMessage.textContent = 'Turno pausado manualmente. Toque em Retomar para continuar.';
  }
}
startButton.addEventListener('click', startOrResume);

const offers = [
  ['R$ 18,70','31 min','10,8 km','36,19'],
  ['R$ 29,40','27 min','8,2 km','65,33'],
  ['R$ 41,90','32 min','12,5 km','78,56']
];
document.querySelector('#newOfferButton').addEventListener('click', () => {
  state.offer = (state.offer + 1) % offers.length;
  const [fare,time,km,hour] = offers[state.offer];
  preview.className = 'decision-preview'; preview.innerHTML = '<span>AGUARDANDO ANÁLISE</span><b>?</b>';
  overlay.className = 'overlay-demo'; overlayTitle.textContent = 'AGUARDANDO'; overlayText.textContent = 'aguardando oferta';
  notification.className = 'notification-demo dotted-shadow'; notificationText.textContent = 'R$ 25,00 | 40:00 | C12';
});
document.querySelectorAll('.filter').forEach(filter => filter.addEventListener('click', () => { document.querySelectorAll('.filter').forEach(f => f.classList.remove('selected')); filter.classList.add('selected'); }));
document.querySelectorAll('.period').forEach(period => period.addEventListener('click', () => {
  document.querySelectorAll('.period').forEach(item => item.classList.remove('selected'));
  period.classList.add('selected');
  document.querySelector('#periodLabel').textContent = period.dataset.period;
}));
document.querySelector('#customPeriodButton').addEventListener('click', () => {
  document.querySelector('#periodModal').classList.add('open');
});

const minimizedOverlay = document.querySelector('#minimizedOverlay');
const minimizeOverlay = document.querySelector('#minimizeOverlay');
function minimizeTag() {
  minimizedOverlay.style.top = `${overlay.offsetTop}px`;
  minimizedOverlay.style.left = `${Math.max(0, overlay.offsetLeft)}px`;
  minimizedOverlay.style.right = 'auto';
  overlay.classList.add('minimized');
  minimizedOverlay.className = `minimized-overlay ${state.decision || ''}`;
  minimizedOverlay.classList.add('visible');
}
function restoreTag() {
  overlay.classList.remove('minimized');
  minimizedOverlay.classList.remove('visible');
}
minimizeOverlay.addEventListener('click', event => { event.stopPropagation(); minimizeTag(); });

let drag = null;
overlay.addEventListener('pointerdown', event => {
  if (event.target.closest('button')) return;
  const phone = document.querySelector('.phone');
  if (overlay.classList.contains('docked')) {
    const dockedRight = overlay.offsetLeft + overlay.offsetWidth;
    overlay.classList.remove('docked');
    overlay.style.right = 'auto';
    overlay.style.left = `${Math.min(phone.clientWidth - overlay.offsetWidth, dockedRight - overlay.offsetWidth)}px`;
  }
  drag = { x: event.clientX, y: event.clientY, left: overlay.offsetLeft, top: overlay.offsetTop };
  overlay.setPointerCapture(event.pointerId);
});
overlay.addEventListener('pointermove', event => {
  if (!drag) return;
  const phone = document.querySelector('.phone');
  const left = Math.max(0, Math.min(phone.clientWidth - overlay.offsetWidth, drag.left + event.clientX - drag.x));
  const top = Math.max(65, Math.min(phone.clientHeight - 110, drag.top + event.clientY - drag.y));
  overlay.style.left = `${left}px`; overlay.style.top = `${top}px`; overlay.style.right = 'auto'; overlay.style.transform = 'none';
});
overlay.addEventListener('pointerup', () => {
  if (!drag) return;
  const phone = document.querySelector('.phone');
  const nearRightEdge =
    overlay.offsetLeft + overlay.offsetWidth >= phone.clientWidth - 4;
  if (nearRightEdge) {
    overlay.classList.add('docked');
    overlay.style.left = 'auto';
    /* Mantém a tag compacta inteiramente dentro da área visível. */
    overlay.style.right = '0px';
    overlay.style.transform = 'none';
  }
  drag = null;
});
overlay.addEventListener('pointercancel', () => { drag = null; });

let minimizedDrag = null;
let minimizedMoved = false;
minimizedOverlay.addEventListener('pointerdown', event => {
  minimizedDrag = { x: event.clientX, y: event.clientY, left: minimizedOverlay.offsetLeft, top: minimizedOverlay.offsetTop };
  minimizedMoved = false;
  minimizedOverlay.setPointerCapture(event.pointerId);
});
minimizedOverlay.addEventListener('pointermove', event => {
  if (!minimizedDrag) return;
  const phone = document.querySelector('.phone');
  const left = Math.max(0, Math.min(phone.clientWidth - minimizedOverlay.offsetWidth, minimizedDrag.left + event.clientX - minimizedDrag.x));
  const top = Math.max(58, Math.min(phone.clientHeight - 64, minimizedDrag.top + event.clientY - minimizedDrag.y));
  minimizedOverlay.style.left = `${left}px`; minimizedOverlay.style.top = `${top}px`; minimizedOverlay.style.right = 'auto';
  minimizedMoved = true;
});
minimizedOverlay.addEventListener('pointerup', () => {
  if (!minimizedMoved) restoreTag();
  minimizedDrag = null;
});

function setPaused(paused) {
  state.paused = paused;
  startButton.innerHTML = paused ? '▶ <span>RETOMAR</span>' : 'Ⅱ <span>PAUSAR</span>';
  document.querySelector('.live-dot').style.animationPlayState = paused ? 'paused' : 'running';
}
document.querySelector('#closeOverlay').addEventListener('click', event => {
  event.stopPropagation();
  overlay.classList.add('minimized');
  minimizedOverlay.classList.remove('visible');
  state.overlayClosed = true;
  setPaused(true);
  startMessage.textContent = 'Overlay fechado. O turno foi pausado; toque em Retomar para reabrir.';
});

const menuPanel = document.querySelector('#menuPanel');
document.querySelector('#menuButton').addEventListener('click', () => menuPanel.classList.add('open'));
document.querySelector('#closeMenu').addEventListener('click', () => menuPanel.classList.remove('open'));
document.querySelectorAll('[data-modal]').forEach(button => button.addEventListener('click', () => {
  menuPanel.classList.remove('open');
  document.querySelector(`#${button.dataset.modal}`).classList.add('open');
}));
document.querySelectorAll('.modal-close, .modal-save').forEach(button => button.addEventListener('click', event => {
  event.currentTarget.closest('.modal').classList.remove('open');
}));
document.querySelector('#saveShift').addEventListener('click', () => {
  const inputs = document.querySelectorAll('#shiftModal input');
  const start = inputs[0].value;
  const end = inputs[1].value;
  const breakStart = inputs[2].value;
  const breakEnd = inputs[3].value;
  const toMinutes = value => {
    const [hour, minute] = value.split(':').map(Number);
    return hour * 60 + minute;
  };
  const startMinutes = toMinutes(start);
  const endMinutes = toMinutes(end);
  const breakStartMinutes = toMinutes(breakStart);
  const breakEndMinutes = toMinutes(breakEnd);
  let minutes = endMinutes - startMinutes;
  if (minutes <= 0) minutes += 24 * 60;
  let pauseMinutes = breakEndMinutes - breakStartMinutes;
  if (pauseMinutes < 0) pauseMinutes += 24 * 60;
  const pauseOffset = (breakStartMinutes - startMinutes + 24 * 60) % (24 * 60);
  const pauseFitsShift = pauseMinutes > 0 && pauseOffset < minutes && pauseOffset + pauseMinutes <= minutes;
  const workingMinutes = minutes - (pauseFitsShift ? pauseMinutes : 0);
  const cycles = Math.ceil(workingMinutes / 60);
  state.shiftPlanned = true;
  state.shift = { start, end, breakStart, breakEnd, cycles, workingMinutes };
  document.querySelector('#shiftPlan').innerHTML = `PLANEJADO HOJE · ${start}–${end} · ${cycles} CICLOS<br><span>pausa ${breakStart}–${breakEnd} · trabalhado 00:00</span>`;
  document.querySelector('#notificationText').textContent = `Turno planejado · ${start}–${end}`;
  startMessage.textContent = 'Turno planejado. Agora confirme as permissões para iniciar.';
});
document.querySelector('#grantPermissions').addEventListener('click', () => {
  state.permissionsReady = true;
  const item = document.querySelector('#capturePermission');
  item.classList.remove('pending');
  item.innerHTML = '<b>✓</b><span>captura de tela<br><small>pronta para iniciar</small></span>';
  startMessage.textContent = state.shiftPlanned ? 'Permissões prontas. Toque em Iniciar.' : 'Permissões prontas. Falta planejar o turno.';
});
document.querySelector('#applyCustomPeriod').addEventListener('click', () => {
  const start = document.querySelector('#periodStart').value;
  const end = document.querySelector('#periodEnd').value;
  if (!start || !end) return;
  const format = value => value.split('-').reverse().join('/');
  document.querySelector('#periodLabel').textContent = `${format(start)}–${format(end)}`;
  document.querySelectorAll('.period').forEach(item => item.classList.remove('selected'));
});

document.querySelector('#acceptRide').addEventListener('click', () => {
  document.querySelector('#cycleLabel').innerHTML = 'CICLO 1 <span class="live-dot"></span> CORRIDA EM ANDAMENTO';
  document.querySelector('#cycleMessage').textContent = 'corrida aceita · tempo de trabalho contando';
  document.querySelector('#timer').textContent = '60:00';
  overlayTitle.textContent = 'EM CORRIDA';
  overlayText.textContent = 'R$ 18,70 · 31 min · 10,8 km · 4,95 ★';
  startMessage.textContent = 'Oferta aceita. O ciclo foi iniciado e o tempo de trabalho está contando.';
});
document.querySelector('#rejectRide').addEventListener('click', () => {
  document.querySelector('#cycleLabel').textContent = 'CICLO 1 · AGUARDANDO PRÓXIMA OFERTA';
  document.querySelector('#cycleMessage').textContent = 'oferta recusada pelo botão ×';
  overlayTitle.textContent = 'RECUSADA';
  overlayText.textContent = 'procurando nova oferta';
  startMessage.textContent = 'Oferta recusada. Nenhum valor foi adicionado ao ciclo.';
});
document.querySelector('#expireRide').addEventListener('click', () => {
  document.querySelector('#cycleLabel').textContent = 'CICLO 1 · AGUARDANDO PRÓXIMA OFERTA';
  document.querySelector('#cycleMessage').textContent = 'oferta expirou sem decisão';
  overlayTitle.textContent = 'EXPIRADA';
  overlayText.textContent = 'procurando nova oferta';
  startMessage.textContent = 'A oferta expirou. Nenhum valor foi adicionado ao ciclo.';
});
