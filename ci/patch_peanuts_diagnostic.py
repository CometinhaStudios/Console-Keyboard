#!/usr/bin/env python3
"""Apply the universal diagnostic-import patch to an upstream Peanuts Native checkout."""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "peanuts")

def patch(path, old, new, label):
    f = ROOT / path
    text = f.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit("{}: expected one match for {!r}, found {}".format(path, label, count))
    f.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("patched", path, label)

# The import-only flag travels through the same foreground service as ordinary builds.
patch(
    "android/app/src/main/java/dev/peanuts/build/BuildRequest.java",
    "    public boolean deleteOldFirst;\n",
    "    public boolean deleteOldFirst;\n    /** Import an unprofiled title into a .nup for diagnostics; do not prepare or launch it. */\n    public boolean importOnly;\n",
    "BuildRequest.importOnly",
)

patch(
    "android/app/src/main/java/dev/peanuts/build/BuildService.java",
    '            EXTRA_BUILD_DESTINATION = "build_destination";\n',
    '            EXTRA_BUILD_DESTINATION = "build_destination", EXTRA_IMPORT_ONLY = "import_only";\n',
    "service intent key",
)

patch(
    "android/app/src/main/java/dev/peanuts/build/BuildService.java",
    '.putExtra(EXTRA_GAME_DIR, r.gameDir == null ? null : r.gameDir.getPath()).putExtra(EXTRA_COMPANION, r.companionPackage).putExtra(EXTRA_BUILD_ANYWAY, buildAnyway);',
    '.putExtra(EXTRA_GAME_DIR, r.gameDir == null ? null : r.gameDir.getPath()).putExtra(EXTRA_COMPANION, r.companionPackage).putExtra(EXTRA_BUILD_ANYWAY, buildAnyway)\n                .putExtra(EXTRA_IMPORT_ONLY, r.importOnly);',
    "service intent value",
)

patch(
    "android/app/src/main/java/dev/peanuts/build/BuildService.java",
    "        r.deleteOldFirst = i.getBooleanExtra(EXTRA_DELETE_FIRST, false);\n        return r;\n",
    "        r.deleteOldFirst = i.getBooleanExtra(EXTRA_DELETE_FIRST, false);\n        r.importOnly = i.getBooleanExtra(EXTRA_IMPORT_ONLY, false);\n        return r;\n",
    "service request value",
)

# A diagnostic import ends after the .nup is complete. It never enters Prepare, shader capture,
# companion installation, or the game launch path.
service = ROOT / "android/app/src/main/java/dev/peanuts/build/BuildService.java"
src = service.read_text(encoding="utf-8")
start = '            File dump = stepImport(r);\n'
end = '            progress("done", 100, message);\n'
if src.count(start) != 1:
    raise SystemExit("BuildService.java: import start marker not unique")
i = src.index(start)
j = src.index(end, i) + len(end)
old_block = src[i + len(start):j]
new_block = (
    start
    + '            if (r.importOnly) {\n'
    + '                ok = true;\n'
    + '                message = name + " imported for analysis. Saved file: " + dump.getAbsolutePath()\n'
    + '                        + ". No game preparation or launch was attempted because this app has no profile for this title.";\n'
    + '                log("diagnostic import only; output: " + dump.getAbsolutePath());\n'
    + '                progress("done", 100, message);\n'
    + '            } else {\n'
    + old_block
    + '            }\n'
)
service.write_text(src[:i] + new_block + src[j:], encoding="utf-8")
print("patched BuildService.java import-only branch")

patch(
    "android/app/src/main/java/dev/peanuts/build/BuildService.java",
    '.setContentTitle(ok ? name + " is ready to play" : "Building " + name + " stopped")',
    '.setContentTitle(ok ? (message.contains("imported for analysis") ? name + " imported for analysis" : name + " is ready to play") : "Building " + name + " stopped")',
    "notification title",
)

