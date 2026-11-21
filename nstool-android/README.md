# NSTool Android Beta 5

Android ARM64 wrapper around upstream NSTool. This release adds a separate NCA Program extraction flow, ticket (.tik) selection, title-key decoding using the user's own prod.keys, and ExeFS-only extraction (main + main.npdm).

## Key file handling
- Optional `prod.keys` is copied to app-private storage and passed with NSTool's `-k` option.
- Optional `title.keys` is copied to `$HOME/.switch/title.keys`.
- A selected ticket is inspected to obtain its Rights ID and encrypted title key. The app attempts the available title-key formats and reports the key revision needed.
- The app does not bundle, generate, or distribute game data or encryption keys. Use only key files obtained from your own console and legitimate setup.

## NSP and NCA processing
- NSP extraction unpacks the PFS0 container (certificates, tickets, NCA files and metadata).
- Extracting an NSP does not automatically decrypt the filesystems inside each NCA. Select the Program NCA separately for the NCA extraction step.
- ExeFS-only mode exports the main executable and main.npdm. Full extraction can export the detected NCA content.
- The app copies the selected input into private storage before processing, so allow enough free space for the source and extracted output.

## Build
GitHub Actions builds the NSTool CLI for Android ARM64, packages the matching C++ runtime, and runs Gradle `assembleDebug`. The workflow artifact is named `NSTool-Android-ARM64-Beta5`.
