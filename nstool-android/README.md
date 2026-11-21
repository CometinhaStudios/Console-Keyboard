# NSTool Android Beta 7

Android ARM64 wrapper around upstream NSTool. It does not bundle game data or encryption keys.

- `prod.keys`: copied to app-private storage and passed with `-k`.
- `title.keys`: copied to `$HOME/.switch/title.keys`. The process receives an explicit `HOME`.
- `LD_LIBRARY_PATH`: includes Android's app native library directory for `libc++_shared.so`.

Extracting an NSP unpacks the PFS0 container (NCA files, certificates, tickets and metadata). It does not automatically decrypt and extract filesystems inside each NCA. NCA processing is separate and requires the appropriate owner-supplied keys/ticket data.

The app copies the NSP/NCA into private storage before processing, so allow enough free storage for the source and extracted output.

## Beta 7
- Procura o RightsID varrendo o arquivo `.tik`, sem depender exclusivamente do tipo de assinatura.
- Tenta as revisões de `titlekek_XX` derivadas da geração e do campo do ticket.
- Registra as tentativas de title key e inclui a exceção no log de diagnóstico.
- Mantém o fluxo de extração do ExeFS/NCA.

O workflow compila o NSTool para ARM64 e empacota o runtime C++ correspondente. O ZIP do projeto não inclui os binários nativos gerados na compilação.
