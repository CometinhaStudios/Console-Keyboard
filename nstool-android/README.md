# NSTool Android Beta 6

Android ARM64 wrapper around upstream NSTool. It does not bundle game data or encryption keys.

- `prod.keys`: copied to app-private storage and passed with `-k`.
- `title.keys`: copied to `$HOME/.switch/title.keys`. The process receives an explicit `HOME`.
- `LD_LIBRARY_PATH`: includes Android's app native library directory for `libc++_shared.so`.

Extracting an NSP unpacks the PFS0 container (NCA files, certificates, tickets and metadata). It does not automatically decrypt and extract filesystems inside each NCA. NCA processing is separate and requires the appropriate owner-supplied keys/ticket data.

The app copies the NSP into private storage before processing, so allow enough free storage for the source and extracted output.

## Beta 6
Corrige a leitura do tipo de assinatura do arquivo .tik (ordem big-endian) e adiciona fallback baseado no tamanho do ticket quando o deslocamento padrão não corresponde ao arquivo. Mantém o fluxo de extração do ExeFS introduzido na Beta 5.
