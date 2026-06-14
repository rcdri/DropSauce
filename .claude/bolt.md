# Bolt's Journal ⚡

Critical, codebase-specific performance learnings only.

## Environment / verification
- This is an Android Gradle app (Kotatsu fork, namespace `org.koitharu.kotatsu`), NOT a JS project — no pnpm/lint/test.
- The web execution container has **no Android SDK, no gradle dependency cache, and network to the gradle/maven repos is blocked**. `./gradlew` fails at plugin resolution. So unit tests / lint / build **cannot be run locally here** — rely on the `pr-preview.yml` / `nightly.yml` CI builds for compile verification. Pure-JVM unit tests live in `app/src/test/kotlin/...` and are the right place to add fast, logic-only verification.

## Hot paths / bottlenecks (this codebase)
- **N+1 DB reads in `MangaListMapper`**: `toListModelImpl` calls 4 separate suspend Room queries *per manga* (progress / favourite / saved / new-chapters). `toListModelList` mapped them **sequentially**, so loading a big Favourites/History list = N×4 serialized queries → the real "tab switch is slow" cost. Fix applied: map items concurrently via `coroutineScope { manga.map { async { … } }.awaitAll() }`. Concurrency is naturally bounded by Room's small query executor, so no thread explosion. The *fuller* win (batch `WHERE id IN (:ids)` DAO queries → 4 total) is bigger but needs new DAO/repo methods + chunking for SQLite's 999-var limit — a dedicated, riskier change. Loops that call the single `toListModel` (HistoryListViewModel, UpdatesViewModel) still have the sequential N+1.
- **DETAILED_LIST built ChipModels only to throw them away**: `MangaDetailedListModel.tags` was a `List<ChipModel>` (each tag lowercased + warn-set lookup + alloc) but the adapter only `joinToString`'d it to plain text — and re-joined authors+tags on *every* `onBindViewHolder`. Replaced with precomputed `authorsText`/`tagsText` strings built in the mapper. Compact model already did this (`subtitle`).
- Reader pipeline (`PageLoader`, `ChaptersLoader`, `ChapterPages`, `BaseReaderAdapter`) is already well-optimized: O(1) index maps, AsyncListDiffer, background dispatchers. Reader-side findings are micro (resource-lookup caching, a 10KB array reused on the crop fallback path) — not worth a PR.

## Patterns found
- `sortedBy { ...expensiveSelector... }` recomputes the selector on every comparison (O(n·log n) calls) because it's backed by `compareBy`. Found 3 sites sorting search results by `String.levenshteinDistance(query)` (MangaSearchRepository, FavouritesRepository, HistoryRepository). Added `Iterable.sortedByCached` (Schwartzian transform) in `core/util/ext/Collections.kt` to compute each key once. Behavior is identical (stable sort preserved).