patch(
    "android/app/src/main/java/dev/peanuts/ui/Library.java",
    '        if (!PeanutsPrefs.hasFonts(a)) return "Add your firmware first (Settings › Library), then build.";\n',
    '        if (!s.importOnly && !PeanutsPrefs.hasFonts(a)) return "Add your firmware first (Settings › Library), then build.";\n',
    "skip firmware gate for import-only",
)

patch(
    "android/app/src/main/java/dev/peanuts/ui/Library.java",
    "        r.replaceNup = s.replaceNup; r.deleteOldFirst = s.deleteOldFirst;   // Import again (GameSheet.importAgain)\n",
    "        r.replaceNup = s.replaceNup; r.deleteOldFirst = s.deleteOldFirst;   // Import again (GameSheet.importAgain)\n        r.importOnly = s.importOnly;\n",
    "pass import-only flag",
)

patch(
    "android/app/src/main/java/dev/peanuts/ui/GameSheet.java",
    "        public boolean buildAnyway;\n",
    "        public boolean buildAnyway;\n        /** Diagnostic-only import for a title without a profile in this APK. */\n        public boolean importOnly;\n",
    "GameSheet.State.importOnly",
)

patch(
    "android/app/src/main/java/dev/peanuts/ui/GameSheet.java",
    '        if (missing != null) { PUi.add(box, PUi.body(a, missing), 0); return; }\n',
    '        if (missing != null) { diagnosticRows(missing); return; }\n',
    "unprofiled game sheet",
)

