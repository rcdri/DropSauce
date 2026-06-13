package org.koitharu.kotatsu.mihon

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trust store for Mihon extension APKs. When verification is enabled, an extension is only loaded
 * if the SHA-256 of its signing certificate has been explicitly trusted, mirroring Mihon's own
 * untrusted-extension model. Verification is **disabled by default** so existing installs keep
 * working until the user opts in (and can then trust the extensions they already have).
 *
 * Trust state lives in its own prefs file so it isn't swept up by the app-settings backup/restore.
 */
@Singleton
class MihonExtensionTrust @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	/** When true, extensions whose signature isn't in the trusted set are blocked from loading. */
	var isVerificationEnabled: Boolean
		get() = prefs.getBoolean(KEY_ENABLED, false)
		set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

	fun isTrusted(pkgName: String, signatureHashes: List<String>): Boolean {
		if (signatureHashes.isEmpty()) return false
		val trusted = prefs.getStringSet(keyFor(pkgName), null).orEmpty()
		return signatureHashes.any { it in trusted }
	}

	/** Trust the signing certificate(s) the package is currently signed with. */
	fun trust(pkgName: String) {
		val hashes = signatureHashes(context.packageManager, pkgName)
		if (hashes.isNotEmpty()) {
			prefs.edit { putStringSet(keyFor(pkgName), hashes.toSet()) }
		}
	}

	fun revoke(pkgName: String) = prefs.edit { remove(keyFor(pkgName)) }

	private fun keyFor(pkgName: String) = "sig_$pkgName"

	companion object {

		private const val PREFS_NAME = "mihon_extension_trust"
		private const val KEY_ENABLED = "verification_enabled"

		/** SHA-256 hex of each signing certificate the package is signed with, or empty on failure. */
		@Suppress("DEPRECATION", "PackageManagerGetSignatures")
		fun signatureHashes(pm: PackageManager, pkgName: String): List<String> {
			return try {
				val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					val signingInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
					when {
						signingInfo == null -> null
						signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
						else -> signingInfo.signingCertificateHistory
					}
				} else {
					pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures
				}
				val digest = MessageDigest.getInstance("SHA-256")
				signatures?.map { signature ->
					digest.digest(signature.toByteArray())
						.joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
				}.orEmpty()
			} catch (e: PackageManager.NameNotFoundException) {
				emptyList()
			}
		}
	}
}
