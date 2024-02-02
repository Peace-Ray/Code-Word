package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.version.SupportedVersions
import com.peaceray.codeword.data.model.version.Versions
import com.peaceray.codeword.domain.manager.version.VersionsManager
import com.peaceray.codeword.presentation.contracts.FeatureAvailabilityContract
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

class FeatureAvailabilityPresenter @Inject constructor():
    FeatureAvailabilityContract.Presenter,
    BasePresenter<FeatureAvailabilityContract.View>()
{
    @Inject lateinit var versionsManager: VersionsManager

    // progress holders: features, progress, disposable operations
    private val features: MutableSet<FeatureAvailabilityContract.Feature> = mutableSetOf()
    private val featuresPending: MutableSet<FeatureAvailabilityContract.Feature> = mutableSetOf()

    override fun onAttached() {
        super.onAttached()

        // retrieve Features of interest
        features.addAll(view!!.getFeatures())
        featuresPending.addAll(features)

        // check availability of each
        for (feature in featuresPending) {
            checkFeatureAvailability(feature)
        }
    }

    override fun onDetached() {
        super.onDetached()

        features.clear()
        featuresPending.clear()
    }

    private fun checkFeatureAvailability(feature: FeatureAvailabilityContract.Feature) {
        when (feature) {
            FeatureAvailabilityContract.Feature.APPLICATION -> viewScope.launch{
                versionsDeferred.awaitAndReport(feature) { f, supportedVersions ->
                    reportFeatureAvailabilityByVersion(
                        f,
                        applicationVersion.application,
                        supportedVersions.current.application,
                        supportedVersions.minimum.application
                    )
                }
            }
            FeatureAvailabilityContract.Feature.SEED -> viewScope.launch{
                versionsDeferred.awaitAndReport(feature) { f, supportedVersions ->
                    reportFeatureAvailabilityByVersion(
                        f,
                        applicationVersion.seed,
                        supportedVersions.current.seed,
                        supportedVersions.minimum.seed
                    )
                }
            }
            FeatureAvailabilityContract.Feature.DAILY -> viewScope.launch{
                versionsDeferred.awaitAndReport(feature) { f, supportedVersions ->
                    reportFeatureAvailabilityByVersion(
                        f,
                        applicationVersion.seed,
                        supportedVersions.current.seed,
                        supportedVersions.minimum.seed
                    )
                }
            }
        }
    }

    private suspend fun <T : Any> Deferred<T>.awaitAndReport(
        feature: FeatureAvailabilityContract.Feature,
        report: (FeatureAvailabilityContract.Feature, T) -> Unit
    ) {
        try {
            val t = await()
            report(feature, t)
            onFeatureComplete(feature)
        } catch (error: IllegalStateException) {
            Timber.e(error, "Couldn't check availability of $feature feature")
            view?.setFeatureAvailability(feature, FeatureAvailabilityContract.Availability.UNKNOWN)
            onFeatureComplete(feature)
        } catch (error: HttpException) {
            Timber.e(error, "Couldn't check availability of $feature feature")
            view?.setFeatureAvailability(feature, FeatureAvailabilityContract.Availability.UNKNOWN)
            onFeatureComplete(feature)
        }
    }

    private fun reportFeatureAvailabilityByVersion(
        feature: FeatureAvailabilityContract.Feature,
        local: Int,
        current: Int,
        minimum: Int
    ) {
        val availability = when (local) {
            current -> FeatureAvailabilityContract.Availability.AVAILABLE
            in (minimum + 1)..current -> FeatureAvailabilityContract.Availability.UPDATE_AVAILABLE
            minimum -> FeatureAvailabilityContract.Availability.UPDATE_URGENT
            else -> if (local > current) {
                // shouldn't occur in practice, but happens in local testing and
                // may occur when a new update is first released but API not updated
                FeatureAvailabilityContract.Availability.AVAILABLE
            } else {
                FeatureAvailabilityContract.Availability.UPDATE_REQUIRED
            }
        }

        view?.setFeatureAvailability(feature, availability)
    }

    private fun onFeatureComplete(feature: FeatureAvailabilityContract.Feature) {
        featuresPending.remove(feature)
        // on completion actions

        // refresh local cache of supported versions? no network call unless cache is older
        // than SUPPORTED_VERSIONS_REFRESH_MILLIS
        if (feature in VERSIONED_FEATURES && VERSIONED_FEATURES.all { it !in featuresPending }) {
            // checked at least one versioned feature and all are now complete.
            // schedule a refresh so the cached version remains at most 3 days old.

            makeVersionsDeferred(cacheLifespan = SUPPORTED_VERSIONS_REFRESH_MILLIS)
        }
    }

    //region Behavior / Caching Constants
    //---------------------------------------------------------------------------------------------

    /**
     * TODO: consider moving these constants to some other centralized location, especially
     * if other classes start basing behavior on similar network limitations.
     */
    companion object {
        // Timing
        const val SUPPORTED_VERSIONS_REFRESH_MILLIS = 1000L * 60 * 60 * 24 * 3
        const val SUPPORTED_VERSIONS_FORCE_REFRESH_MILLIS = 1000L * 60 * 60 * 24 * 7

        // Features
        val VERSIONED_FEATURES = setOf(
            FeatureAvailabilityContract.Feature.APPLICATION,
            FeatureAvailabilityContract.Feature.SEED,
            FeatureAvailabilityContract.Feature.DAILY
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Cached Properties / Observables
    //---------------------------------------------------------------------------------------------

    private val applicationVersion: Versions by lazy {
        versionsManager.applicationVersions
    }

    private val versionsDeferred: Deferred<SupportedVersions> by lazy {
        makeVersionsDeferred(cacheLifespan = SUPPORTED_VERSIONS_FORCE_REFRESH_MILLIS)
    }

    private fun makeVersionsDeferred(cacheLifespan: Long, allowRemote: Boolean = true): Deferred<SupportedVersions> {
        Timber.v("Creating SupportedVersions Observable with cacheLifespan $cacheLifespan allowRemote $allowRemote")
        return viewScope.async {
            versionsManager.getSupportedVersions(
                cacheLifespan = cacheLifespan,
                allowRemote = allowRemote
            )
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion
}