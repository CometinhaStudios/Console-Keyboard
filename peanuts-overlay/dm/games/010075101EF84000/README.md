# Stray (010075101EF84000) — provisional Peanuts Native profile

This folder adds Stray's base title ID and the verified module build IDs from the user's own dump report so the standard Peanuts Native source can recognize the title as a profile target.

## Verified from the dump report
- Base CNMT version: 0
- Program title ID: 010075101EF84000
- NPDM name: Hk_project; 64-bit
- Modules: main, subsdk0, rtld, sdk
- RomFS marker: Hk_project/Content/Paks/Hk_project-Switch.pak
- UE4 project: Hk_project

The profile's `build` row uses the first 16 bytes of each 20-byte NSO Build ID, matching the 32-hex build-ID format used by Peanuts profiles.

## Not yet supported / not validated
- No `shader_map.tsv`, `shader_facts.tsv`, `shader_pairs.tsv` or `pso_pairs.tsv` are included. These must be generated from the user's local dump and the real Unreal shader archives; do not create placeholder shader rows.
- Stray's shaders are expected in the Unreal PAK. The official Peanuts Native NEW-GAME guide says Unreal shader archives may require the game's own Oodle decoding or the on-device UE shader path. Whether this exact Stray PAK is readable by the current decoder has not been tested.
- No memory layout, FPS, rendering or controller settings have been guessed.
- This profile does not claim that Stray boots or is playable.

## Data handling
No NSP/NCA/NSO/PAK, ticket, keys, firmware, extracted game code, pack or shader library is stored in this repository. Keep all game data on the user's device.