diagnostic_method = r'''
    /**
     * An unprofiled title cannot be prepared or launched by this APK. Let the owner import
     * their own NSP/XCI into a .nup and share the text-only build log for external analysis.
     */
    private void diagnosticRows(String missing) {
        boolean building = lib.building(title.titleId);
        LibraryScanner.Source b = title.base;
        boolean baseGone = b != null && !b.file.exists();
        PUi.add(box, PUi.warnChip(a, "No game profile", "No game profile", missing), 0);
        PUi.add(box, PUi.body(a,
                "Diagnostic import only. This makes a .nup from your own game files and records the import log. It does not prepare shaders, install a game profile, or attempt to launch the game."), 10);
        PUi.add(box, PUi.body(a, "Title ID: " + title.titleId), 4);
        CharSequence baseLine = b == null ? "None found"
                : b.file.getName() + (b.version > 0 ? " · " + versionText(b.version) : "")
                + (b.isNup() ? " · existing .nup" : b.isDump() ? " · dump folder" : " · import needed")
                + (b.file.isFile() ? " · " + String.format(Locale.ROOT, "%.2f GB", b.file.length() / 1073741824.0) : "");
        PUi.add(box, new PUi.Setting(a, "📀", "Base", null).value(baseLine).view, 10);

        LibraryScanner.Source newest = title.updates.isEmpty() ? null : title.updates.get(0);
        CharSequence up = chosenUpdate == null ? (newest == null ? "None found" : newest.file.getName() + " · " + versionText(newest.version))
                : chosenUpdate.file.getName() + " · " + versionText(chosenUpdate.version);
        PUi.add(box, new PUi.Setting(a, "⬆️", "Update",
                v -> { if (!building) LibrarySettings.pickFile(a, REQ_UPDATE, "Pick the update (.nsp, .nsz, .xci or .xcz)"); })
                .value(up).chevron(a).view, 0);

        List<String> dlcNames = new ArrayList<>();
        for (LibraryScanner.Source d : title.dlcs) dlcNames.add(d.name.isEmpty() ? d.file.getName() : d.name);
        for (File f : extraDlc) if (!dlcNames.contains(f.getName())) dlcNames.add(f.getName());
        PUi.add(box, new PUi.Setting(a, "➕", "DLC",
                v -> { if (!building) LibrarySettings.pickFile(a, REQ_DLC, "Pick a DLC (.nsp or .nsz)"); })
                .value(dlcNames.isEmpty() ? "None found · Add…" : String.join(", ", dlcNames) + " · Add…").chevron(a).view, 0);

        boolean needKeys = !PeanutsPrefs.hasKeys(a);
        if (needKeys) {
            PUi.add(box, PUi.warnChip(a, "Keys needed", "Keys needed", "Add your own prod.keys in Settings › Library to import an encrypted NSP."), 12);
            PUi.add(box, PUi.button(a, "Add keys…", PUi.Kind.SOFT, Haptics.on(Haptics.Word.TICK, v -> LibrarySettings.pickKeys(a))), 4, PUi.wrap());
        }
        if (building) PUi.add(box, PUi.small(a, "Importing now. Progress is shown on the game's card."), 12);
        String chosenFolder = PeanutsPrefs.chosenBuildDir(a, title.titleId);
        String defaultFolder = PeanutsPrefs.buildDir(a);
        PUi.Setting destination = new PUi.Setting(a, "🗂️", "Import destination",
                Haptics.on(Haptics.Word.TICK, v -> pickBuildFolder()))
                .value(chosenFolder != null ? chosenFolder : defaultFolder != null ? PUi.joined("Default", defaultFolder)
                        : title.home != null ? "Game folder" : "Choose a folder in Settings › Library").chevron(a);
        destination.view.setEnabled(!building);
        PUi.add(box, destination.view, 12);
        renderSpaceLine();

        boolean hasDestination = title.home != null || chosenFolder != null || defaultFolder != null;
        PUi.Action import = PUi.action(a, R.drawable.ic_build, "Import for analysis", PUi.Kind.PRIMARY,
                Haptics.on(Haptics.Word.CLICK, v -> startDiagnosticImport()));
        import.setEnabled(!building && b != null && !baseGone && !needKeys && hasDestination);
        PUi.add(box, import, 12, PUi.fill());
        PUi.add(box, PUi.button(a, "Share diagnostic report", PUi.Kind.QUIET,
                Haptics.on(Haptics.Word.CLICK, v -> BuildLogUi.share(a, title.titleId, title.name))), 8, PUi.wrap());
    }

    private void startDiagnosticImport() {
        State s = state();
        s.importOnly = true;
        if (s.base == null || !s.base.exists()) {
            new AlertDialog.Builder(a).setMessage("Pick the game's base file first.").setPositiveButton("OK", null).show();
            return;
        }
        String why = lib.startBuild(s);
        if (why != null) {
            new AlertDialog.Builder(a).setTitle("Import for analysis").setMessage(why).setPositiveButton("OK", null).show();
            return;
        }
        if (lib.building(title.titleId)) dialog.dismiss();
    }

'''
patch(
    "android/app/src/main/java/dev/peanuts/ui/GameSheet.java",
    "    // ---- Import again (dev.peanuts.build.Reimport) ----\n",
    diagnostic_method + "    // ---- Import again (dev.peanuts.build.Reimport) ----\n",
    "diagnostic-only sheet controls",
)

patch(
    "android/app/src/main/java/dev/peanuts/ui/GameSheet.java",
    "    State state() {\n        State s = new State();\n        s.titleId = title.titleId; s.name = title.name;\n",
    "    State state() {\n        State s = new State();\n        s.titleId = title.titleId; s.name = title.name;\n        s.importOnly = title.status == LibraryScanner.Status.NO_PROFILE;\n",
    "set state import-only",
)

# Fail closed if this script is applied to an unexpected revision or if any hook was missed.
for p in [
    "android/app/src/main/java/dev/peanuts/build/BuildRequest.java",
    "android/app/src/main/java/dev/peanuts/build/BuildService.java",
    "android/app/src/main/java/dev/peanuts/ui/Library.java",
    "android/app/src/main/java/dev/peanuts/ui/GameSheet.java",
]:
    data = (ROOT / p).read_text(encoding="utf-8")
    if "importOnly" not in data:
        raise SystemExit("verification failed: {} has no importOnly hook".format(p))
print("Universal diagnostic import patch applied.")
