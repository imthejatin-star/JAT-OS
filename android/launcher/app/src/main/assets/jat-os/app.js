const $ = id => document.getElementById(id);

function updateClock() {
  const now = new Date();
  $('clock').textContent = now.toLocaleTimeString([], {
    hour:'2-digit',minute:'2-digit',hour12:false
  });
  $('date').textContent = now.toLocaleDateString([], {
    weekday:'long',day:'numeric',month:'long',year:'numeric'
  });
}

updateClock();
setInterval(updateClock,1000);

function showToast(message) {
  const toast = $('toast');
  toast.textContent = message;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'),1800);
}

function navigate(screen) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  $(screen).classList.add('active');

  document.querySelectorAll('.nav-item').forEach(b => {
    b.classList.toggle('active',b.dataset.screen === screen);
  });

  if(screen === 'home' || screen === 'apps' || screen === 'settings') {
    document.querySelectorAll('.nav-item').forEach(b => {
      b.classList.toggle('active',b.dataset.screen === screen);
    });
  } else {
    document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
  }

  window.scrollTo({top:0,behavior:'smooth'});
}

document.querySelectorAll('.nav-item').forEach(button => {
  button.addEventListener('click',() => navigate(button.dataset.screen));
});

const appInfo = {
  Files:'Connect the future native file-management integration.',
  Notes:'Your personal notes workspace.',
  Browser:'Your Android browser integration.',
  Calculator:'A future native calculator integration.',
  Settings:'Configure your personal environment.',
  Terminal:'Connect your Termux development environment.'
};

function openApp(name) {
  if(name === 'Settings') return navigate('settings');
  if(name === 'Notes') return navigate('notes');

  $('detail-title').textContent = name;
  $('detail-name').textContent = name;
  $('detail-description').textContent = appInfo[name] || 'Application information';

  $('detail-action').innerHTML =
    '<div class="info-card"><div class="info-row"><span>Status</span><strong>Prototype</strong></div><div class="info-row"><span>Integration</span><strong>Not connected yet</strong></div></div>';

  navigate('detail');
}

document.querySelectorAll('[data-app]').forEach(button => {
  button.addEventListener('click',() => openApp(button.dataset.app));
});

$('back-button').addEventListener('click',() => navigate('apps'));

$('search').addEventListener('input',event => {
  const query = event.target.value.toLowerCase();
  document.querySelectorAll('.app-entry').forEach(item => {
    item.style.display = item.textContent.toLowerCase().includes(query) ? 'flex' : 'none';
  });
});

let light = localStorage.getItem('jat-theme') === 'light';

function applyTheme() {
  document.body.classList.toggle('light',light);
  $('theme-toggle').textContent = light ? 'ON' : 'OFF';
}

$('theme-toggle').addEventListener('click',() => {
  light = !light;
  localStorage.setItem('jat-theme',light ? 'light' : 'dark');
  applyTheme();
});

applyTheme();

$('notes-input').value = localStorage.getItem('jat-notes') || '';

$('notes-input').addEventListener('input',() => {
  localStorage.setItem('jat-notes',$('notes-input').value);
  $('save-status').textContent = 'Saved locally';
});

$('clear-notes').addEventListener('click',() => {
  if(!confirm('Clear your locally saved notes?')) return;
  $('notes-input').value = '';
  localStorage.removeItem('jat-notes');
  showToast('Notes cleared');
});
