package com.peaceray.codeword.presentation.datamodel

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome

data class GameStatusReview(
    val seed: String?,
    val setup: GameSetup,
    val status: Status,
    val purpose: Purpose,
    val notes: Set<Note> = emptySet()
) {

    //region Status
    //---------------------------------------------------------------------------------------------
    enum class Status {
        NEW,
        ONGOING,
        WON,
        LOST,
        LOADING;

        companion object {
            fun from(outcome: GameOutcome.Outcome) = when (outcome) {
                GameOutcome.Outcome.WON -> WON
                GameOutcome.Outcome.LOST -> LOST
                GameOutcome.Outcome.FORFEIT -> ONGOING
                GameOutcome.Outcome.LOADING -> LOADING
            }

            fun from(outcome: GameOutcome): Status {
                return from(outcome.outcome)
            }
        }
    }

    enum class Purpose {
        LAUNCH,
        EXAMINE;
    }

    enum class Note(override val level: Information.Level): Information {
        /**
         * The Seed value used is a legacy value; it is supported but behavior may differ
         * from modern gameplay expectations and not all settings may be available.
         */
        SEED_LEGACY(Information.Level.TIP),

        /**
         * The Seed value used is retired; it is no longer actively supported. An attempt may
         * be made to reproduce this game but it may not be entirely consistent with
         * previous behavior.
         */
        SEED_RETIRED(Information.Level.WARN),

        /**
         * The Seed value used is not fully supported as it comes from (or appears to) a
         * future version of the app. An attempt may be made to reproduce this game but it
         * will almost certainly not be consistent with intended behavior.
         */
        SEED_FUTURISTIC(Information.Level.ERROR),

        /**
         * The Era of this seed (Legacy, Retired, Futuristic, or just normal) cannot yet be determined
         * or a determination is pending.
         */
        SEED_ERA_UNDETERMINED(Information.Level.TIP),

        /**
         * The Seed indicates a timed game that cannot be launched as the time to
         * play it has expired.
         */
        GAME_EXPIRED(Information.Level.ERROR),

        /**
         * The Seed indicates a timed game that cannot be launched as the time to
         * play it has not arrived.
         */
        GAME_FORTHCOMING(Information.Level.ERROR),
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Modification
    //---------------------------------------------------------------------------------------------
    fun with(
        seed: String? = this.seed,
        setup: GameSetup = this.setup,
        status: Status = this.status,
        purpose: Purpose = this.purpose,
        notes: Set<Note> = this.notes
    ) = GameStatusReview(seed, setup, status, purpose, notes)
    //---------------------------------------------------------------------------------------------
    //endregion

}