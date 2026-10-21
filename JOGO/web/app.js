const lagButton = document.querySelector('#lagButton');
const buttonLabel = document.querySelector('#buttonLabel');
const latencyRange = document.querySelector('#latencyRange');
const latencyValue = document.querySelector('#latencyValue');
const metricValue = document.querySelector('#metricValue');
const statePill = document.querySelector('#statePill');
const stateLabel = document.querySelector('#stateLabel');
const statusVisual = document.querySelector('#statusVisual');
const statusTitle = document.querySelector('#statusTitle');
const statusDescription = document.querySelector('#statusDescription');
const jitterToggle = document.querySelector('#jitterToggle');
const lossToggle = document.querySelector('#lossToggle');
const installButton = document.querySelector('#installButton');
const overlayToggle = document.querySelector('#overlayToggle');
const overlayDescription = document.querySelector('#overlayDescription');
const installGuide = document.querySelector('#installGuide');
const installGuideText = document.querySelector('#installGuideText');
const desktopBridge = window.lagLabDesktop;
const mobileBridge = window.lagLabMobile;

let isActive = false;
let installPrompt;
let latencyTimer;

function updateLatency() {
  const latency = Number(latencyRange.value);
  const displayedLatency = isActive && jitterToggle.checked
    ? Math.max(0, latency + Math.round((Math.random() - 0.5) * latency * 0.3))
    : latency;
  latencyValue.innerHTML = `${latency} <small>ms</small>`;
  document.body.style.setProperty('--simulated-lag', `${Math.max(400, displayedLatency)}ms`);

  if (isActive) {
    metricValue.innerHTML = `${displayedLatency} <small>ms</small>`;
  }
}

function setActive(active) {
  isActive = active;
  document.body.classList.toggle('is-lagging', active);
  document.body.classList.toggle('loss-enabled', active && lossToggle.checked);
  lagButton.classList.toggle('active', active);
  lagButton.setAttribute('aria-pressed', String(active));
  buttonLabel.textContent = active ? 'DESATIVAR LAG' : 'ATIVAR LAG';
  statePill.classList.toggle('active', active);
  stateLabel.textContent = active ? 'ATIVO' : 'DESLIGADO';
  statusVisual.classList.toggle('active', active);
  statusTitle.textContent = active ? 'Lag simulado ativo' : 'Tudo estável';
  statusDescription.textContent = active
    ? 'Efeito visual ativo nesta demonstração.'
    : 'Ative o botão para iniciar a demonstração visual.';
  metricValue.innerHTML = active ? `${latencyRange.value} <small>ms</small>` : '— <small>ms</small>';
  desktopBridge?.setLagState(active);
  mobileBridge?.setLagState(active);

  if (active) {
    updateLatency();
    latencyTimer = window.setInterval(updateLatency, 900);
  } else {
    window.clearInterval(latencyTimer);
  }
}

lagButton.addEventListener('click', () => setActive(!isActive));
latencyRange.addEventListener('input', updateLatency);
jitterToggle.addEventListener('change', updateLatency);
lossToggle.addEventListener('change', () => {
  document.body.classList.toggle('loss-enabled', isActive && lossToggle.checked);
});

if (desktopBridge) {
  overlayToggle.addEventListener('change', async () => {
    try {
      const enabled = await desktopBridge.setOverlayEnabled(overlayToggle.checked);
      overlayToggle.checked = enabled;
      overlayDescription.textContent = enabled
        ? 'Ativo: botão sempre no topo do Windows'
        : 'Disponível nos apps Windows e Android';
    } catch (error) {
      overlayToggle.checked = false;
      overlayDescription.textContent = 'Não foi possível abrir o botão flutuante';
      console.error('Não foi possível alterar o botão flutuante:', error);
    }
  });

  desktopBridge.onOverlayState((enabled) => {
    overlayToggle.checked = enabled;
    overlayDescription.textContent = enabled
      ? 'Ativo: botão sempre no topo do Windows'
      : 'Disponível nos apps Windows e Android';
  });

  desktopBridge.onToggleLag(() => setActive(!isActive));
  desktopBridge.onLagState(setActive);
} else if (mobileBridge) {
  overlayToggle.addEventListener('change', () => {
    mobileBridge.setOverlayEnabled(overlayToggle.checked);
    overlayDescription.textContent = overlayToggle.checked
      ? 'Solicitando permissão de sobreposição…'
      : 'Disponível na versão Android';
  });

  window.addEventListener('lgl-overlay-state', (event) => {
    overlayToggle.checked = event.detail;
    overlayDescription.textContent = event.detail
      ? 'Ativo: botão sempre no topo'
      : 'Permita a sobreposição nas configurações do Android';
  });
  window.addEventListener('lgl-toggle-lag', () => setActive(!isActive));
  window.addEventListener('lgl-lag-state', (event) => setActive(event.detail));
  mobileBridge.requestCurrentState();
} else {
  overlayToggle.disabled = true;
}

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  installPrompt = event;
  installButton.textContent = 'Instalar app';
});

installButton.addEventListener('click', async () => {
  if (!installPrompt) {
    const userAgent = navigator.userAgent;
    const isIOS = /iPhone|iPad|iPod/i.test(userAgent)
      || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

    if (isIOS) {
      installGuideText.textContent = 'No Safari, toque em Compartilhar e escolha “Adicionar à Tela de Início”. O iOS não permite botão flutuante sobre outros apps.';
    } else if (/Android/i.test(userAgent)) {
      installGuideText.textContent = 'No menu do navegador, escolha “Instalar app” ou “Adicionar à tela inicial”. Para sobreposição, instale o APK Android.';
    } else {
      installGuideText.textContent = 'No Edge ou Chrome, use o ícone Instalar na barra de endereço ou abra o menu do navegador e escolha “Instalar LGL 3.0”. A instalação da PWA requer HTTPS.';
    }

    installGuide.showModal();
    return;
  }

  const promptEvent = installPrompt;
  installPrompt = null;
  installButton.disabled = true;
  try {
    await promptEvent.prompt();
    await promptEvent.userChoice;
  } catch (error) {
    console.error('Não foi possível instalar a PWA:', error);
    installGuideText.textContent = 'O navegador não conseguiu iniciar a instalação. Abra o menu do navegador e tente “Instalar LGL 3.0” novamente.';
    installGuide.showModal();
  } finally {
    installButton.disabled = false;
  }
});

window.addEventListener('appinstalled', () => {
  installButton.textContent = 'App instalado';
  installButton.disabled = true;
});

if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch((error) => {
      console.error('Não foi possível registrar o service worker:', error);
    });
  });
}
