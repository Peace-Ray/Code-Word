package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.version.SupportedVersions
import com.peaceray.codeword.data.model.version.Versions
import com.peaceray.codeword.domain.manager.version.VersionsManager
import com.peaceray.codeword.presentation.contracts.FeatureAvailabilityContract
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
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
    private val disposables: MutableSet<Disposable> = mutableSetOf()

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

        for (disposable in disposables) {
            disposable.dispose()
        }
        features.clear()
        featuresPending.clear()
        disposables.clear()
    }

    private fun checkFeatureAvailability(feature: FeatureAvailabilityContract.Feature) {
        when (feature) {
            FeatureAvailabilityContract.Feature.APPLICATION -> {
                versionsObservable.subscribeToReport(feature) { f, supportedVersions ->
                    reportFeatureAvailabilityByVersion(
                        f,
                        applicationVersion.application,
                        supportedVersions.current.application,
                        supportedVersions.minimum.application
                    )
                }
            }
            FeatureAvailabilityContract.Feature.SEED -> {
                versionsObservable.subscribeToReport(feature) { f, supportedVersions ->
                    reportFeatureAvailabilityByVersion(
                        f,
                        applicationVersion.seed,
                        supportedVersions.current.seed,
                        supportedVersions.minimum.seed
                    )
                }
            }
            FeatureAvailabilityContract.Feature.DAILY -> {
                versionsObservable.subscribeToReport(feature) { f, supportedVersions ->
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

    private fun <T : Any> Single<T>.subscribeToReport(
        feature: FeatureAvailabilityContract.Feature,
        report: (FeatureAvailabilityContract.Feature, T) -> Unit
    ) {
        observeOn(AndroidSchedulers.mainThread()).let {
            disposables.add(it.subscribe(
                { t ->
                    report(feature, t)
                    onFeatureComplete(feature)
                }, { error ->
                    Timber.e(error, "Couldn't check availability of $feature feature.")
                    view?.setFeatureAvailability(feature, FeatureAvailabilityContract.Availability.UNKNOWN)
                    onFeatureComplete(feature)
                }
            ))
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
            else -> FeatureAvailabilityContract.Availability.UPDATE_REQUIRED
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
            makeVersionsObservable(cacheLifespan = SUPPORTED_VERSIONS_REFRESH_MILLIS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
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

    private val versionsObservable: Single<SupportedVersions> by lazy {
        makeVersionsObservable(cacheLifespan = SUPPORTED_VERSIONS_FORCE_REFRESH_MILLIS)
            .cache()
    }

    private fun makeVersionsObservable(cacheLifespan: Long, allowRemote: Boolean = true): Single<SupportedVersions> {
        Timber.v("Creating SupportedVersions Observable with cacheLifespan $cacheLifespan allowRemote $allowRemote")
        return Single.defer {
            Single.just(versionsManager.getSupportedVersions(
                cacheLifespan = cacheLifespan,
                allowRemote = allowRemote
            ))
        }.subscribeOn(Schedulers.io())
    }

    //---------------------------------------------------------------------------------------------
    //endregion
}