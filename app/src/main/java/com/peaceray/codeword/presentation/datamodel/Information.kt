package com.peaceray.codeword.presentation.datamodel

/**
 * Information associated with some feature or error condition. For instance, a seed value that is
 * not supported by this version of the app, or that an update will soon be required to continue
 * playing the daily.
 *
 * Recommended use is to define an enum class that implements this interface for each specific
 * set of circumstances or contexts, s.t. any processing of that Warning (such as
 * user display) can fully handle all possible cases. For instance, an Information enum for manually
 * entered Seed values.
 */
interface Information {
    val level: Level

    enum class Level(val priority: Int) {
        /**
         * General information about the application setting or state. Normal operation
         * continues. May not be relevant to the user's intended actions.
         */
        INFO(0),

        /**
         * Specific information about a particular part of the application of interest to the
         * user. For instance, the state of a game or setting they are accessing. The component
         * is working appropriately.
         */
        TIP(1),

        /**
         * Specific information about an application component which may function, but requires
         * intervention or awareness of the user. For instance: a notification that an application
         * update will soon be required for a particular feature to continue functioning.
         */
        WARN(2),

        /**
         * Specific information about an application component which does not function. For instance:
         * a notification that an application update is required for a particular feature to
         * be re-enabled.
         */
        ERROR(3),

        /**
         * General information about the application setting or state. Normal operation is
         * suspended. A large-scale application error has occurred.
         */
        FATAL(4);
    }
}