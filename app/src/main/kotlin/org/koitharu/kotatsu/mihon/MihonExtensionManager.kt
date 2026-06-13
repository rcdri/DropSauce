package org.koitharu.kotatsu.mihon

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.extensions.runtime.ExternalExtensionManagerFacade
import org.koitharu.kotatsu.mihon.model.MihonLoadResult
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MihonExtensionManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val loader: MihonExtensionLoader,
	private val trust: MihonExtensionTrust,
) {

	companion object {
		@Volatile
		private var activeInstance: MihonExtensionManager? = null

		fun getById(sourceId: Long): MihonMangaSource? = activeInstance?.getMihonMangaSourceById(sourceId)

		fun getByName(name: String): MihonMangaSource? = activeInstance?.getMihonMangaSourceByName(name)
	}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val refreshMutex = Mutex()

	private val facade = ExternalExtensionManagerFacade<
		MihonLoadResult,
		MihonLoadResult.Success,
		MihonLoadResult.Error,
		Source,
		CatalogueSource,
		MihonMangaSource,
	>(
		context = context,
		scope = scope,
		loadResults = loader::loadExtensions,
		successOf = { it as? MihonLoadResult.Success },
		errorOf = { it as? MihonLoadResult.Error },
		untrustedPackageNameOf = { (it as? MihonLoadResult.Untrusted)?.pkgName },
		successSources = { it.sources },
		successPackageName = { it.pkgName },
		successIsNsfw = { it.isNsfw },
		successCatalogueSources = { it.catalogueSources },
		sourceId = { it.id },
		asCatalogueSource = { it as? CatalogueSource },
		catalogueSourceName = { it.name },
		catalogueSourceLang = { it.lang },
		buildWrappedSource = { catalogueSource, pkgName, isNsfw, hasLanguageSuffix ->
			MihonMangaSource(catalogueSource, pkgName, isNsfw, hasLanguageSuffix)
		},
		sourceNamePrefix = "MIHON_",
		errorPackageName = { it.pkgName },
		errorMessage = { it.message },
		errorThrowable = { it.exception },
	)

	val installedExtensions: StateFlow<List<MihonLoadResult.Success>> = facade.installedExtensions
	val failedExtensions: StateFlow<List<MihonLoadResult.Error>> = facade.failedExtensions
	val isLoading: StateFlow<Boolean> = facade.isLoading
	val isReady: StateFlow<Boolean> = facade.isReady

	/** Package names of installed extensions blocked because their signature isn't trusted. */
	val untrustedExtensions: StateFlow<List<String>> = facade.untrustedExtensions

	/** Whether extension signature verification is enforced. Disabled by default. */
	var isVerificationEnabled: Boolean
		get() = trust.isVerificationEnabled
		set(value) {
			trust.isVerificationEnabled = value
		}

	/** Trust the signing certificate the given package is currently signed with. */
	fun trustExtension(pkgName: String) = trust.trust(pkgName)

	/** Revoke trust for a package; it will be blocked on the next load when verification is on. */
	fun revokeExtensionTrust(pkgName: String) = trust.revoke(pkgName)

	init {
		activeInstance = this
	}

	fun initialize() {
		facade.initialize()
	}

	suspend fun loadExtensions() {
		refreshMutex.withLock {
			facade.loadExtensions()
		}
	}

	suspend fun ensureReady(forceRefresh: Boolean = false) {
		initialize()
		if (forceRefresh) {
			loadExtensions()
			return
		}
		if (!isReady.value && !isLoading.value) {
			loadExtensions()
		}
		if (isLoading.value) {
			isLoading.filter { !it }.first()
		}
		if (!isReady.value) {
			loadExtensions()
		}
	}

	fun getCatalogueSources(): List<CatalogueSource> = facade.getCatalogueSources()

	fun getMihonMangaSources(): List<MihonMangaSource> = facade.getWrappedSources()

	fun getSourceById(sourceId: Long): Source? = facade.getSourceById(sourceId)

	fun getCatalogueSourceById(sourceId: Long): CatalogueSource? = facade.getCatalogueSourceById(sourceId)

	fun getMihonMangaSourceById(sourceId: Long): MihonMangaSource? = facade.getWrappedSourceById(sourceId)

	fun getMihonMangaSourceByName(name: String): MihonMangaSource? {
		val exact = facade.getWrappedSourceByName(name)
		if (exact != null) return exact
		val sourceId = name.removePrefix("MIHON_").substringBefore(':').toLongOrNull() ?: return null
		return facade.getWrappedSourceById(sourceId)
	}

	fun getSourcesByLanguage(): Map<String, List<CatalogueSource>> = facade.getSourcesByLanguage()

	fun getSourceCount(): Int = facade.getSourceCount()

	fun hasExtensions(): Boolean = facade.hasExtensions()
}
