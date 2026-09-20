# Console Keyboard v0.1.0

Primeiro protótipo do teclado Android:

- Retrato: teclado normal escuro inspirado na organização do Samsung Keyboard.
- Paisagem: teclado estilo console.
- Toque na tela continua funcionando no modo paisagem.
- D-pad navega pelas teclas.
- Botão de confirmar seleciona a tecla.
- Detecção automática de controles Xbox e PlayStation por vendor/name.
- Ícones/legendas do modo console mudam conforme a família do controle.
- Hot-plug: conectar/desconectar controle atualiza o tema sem reiniciar o app.
- Tela inicial com atalhos para ativar e escolher o IME.

## Controles atuais

Xbox: A confirmar, B fechar teclado, X apagar, Y espaço, RT concluir.
PlayStation: ✕ confirmar, ○ fechar teclado, □ apagar, △ espaço, R2 concluir.
D-pad: navegação.

## Build

Abrir o projeto no Android Studio (JDK 17+), deixar o Android Studio instalar o Android SDK 35/Build Tools e gerar `assembleDebug`.

Package: `com.consolekey.android`
Min SDK: 26
Target SDK: 35

## Limitações da v0.1.0

- Autocorreção/sugestões ainda não implementadas.
- Emoji/clipboard completos ainda não implementados; a toolbar é protótipo visual.
- Analógico ainda não está mapeado (D-pad já funciona).
- Compatibilidade de gamepad depende de como cada fabricante anuncia o controle ao Android; há fallback genérico.
