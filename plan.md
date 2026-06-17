1. **Optimize FavouritesDao**: Add `@Upsert abstract suspend fun upsert(entities: Collection<FavouriteEntity>)`.
2. **Optimize StatsDao**: Add `@Upsert abstract suspend fun upsert(entities: Collection<StatsEntity>)`.
3. **Optimize ScrobblingDao**: Add `@Upsert abstract suspend fun upsert(entities: Collection<ScrobblingEntity>)`.
4. **Optimize MangaDao**:
Add the following methods:
```kotlin
	@Upsert
	protected abstract suspend fun upsert(mangas: Collection<MangaEntity>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	protected abstract suspend fun insertTagRelations(tags: Collection<MangaTagsEntity>)

	@Query("DELETE FROM manga_tags WHERE manga_id IN (:mangaIds)")
	abstract suspend fun clearTagRelations(mangaIds: Collection<Long>)
```
Modify `upsert` or add `upsertAll`:
```kotlin
	@Transaction
	open suspend fun upsert(mangasAndTags: Collection<Pair<MangaEntity, Iterable<TagEntity>?>>) {
		upsert(mangasAndTags.map { it.first })
		val mangaIdsWithTags = mangasAndTags.filter { it.second != null }.map { it.first.id }.chunked(900)
		mangaIdsWithTags.forEach { ids ->
			if (ids.isNotEmpty()) {
				clearTagRelations(ids)
			}
		}

		val tagRelations = mangasAndTags.flatMap { (manga, tags) ->
			tags?.map { MangaTagsEntity(manga.id, it.id) } ?: emptyList()
		}
		if (tagRelations.isNotEmpty()) {
			insertTagRelations(tagRelations)
		}
	}
```
5. **Optimize HistoryDao**:
Update `upsert(entities: Iterable<HistoryEntity>)` to use `@Upsert` instead of doing row-by-row logic (Wait! `upsert` for `HistoryDao` has a specific implementation that does `UPDATE ... SET ... deleted_at = 0 WHERE manga_id = :mangaId`). Looking at the code, it's doing `if (update(e) == 0) insert(e)`. This is equivalent to an upsert except that it's doing a custom update statement where it sets `deleted_at = 0`. Let's create a batch version of it, but maybe just using `chunked` logic or we can rely on standard room batch execution if we create `@Insert(onConflict = OnConflictStrategy.IGNORE)` batch insert and `@Update` batch update. Since it's doing a custom query for update, it's tricky to batch the custom update without a loop. We'll leave `HistoryDao` alone for now, or just leave `upsert(entities: Iterable<HistoryEntity>)` unchanged, since `MihonBackupManager` doesn't currently call it in batch. Wait, I can update `HistoryDao` to have:
```kotlin
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	protected abstract suspend fun insert(entities: Collection<HistoryEntity>): List<Long>
```
and call `update` and `insert` one by one, wait, batch `update` via a custom query in SQLite is not natively supported to take different parameters per row. Room will run it in a loop anyway.

Let's focus on `MangaDao`, `FavouritesDao`, `StatsDao`, and `ScrobblingDao`.

6. **Optimize MihonBackupManager**:
Update `restoreManga`:
Instead of:
```kotlin
        pending.flatMapTo(linkedSetOf()) { it.tags }
            .takeIf { it.isNotEmpty() }
            ?.let { db.getTagsDao().upsert(it.toList()) }
        pending.forEach { item -> db.getMangaDao().upsert(item.manga, item.tags) }
        pending.forEach { item -> item.favourites.forEach { db.getFavouritesDao().upsert(it) } }
        pending.forEach { item -> db.getChaptersDao().replaceAll(item.manga.id, item.chapters) }
        pending.forEach { item -> item.history?.let { db.getHistoryDao().upsert(it) } }
        pending.forEach { item -> item.stats?.let { db.getStatsDao().upsert(it) } }
        pending.forEach { item ->
            if (item.bookmarks.isNotEmpty()) {
                db.getBookmarksDao().upsert(item.bookmarks)
            }
        }
        pending.forEach { item ->
            item.scrobblings.forEach {
                db.getScrobblingDao().upsert(it)
                diagnostics.restoredTrackingCount += 1
            }
        }
```

We will replace it with:
```kotlin
        pending.flatMapTo(linkedSetOf()) { it.tags }
            .takeIf { it.isNotEmpty() }
            ?.let { db.getTagsDao().upsert(it.toList()) }

        val mangasAndTags = pending.map { it.manga to it.tags }
        if (mangasAndTags.isNotEmpty()) {
            db.getMangaDao().upsertAll(mangasAndTags) // Wait, I'll name it upsertAll so it doesn't clash
        }

        val favourites = pending.flatMap { it.favourites }
        if (favourites.isNotEmpty()) {
            db.getFavouritesDao().upsert(favourites)
        }

        pending.forEach { item -> db.getChaptersDao().replaceAll(item.manga.id, item.chapters) }

        val histories = pending.mapNotNull { it.history }
        if (histories.isNotEmpty()) {
            db.getHistoryDao().upsert(histories) // upsert(Iterable) already exists!
        }

        val stats = pending.mapNotNull { it.stats }
        if (stats.isNotEmpty()) {
            db.getStatsDao().upsert(stats)
        }

        pending.forEach { item ->
            if (item.bookmarks.isNotEmpty()) {
                db.getBookmarksDao().upsert(item.bookmarks) // bookmarks already batched
            }
        }

        val scrobblings = pending.flatMap { it.scrobblings }
        if (scrobblings.isNotEmpty()) {
            db.getScrobblingDao().upsert(scrobblings)
            diagnostics.restoredTrackingCount += scrobblings.size
        }
```

Wait, `upsert` in MangaDao should be named `upsertAll(mangasAndTags)` to be clear.

Let's review the plan with the `request_plan_review` tool.
