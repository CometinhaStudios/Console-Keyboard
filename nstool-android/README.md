# NSTool Android

Android ARM64 wrapper around the upstream NSTool CLI. Uses the upstream MIT-licensed source and does not bundle game data or keys.

The first build is a technical preview. It selects an NSP through Android's document picker, copies it to app-private storage, then runs NSTool for verbose metadata/layout/tree output or filesystem extraction. This currently requires free storage for a second copy of the NSP. Key-file integration and extraction verification remain follow-up work.
