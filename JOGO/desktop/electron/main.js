const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { app, BrowserWindow, ipcMain, screen } = require('electron');

const WEB_DIR = path.resolve(__dirname, '..', '..', 'web');
let mainWindow;
let overlayWindow;
let lagActive = false;

function sendOverlayState() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('overlay:state', Boolean(overlayWindow));
  }
}

function createOverlay() {
  if (overlayWindow && !overlayWindow.isDestroyed()) return;

  const display = screen.getDisplayMatching(mainWindow.getBounds());
  const { x, y, width, height } = display.workArea;

  overlayWindow = new BrowserWindow({
    width: 206,
    height: 78,
    x: x + width - 230,
    y: y + height - 104,
    frame: false,
    transparent: true,
    resizable: false,
    movable: true,
    focusable: true,
    skipTaskbar: true,
    alwaysOnTop: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  overlayWindow.setAlwaysOnTop(true, 'floating');
  overlayWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  overlayWindow.loadFile(path.join(__dirname, 'overlay.html'));
  overlayWindow.webContents.on('did-finish-load', () => {
    if (overlayWindow && !overlayWindow.isDestroyed()) {
      overlayWindow.webContents.send('lag:state', lagActive);
    }
  });
  overlayWindow.on('closed', () => {
    overlayWindow = null;
    sendOverlayState();
  });
  sendOverlayState();
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1120,
    height: 820,
    minWidth: 360,
    minHeight: 560,
    backgroundColor: '#090b12',
    title: 'LGL 3.0 — painel visual',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.loadFile(path.join(WEB_DIR, 'index.html'));
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.webContents.on('will-navigate', (event, destination) => {
    const appPage = pathToFileURL(path.join(WEB_DIR, 'index.html')).href;
    if (destination !== appPage) event.preventDefault();
  });
  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow.webContents.send('overlay:state', Boolean(overlayWindow));
    mainWindow.webContents.send('lag:state', lagActive);
  });
  mainWindow.on('closed', () => {
    if (overlayWindow && !overlayWindow.isDestroyed()) overlayWindow.close();
    mainWindow = null;
  });
}

ipcMain.handle('overlay:set-enabled', (event, enabled) => {
  if (!mainWindow || event.sender !== mainWindow.webContents) {
    throw new Error('Somente a janela principal pode alterar o botão flutuante.');
  }
  if (typeof enabled !== 'boolean') {
    throw new TypeError('O estado do botão flutuante precisa ser booleano.');
  }

  if (enabled) {
    createOverlay();
  } else if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.close();
  }

  return enabled && Boolean(overlayWindow);
});

ipcMain.on('lag:toggle', (event) => {
  if (!overlayWindow || event.sender !== overlayWindow.webContents) return;
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('lag:toggle');
  }
});

ipcMain.on('lag:state', (event, active) => {
  if (event.sender !== mainWindow?.webContents || typeof active !== 'boolean') return;
  lagActive = active;
  if (overlayWindow && !overlayWindow.isDestroyed()) {
    overlayWindow.webContents.send('lag:state', lagActive);
  }
});

ipcMain.on('overlay:close', (event) => {
  if (overlayWindow && event.sender === overlayWindow.webContents) overlayWindow.close();
});

app.whenReady().then(() => {
  createMainWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
