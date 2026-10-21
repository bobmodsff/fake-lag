const { contextBridge, ipcRenderer } = require('electron');

function subscribe(channel, callback) {
  if (typeof callback !== 'function') {
    throw new TypeError('O callback do evento precisa ser uma função.');
  }
  const listener = (_event, value) => callback(value);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

contextBridge.exposeInMainWorld('lagLabDesktop', {
  setOverlayEnabled: (enabled) => ipcRenderer.invoke('overlay:set-enabled', enabled),
  toggleLag: () => ipcRenderer.send('lag:toggle'),
  setLagState: (active) => ipcRenderer.send('lag:state', active),
  closeOverlay: () => ipcRenderer.send('overlay:close'),
  onOverlayState: (callback) => subscribe('overlay:state', callback),
  onToggleLag: (callback) => subscribe('lag:toggle', callback),
  onLagState: (callback) => subscribe('lag:state', callback)
});
