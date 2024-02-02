package com.peaceray.codeword.domain.manager.version

import com.peaceray.codeword.data.model.version.SupportedVersions
import com.peaceray.codeword.data.model.version.Versions
import retrofit2.HttpException

/**
 * A Manager for application and seed "versions". Reports the installed application and available
 * GameSetup seed versions, the minimum necessary version(s) as reported by the web API.
 *
 * Values provided as properties are safe and efficient to access on any thread. Values provided
 * via function calls may require long-running computation or network operations, and should be
 * queried off the main thread -- for instance in a worker thread or via coroutines.
 */
interface VersionsManager {

    //region Properties
    //---------------------------------------------------------------------------------------------

    /**
     * A Versions instance describing the installed application version. Since this
     * object refers to the installed instance, "minimum" is the same as "current".
     */
    val applicationVersions: Versions

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Asynchronous Getters
    //---------------------------------------------------------------------------------------------

    /**
     * Returns a SupportedVersions instance describing the globally available versions. This function may
     * require external API queries, so should be made off the main thread.
     *
     * Throws an exception if an appropriate value cannot be found.
     *
     * @param cacheLifespan The maximum number of milliseconds a cached value should be trusted before
     * a new query is made to the server. Note that the Manager itself, or the server, may impose
     * additional lifespan requirements, the most restrictive of which will be used.
     * @param allowRemote Allow the Manager to make a Remote call. If 'false', this function
     * will only retrieve cached values, throwing an exception if unavailable or expired.
     * @return The versions supported, according to the canonical record (external server).
     */
    @Throws(IllegalStateException::class, HttpException::class)
    suspend fun getSupportedVersions(
        cacheLifespan: Long = Long.MAX_VALUE,
        allowRemote: Boolean = true
    ): SupportedVersions

    /**
     * A wrapper for [getSupportedVersions] that checks the application's version against
     * the server's SupportedVersions, returning whether the installed application meets or
     * exceeds the minimum application and seed versions.
     *
     * @param cacheLifespan The maximum number of milliseconds a cached value should be trusted before
     * a new query is made to the server. Note that the Manager itself, or the server, may impose
     * additional lifespan requirements, the most restrictive of which will be used.
     * @param allowRemote Allow the Manager to make a Remote call. If 'false', this function
     * will only retrieve cached values, throwing an exception if unavailable or expired.
     * @return Whether this application meets or exceeds the minimum supported seed and application
     * versions.
     */
    @Throws(IllegalStateException::class, HttpException::class)
    suspend fun isApplicationSupported(
        cacheLifespan: Long = Long.MAX_VALUE,
        allowRemote: Boolean = true
    ): Boolean

    /**
     * A wrapper for [getSupportedVersions] that checks the application's version against
     * the server's SupportedVersions, returning whether the installed application meets or
     * exceeds the current application and seed versions. The inverse of this function may be
     * called "isUpdateAvailable".
     *
     * @param cacheLifespan The maximum number of milliseconds a cached value should be trusted before
     * a new query is made to the server. Note that the Manager itself, or the server, may impose
     * additional lifespan requirements, the most restrictive of which will be used.
     * @param allowRemote Allow the Manager to make a Remote call. If 'false', this function
     * will only retrieve cached values, throwing an exception if unavailable or expired.
     * @return Whether this application meets or exceeds the current seed and application
     * versions.
     */
    @Throws(IllegalStateException::class, HttpException::class)
    suspend fun isApplicationCurrent(
        cacheLifespan: Long = Long.MAX_VALUE,
        allowRemote: Boolean = true
    ): Boolean

    //---------------------------------------------------------------------------------------------
    //endregion

}