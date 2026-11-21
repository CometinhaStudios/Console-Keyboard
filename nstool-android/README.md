# NSTool Android Beta 4

Android ARM64 wrapper around upstream NSTool. It does not bundle game data or encryption keys.

## Key file handling
- Optional `prod.keys` is copied to app-private storage and passed with NSTool's `-k` option.
- Optional `title.keys` is copied to `$HOME/.switch/title.keys`. The child NSTool process receives an explicit `HOME` pointing to the app-private NSTool home directory, which is how upstream NSTool discovers this file.
- Provide only key files obtained from your own console/legitimate setup. The app does not generate or distribute keys.

## NSP extraction
Extracting an NSP with NSTool unpacks the PFS0 container (certificates, tickets, NCA files and metadata). It does not automatically decrypt and extract the filesystems inside each NCA. NCA processing is a separate step and requires the appropriate keys/ticket data.

The app copies the NSP into private storage before processing, so allow enough free space for the source and extracted output.
