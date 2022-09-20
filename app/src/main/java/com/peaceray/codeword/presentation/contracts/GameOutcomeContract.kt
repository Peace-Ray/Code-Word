package com.peaceray.codeword.presentation.contracts

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.data.model.record.PerformanceRecord
import com.peaceray.codeword.data.model.record.PlayerStreak
import com.peaceray.codeword.data.model.record.TotalPerformanceRecord
import java.util.*

interface GameOutcomeContract: BaseContract {

    interface View: BaseContract.View {

        //region Creation Data
        //-----------------------------------------------------------------------------------------

        /**
         * Provide the game UUID passed in to the View upon creation / use.
         *
         * @return The unique ID for the game in question. All other outcome details can be loaded
         * from this value.
         */
        fun getGameUUID(): UUID

        /**
         * Provide the game seed passed in to the View upon creation / use, in any. Optional: even
         * seeded games do not need to provide this value from the View, although doing so may
         * speed up the data load.
         *
         * @return The unique game seed, if it exists and is known.
         */
        fun getGameSeed(): String?

        /**
         * Provide the GameSetup used for this game, if known. Optional: the View is not obligated
         * to know or to provide this value, although doing so may speed up the data load.
         *
         * @return The GameSetup used for this game, if it exists and is known.
         */
        fun getGameSetup(): GameSetup?

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Result Display
        //-----------------------------------------------------------------------------------------

        /**
         * Set the game outcome
         */
        fun setGameOutcome(outcome: GameOutcome)

        /**
         * Set historical information about games of this type
         */
        fun setGameHistory(
            performanceRecord: PerformanceRecord,
            totalPerformanceRecord: TotalPerformanceRecord,
            playerStreak: PlayerStreak?
        )

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Actions
        //-----------------------------------------------------------------------------------------

        /**
         * Share the game result.
         */
        fun share(outcome: GameOutcome)

        /**
         * Copy the game result to clipboard.
         */
        fun copy(outcome: GameOutcome)

        /**
         * Close the results screen
         */
        fun close();

        //-----------------------------------------------------------------------------------------
        //endregion

    }

    interface Presenter: BaseContract.Presenter<View> {

        //region User actions
        //-----------------------------------------------------------------------------------------

        /**
         * The user has clicked a control indicating that they want to share this result,
         * possibly including the game seed.
         */
        fun onShareButtonClicked();

        /**
         * The user has clicked a control indicating that they want to share this result
         * via the device clipboard, possibly including the game seed.
         */
        fun onCopyButtonClicked();

        /**
         * The user has clicked a control indicating that they want to close this screen.
         */
        fun onCloseButtonClicked();

        //-----------------------------------------------------------------------------------------
        //endregion

    }

}