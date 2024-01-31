package com.peaceray.codeword.domain.manager.version.impl

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.peaceray.codeword.BuildConfig
import com.peaceray.codeword.data.model.version.SupportedVersions
import com.peaceray.codeword.data.model.version.Versions
import com.peaceray.codeword.domain.api.CodeWordApi
import com.peaceray.codeword.domain.manager.game.setup.GameSetupManager
import com.peaceray.codeword.domain.manager.version.VersionsManager
import com.peaceray.codeword.glue.ForApplication
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionsManagerImpl @Inject constructor(
    @ForApplication val context: Context,
    val gameSetupManager: GameSetupManager,
    val api: CodeWordApi
): VersionsManager {

    //region SharedPreferences
    //---------------------------------------------------------------------------------------------

    private companion object {
        const val PREFS_FILENAME = "VERSIONS_MANAGER_PREFERENCES"
        // supported versions
        const val KEY_SUPPORTED_VERSIONS = "SUPPORTED_VERSIONS"
        const val KEY_SUPPORTED_VERSIONS_CACHED_AT = "SUPPORTED_VERSIONS_CACHED_AT"
        const val KEY_SUPPORTED_VERSIONS_EXPIRES_AT = "SUPPORTED_VERSIONS_EXPIRES_AT"

        // gson
        val gson = Gson()
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.getSupportedVersions(lifespan: Long): SupportedVersions? {
        // check that the cached version hasn't expired
        val time = System.currentTimeMillis()
        val expiresAt = getLong(KEY_SUPPORTED_VERSIONS_EXPIRES_AT, 0)
        val cachedAt = getLong(KEY_SUPPORTED_VERSIONS_CACHED_AT, 0)
        if (expiresAt < time || (lifespan >= 0 && cachedAt + lifespan < time)) {
            Timber.v("Cached SupportedVersions expired")
            return null
        }

        // retrieve and build the SupportedVersions object
        return try {
            gson.fromJson(
                getString(KEY_SUPPORTED_VERSIONS, null),
                SupportedVersions::class.java
            )
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving cached SupportedVersions")
            null
        }
    }

    private fun SharedPreferences.Editor.putSupportedVersions(versions: SupportedVersions): SharedPreferences.Editor {
        try {
            val cachedAt = System.currentTimeMillis()
            val expiresAt = cachedAt + versions.lifespan

            putString(KEY_SUPPORTED_VERSIONS, gson.toJson(versions))
            putLong(KEY_SUPPORTED_VERSIONS_CACHED_AT, cachedAt)
            putLong(KEY_SUPPORTED_VERSIONS_EXPIRES_AT, expiresAt)
        } catch (e: Exception) {
            Timber.e(e, "Error caching SupportedVersions")
        }

        return this
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Properties
    //---------------------------------------------------------------------------------------------

    /**
     * A Versions instance describing the installed application version. Since this
     * object refers to the installed instance, "minimum" is the same as "current".
     */
    override val applicationVersions: Versions by lazy {
        Versions(
            gameSetupManager.getSeedVersion(),
            BuildConfig.VERSION_CODE
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Asynchronous Getters
    //---------------------------------------------------------------------------------------------

    /**
     * Returns a SupportedVersions instance describing the globally available versions. This function may
     * require external API queries, so should be made off the main thread.
     *
     * @param cacheLifespan The maximum number of milliseconds a cached value should be trusted before
     * a new query is made to the server. Note that the Manager itself, or the server, may impose
     * additional lifespan requirements, the most restrictive of which will be used.
     * @return The versions supported, according to the canonical record (external server).
     */
    override fun getSupportedVersions(
        cacheLifespan: Long,
        allowRemote: Boolean
    ): SupportedVersions {
        // Log
        Timber.v("getSupportedVersions cacheLifespan $cacheLifespan allowRemote $allowRemote")

        // attempt to use a cached version
        var versions = preferences.getSupportedVersions(cacheLifespan)
        Timber.v("getSupportedVersions has cached versions $versions")
        if (versions != null) return versions

        // unavailable or expired; make an API call
        if (!allowRemote) throw IllegalStateException("Cannot provide SupportedVersions without a remote call, but allowRemote = false")

        val response = api.getVersions().execute()
        versions = response.body()!!
        Timber.v("getSupportedVersions has remote versions $versions")

        // cache retrieved value
        preferences.edit()
            .putSupportedVersions(versions)
            .apply()

        return versions
    }

    /**
     * A wrapper for [getSupportedVersions] that checks the application's version against
     * the server's SupportedVersions, returning whether the installed application meets or
     * exceeds the minimum application and seed versions.
     *
     * @param cacheLifespan The maximum number of milliseconds a cached value should be trusted before
     * a new query is made to the server. Note that the Manager itself, or the server, may impose
     * additional lifespan requirements, the most restrictive of which will be used.
     * @return Whether this application meets or exceeds the minimum supported seed and application
     * versions.
     */
    override fun isApplicationSupported(
        cacheLifespan: Long,
        allowRemote: Boolean
    ): Boolean {
        val supported = getSupportedVersions(cacheLifespan, allowRemote)
        return applicationVersions.seed >= supported.minimum.seed
                && applicationVersions.application >= supported.minimum.application
    }

    /**
     * A wrapper for [getSupportedVersions] that checks the application's version against
     * the server's SupportedVersions, returning whether the installed application meets or
     * exceeds the current application and seed versions. The inverse of this function may be
     * called "isUpdateAvailable".
     *
     * @param cacheLifespan The maximum number of milliseconds a cached value should be trusted before
     * a new query is made to the server. Note that the Manager itself, or the server, may impose
     * additional lifespan requirements, the most restrictive of which will be used.
     * @return Whether this application meets or exceeds the current seed and application
     * versions.
     */
    override fun isApplicationCurrent(
        cacheLifespan: Long,
        allowRemote: Boolean
    ): Boolean {
        val supported = getSupportedVersions(cacheLifespan, allowRemote)
        return applicationVersions.seed >= supported.current.seed
                && applicationVersions.application >= supported.current.application
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}