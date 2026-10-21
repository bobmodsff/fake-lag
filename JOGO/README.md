# LGL 3.0

O painel oferece uma simulação visual local. O controle de atraso, jitter e falhas muda as animações e os indicadores do próprio painel; ele não atrasa o tráfego de rede nem altera outros aplicativos.

## Instalação por plataforma

### PC: instalador Windows

1. Instale Node.js LTS.
2. Abra um terminal nesta pasta e execute `npm install`.
3. Execute `npm run build:windows`.
4. O instalador `.exe` será criado em `release/`.

Também é possível instalar a PWA no Edge ou Chrome quando `web/` estiver publicado por HTTPS: abra o site e selecione **Instalar aplicativo** na barra do navegador.

### Android: APK

1. Instale Android Studio, Android SDK 35 e JDK 17.
2. Abra a pasta `android/` no Android Studio e sincronize o projeto.
3. Selecione **Build > Build APK(s)**.
4. O APK de depuração fica em `android/app/build/outputs/apk/debug/app-debug.apk`.

O build copia automaticamente os arquivos de `web/` para o app. A permissão `SYSTEM_ALERT_WINDOW` é solicitada somente quando a pessoa ativa o botão flutuante; o Android abre a tela oficial de permissão e a pessoa pode aceitar ou recusar. A instalação do APK pode pedir confirmação do Android para instalar apps de fora da Play Store.

### iPhone e iPad: PWA

O iOS não permite que um site mostre um botão sempre no topo de outros apps. O painel visual pode ser instalado como PWA:

1. Publique o conteúdo de `web/` em um endereço HTTPS.
2. Abra o endereço no Safari.
3. Toque em **Compartilhar > Adicionar à Tela de Início**.

A versão PWA também pode ser instalada no Android e em navegadores compatíveis de desktop. A opção de botão sobre outros apps existe apenas nos apps nativos Windows e Android.

## Estrutura

- `web/`: interface, PWA, ícones, imagem e service worker.
- `desktop/electron/`: app Windows e overlay do desktop.
- `android/`: projeto Android, manifesto, ícones e serviço de overlay.
- `release/`: instaladores desktop gerados pelo build (criada ao compilar).
