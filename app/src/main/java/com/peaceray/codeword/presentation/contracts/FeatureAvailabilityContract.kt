package com.peaceray.codeword.presentation.contracts

/**
 * A Contract describing presenter-view interaction for checking the availability of versioned
 * features. For instance, the Daily challenge might not be available to users who have not
 * updated their local app in a while.
 */
interface FeatureAvailabilityContract: BaseContract {

    enum class Feature {
        APPLICATION,
        SEED,
        DAILY;
    }

    enum class Availability {
        /**
         * The specified [Feature] is available.
         */
        AVAILABLE,

        /**
         * The specified [Feature] is available, but an update to it is available. This update
         * does not necessarily need to be applied for the feature to remain available.
         */
        UPDATE_AVAILABLE,

        /**
         * The specified [Feature] is available, but not for long. For instance: a new version
         * may be available that will soon deprecate it.
         */
        UPDATE_URGENT,

        /**
         * The specified [Feature] is not available, but an update is available to restore it.
         */
        UPDATE_REQUIRED,

        /**
         * The specified [Feature] has been permanently retired.
         */
        RETIRED,

        /**
         * The specified [Feature] cannot have its availability verified. There may be different
         * reasons for this depending on the feature.
         */
        UNKNOWN;
    }

    interface View: BaseContract.View {

        //region Setup
        //-----------------------------------------------------------------------------------------

        /**
         * Get the Features of interest to the View; the Presenter will not bother checking the
         * status of those Features not listed here, but will (possibly asynchronously) set the
         * availability of any Feature reported.
         */
        fun getFeatures(): Iterable<Feature>

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Actions
        //-----------------------------------------------------------------------------------------

        fun setFeatureAvailability(availability: Map<Feature, Availability>)

        fun setFeatureAvailability(feature: Feature, availability: Availability)

        //-----------------------------------------------------------------------------------------
        //endregion

    }

    interface Presenter: BaseContract.Presenter<View> {

        // anything?

    }
}