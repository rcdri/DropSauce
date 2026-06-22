# Kotatsu → Mihon source map generator

Generates `app/src/main/res/raw/kotatsu_source_map.json`, the predefined table the
**Migrate Kotatsu library** feature (`org.koitharu.kotatsu.kotatsumigration`) uses to map a
restored Kotatsu backup's built-in source names onto installed Mihon (keiyoushi) extensions.

The table is keyed by the **raw Kotatsu parser enum name** stored in backups (e.g. `MANGADEX`,
`WEBTOONS_EN`) → `{ id, name, lang, pkg }`, where `id` is the Mihon `CatalogueSource.id`
(resolved at runtime via `MihonExtensionManager.getById`).

## How matching works
For each `@MangaSourceParser("ENUM", "Title"[, "lang"])` annotation found in the parser repos,
the script collects the source's `ConfigKey.Domain(...)` literals and matches against the
keiyoushi extension index, **primarily by website domain**, then by exact normalized name.
Domain matches are the same site by construction; name-only matches are mostly the same site on a
rotated/rebranded domain. The per-manga title search at migration time is a safety net for any
mismatch (a different site won't contain the user's manga → it's skipped).

## Regenerate / add another fork
```sh
cd <scratch dir>
curl -s https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json -o keiyoushi_index.json
git clone --depth 1 https://github.com/Kotatsu-Redo/kotatsu-parsers-redo.git redo-parsers
git clone --depth 1 https://github.com/kotatsuapp/kotatsu-parsers.git orig-parsers
# add more forks: clone next to these, then add the path to scan_repo(...) calls in build_map.py
python build_map.py                 # writes kotatsu_source_map.json (+ mapping_full.json, unmatched.json)
python verify_popular.py            # sanity-check coverage of popular sources
cp kotatsu_source_map.json <repo>/app/src/main/res/raw/kotatsu_source_map.json
```

## Curation
`build_map.py` has `OVERRIDES` (force an enum → explicit Mihon id) and `EXCLUDES` (drop a
verified-wrong auto-match). Current: `EXCLUDES = {MANGAPARK}` — the real mangapark.net is not on
keiyoushi; the only "Manga-Park" there is an unrelated knockoff.
