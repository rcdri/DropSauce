# Bolt's Journal ⚡

Critical, codebase-specific performance learnings only.

## Environment / verification
- This is an Android Gradle app (Kotatsu fork, namespace `org.koitharu.kotatsu`), NOT a JS project — no pnpm/lint/test.
- The web execution container has **no Android SDK, no gradle dependency cache, and network to the gradle/maven repos is blocked**. `./gradlew` fails at plugin resolution. So unit tests / lint / build **cannot be run locally here** — rely on the `pr-preview.yml` / `nightly.yml` CI builds for compile verification. Pure-JVM unit tests live in `app/src/test/kotlin/...` and are the right place to add fast, logic-only verification.

## Patterns found
- `sortedBy { ...expensiveSelector... }` recomputes the selector on every comparison (O(n·log n) calls) because it's backed by `compareBy`. Found 3 sites sorting search results by `String.levenshteinDistance(query)` (MangaSearchRepository, FavouritesRepository, HistoryRepository). Added `Iterable.sortedByCached` (Schwartzian transform) in `core/util/ext/Collections.kt` to compute each key once. Behavior is identical (stable sort preserved).
