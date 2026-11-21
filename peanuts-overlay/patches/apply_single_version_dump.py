#!/usr/bin/env python3
"""Patch Peanuts Native to accept NSTool's one-version folder as base version 0.

A folder containing exefs/ and romfs/ directly is a complete base-game dump
when it has exefs/main, exefs/main.npdm and a non-empty RomFS. Keep the dump
where the user put it; do not move or rewrite any game files.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_single_version_dump.py <peanuts-native checkout>")
root = Path(sys.argv[1]).resolve()

def replace_once(relpath: str, old: str, new: str) -> None:
    path = root / relpath
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{relpath}: expected one patch target, found {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")

replace_once(
    "android/app/src/main/java/dev/nxnative/android/DumpLayout.java",
    '                if (new File(root(new File(dump, "base")), "exefs/main").isFile()) out.add(0L);   // a one-version folder (exefs/ at its top) has no version number: not here',
    '''                File base = new File(dump, "base");
                File direct = root(dump);
                if (new File(root(base), "exefs/main").isFile()) out.add(0L);
                else if (new File(direct, "exefs/main").isFile() && new File(direct, "romfs").isDirectory()) out.add(0L); // NSTool one-version export: base version 0''',
)
replace_once(
    "android/app/src/main/java/dev/nxnative/android/DumpLayout.java",
    '''        File b = new File(dump, "base");
        return b.isDirectory() || !new File(dump, "exefs").isDirectory() ? root(b) : dump;''',
    '''        File b = new File(dump, "base");
        return root(b.isDirectory() ? b : dump);''',
)
replace_once(
    "android/app/src/main/java/dev/nxnative/android/Pack.java",
    '''     * 1.8.0, an update alone that carries the whole game, or both). One version's folder (exefs/, romfs/ at its top) is not one: it needs a pack made elsewhere.
''',
    '''     * 1.8.0, an update alone that carries the whole game, both, or a one-version folder (exefs/ and romfs/ at its top, treated as base version 0).
''',
)
replace_once(
    "scripts/pack_dump.py",
    "def base_dir(dump): return package_root(dump / 'base')   # a folder dump's base package",
    """def base_dir(dump):   # a folder dump's base package; NSTool may place a one-version base directly at the dump root
    base = dump / 'base'
    if base.is_dir(): return package_root(base)
    if (dump / 'exefs').is_dir() or (dump / 'romfs').is_dir(): return package_root(dump)
    return package_root(base)""",
)
replace_once(
    "android/app/src/jvmtest/java/dev/nxnative/android/GameListJvmTest.java",
    '''        // not a dump: an empty folder, a one-version folder
        File empty = new File(root, "empty");
        empty.mkdirs();
        check(DumpLayout.find(empty).error.contains("not a dump folder") && !Pack.isDump(empty), "empty folder");
        File one = new File(root, "one-version");
        version(one, FIX2, 11, true);
        check(DumpLayout.find(one).error.contains("one version's folder") && !Pack.isDump(one), "one-version folder: " + DumpLayout.find(one).error);''',
    '''        // An empty folder is not a dump; NSTool's one-version folder is the base (version 0).
        File empty = new File(root, "empty");
        empty.mkdirs();
        check(DumpLayout.find(empty).error.contains("not a dump folder") && !Pack.isDump(empty), "empty folder");
        File one = new File(root, "one-version");
        version(one, FIX2, 11, true);
        DumpLayout.Found oneFound = DumpLayout.find(one);
        check(DumpLayout.versions(one).equals(List.of(0L)) && oneFound.error == null && oneFound.version == 0
                && oneFound.buildIds.get("main").startsWith(id(11)) && Pack.isDump(one),
                "one-version folder is base version 0: " + oneFound.error);
        check(DumpLayout.path(one, 0, "romfs").endsWith("one-version/romfs".replace('/', File.separatorChar)),
                "one-version folder's RomFS: " + DumpLayout.path(one, 0, "romfs"));
        Pack onePack = pack(new File(run, "pack-one-version"), FIX2, "0", "main=" + id(11), "sdk=" + id(3));
        check(BuildCheck.build(onePack, one).ok(), "one-version folder's Android pack fits");''',
)
replace_once(
    "dm/tools/tests/test_android_gamelist.py",
    '''            cases = {'base-only': [('base', True, True)], 'update-only': [('updates/65537', True, True)], 'both': [('base', True, True), ('updates/65537', True, True)],
                     'code-in-update': [('base', True, True), ('updates/65537', True, False)]}
            for name, parts in cases.items():
                folder = t / name
                for sub, code, rom in parts: version(folder / sub, code, rom)
                r = subprocess.run([sys.executable, '-I', str(REPO / 'dm/tools/nup_pack.py'), str(folder), str(t / f'{name}.nup'), '--raw'], capture_output=True, text=True)
                self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
                for d in (folder, t / f'{name}.nup'):
                    with self.subTest(d.name):
                        if d.is_dir(): self.assertTrue(pack_dump.is_dump(d))
                        app, vers, dlc = pack_dump.scan(d)
                        self.assertEqual(app, tid)
                        ver = max(vers, default=0)
                        self.assertEqual(ver, 0 if name == 'base-only' else 65537)
                        exe, rom = pack_dump.version_part(d, vers, ver, 'exefs'), pack_dump.version_part(d, vers, ver, 'romfs')
                        self.assertTrue((exe / 'main.npdm').is_file() and rom.is_dir(), (exe, rom))
                        own = 'base' if name == 'base-only' else 'update/65537' if d.suffix == '.nup' else 'updates/65537'
                        base = 'base'
                        self.assertTrue(str(exe).endswith(f'{own}/exefs'), exe)
                        self.assertTrue(str(rom).endswith(f'{base if name == "code-in-update" else own}/romfs'), rom)''',
    '''            cases = {'base-only': [('base', True, True)], 'update-only': [('updates/65537', True, True)], 'both': [('base', True, True), ('updates/65537', True, True)],
                     'code-in-update': [('base', True, True), ('updates/65537', True, False)], 'single-version-root': [('', True, True)]}
            for name, parts in cases.items():
                folder = t / name
                for sub, code, rom in parts: version(folder / sub, code, rom)
                inputs = [folder]
                if name != 'single-version-root':   # the NUP container format keeps its explicit base/ directory
                    nup_file = t / f'{name}.nup'
                    r = subprocess.run([sys.executable, '-I', str(REPO / 'dm/tools/nup_pack.py'), str(folder), str(nup_file), '--raw'], capture_output=True, text=True)
                    self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
                    inputs.append(nup_file)
                for d in inputs:
                    with self.subTest(d.name):
                        if d.is_dir(): self.assertTrue(pack_dump.is_dump(d))
                        app, vers, dlc = pack_dump.scan(d)
                        self.assertEqual(app, tid)
                        ver = max(vers, default=0)
                        self.assertEqual(ver, 0 if name in ('base-only', 'single-version-root') else 65537)
                        exe, rom = pack_dump.version_part(d, vers, ver, 'exefs'), pack_dump.version_part(d, vers, ver, 'romfs')
                        self.assertTrue((exe / 'main.npdm').is_file() and rom.is_dir(), (exe, rom))
                        if name == 'single-version-root':
                            self.assertEqual(exe, folder / 'exefs')
                            self.assertEqual(rom, folder / 'romfs')
                        else:
                            own = 'base' if name == 'base-only' else 'update/65537' if d.suffix == '.nup' else 'updates/65537'
                            base = 'base'
                            self.assertTrue(str(exe).endswith(f'{own}/exefs'), exe)
                            self.assertTrue(str(rom).endswith(f'{base if name == "code-in-update" else own}/romfs'), rom)''',
)

print("Applied one-version NSTool dump support to Android and Python packers, with regression tests.")

# Beta 3: do not turn Android storage denial into a false "one version's folder".
replace_once(
    "android/app/src/main/java/dev/nxnative/android/DumpLayout.java",
    """    /** What {@link #find} found: the game and the version Prepare starts from with its modules' build IDs, or why there is none (error, a sentence) */
    static final class Found {""",
    """    /**
     * Detect access denial before layout checks. File.isFile()/listFiles() silently return false/null
     * for some EACCES cases, which otherwise looks exactly like a missing one-version layout.
     */
    static String accessProblem(File dump) {
        if (dump == null) return null;
        if (Nup.isNup(dump)) {
            if (dump.exists() && !dump.canRead()) return "no_storage_permission: cannot read " + dump + ". Allow All files access for this app.";
            return null;
        }
        if (!dump.exists()) return null;
        if (!dump.canRead() || (dump.isDirectory() && dump.list() == null))
            return "no_storage_permission: cannot list dump folder " + dump + ". Allow All files access for this app.";
        File[] versions = { new File(dump, "base"), dump };
        for (File version : versions) {
            File pkg = root(version);
            File exefs = new File(pkg, "exefs");
            if (exefs.exists() && exefs.isDirectory()) {
                String[] names = exefs.list();
                if (names == null) return "no_storage_permission: cannot list " + exefs + ". Allow All files access for this app.";
                for (String name : new String[] {"main", "main.npdm"}) {
                    boolean listed = java.util.Arrays.asList(names).contains(name);
                    File file = new File(exefs, name);
                    if (listed || file.exists()) {
                        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) { in.read(); }
                        catch (IOException e) {
                            String why = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                            if (why.contains("eacces") || why.contains("permission denied") || e instanceof java.nio.file.AccessDeniedException)
                                return "no_storage_permission: cannot read " + file + ": " + e.getMessage() + ". Allow All files access for this app.";
                        }
                    }
                }
            }
            File romfs = new File(pkg, "romfs");
            if (romfs.exists() && romfs.isDirectory() && (!romfs.canRead() || romfs.list() == null))
                return "no_storage_permission: cannot list " + romfs + ". Allow All files access for this app.";
        }
        return null;
    }

    /** What {@link #find} found: the game and the version Prepare starts from with its modules' build IDs, or why there is none (error, a sentence) */
    static final class Found {""",
)
replace_once(
    "android/app/src/main/java/dev/nxnative/android/DumpLayout.java",
    """    private static String find(Found f, File dump, boolean anyBuild) {
        String fn = dump.getName();""",
    """    private static String find(Found f, File dump, boolean anyBuild) {
        String access = accessProblem(dump);
        if (access != null) return access;
        String fn = dump.getName();""",
)
replace_once(
    "android/app/src/main/java/dev/nxnative/android/Pack.java",
    """        File npdm = npdmOf(game);
        if (npdm == null) throw new IOException(game + " has no main.npdm (base/exefs/, updates/<version>/exefs/ or exefs/).");""",
    """        String access = DumpLayout.accessProblem(game);
        if (access != null) throw new IOException(access);
        File npdm = npdmOf(game);
        if (npdm == null) throw new IOException(game + " has no main.npdm (base/exefs/, updates/<version>/exefs/ or exefs/).");""",
)
replace_once(
    "android/app/src/main/java/dev/nxnative/android/MainActivity.java",
    """    boolean needAccess() {
        if (Environment.isExternalStorageManager()) return false;
        new PadFocus.Builder(this).setTitle("Allow file access").setMessage("The app needs All files access to read your game dump. Allow it, then come back.")
                .setPositiveButton("Open settings", (d, w) -> openAccessSettings()).setNegativeButton("Cancel", null).show();
        return true;
    }""",
    """    boolean needAccess() {
        if (Environment.isExternalStorageManager()) return false;
        android.util.Log.w("nxnative", "[RED] no_storage_permission: package " + getPackageName() + " needs MANAGE_EXTERNAL_STORAGE");
        new PadFocus.Builder(this).setTitle("Allow All files access")
                .setMessage("Allow 'Acesso a todos os arquivos' for this app (package " + getPackageName() + "), then return and try again.")
                .setPositiveButton("Open app settings", (d, w) -> openAccessSettings()).setNegativeButton("Cancel", null).show();
        return true;
    }""",
)
replace_once(
    "android/app/src/main/java/dev/nxnative/android/MainActivity.java",
    """    void gamePicked(File game) {
        if (relinkFor != null) { Game g = relinkFor; relinkFor = null; relinkTo(g, game); return; }""",
    """    void gamePicked(File game) {
        if (relinkFor != null) { Game g = relinkFor; relinkFor = null; relinkTo(g, game); return; }
        if (needAccess(game)) return;""",
)
replace_once(
    "android/app/src/ondevice/java/dev/nxnative/android/Ondevice.java",
    """    static void prepare(MainActivity a, File dump, boolean again) { if (panel != null && panel.a == a) panel.picked(dump, false, null, again); }""",
    """    static void prepare(MainActivity a, File dump, boolean again) {
        if (panel != null && panel.a == a && !a.needAccess(dump)) panel.picked(dump, false, null, again);
    }""",
)
