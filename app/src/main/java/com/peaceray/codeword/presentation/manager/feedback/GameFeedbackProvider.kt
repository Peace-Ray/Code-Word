package com.peaceray.codeword.presentation.manager.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.manager.feedback.guess.GuessCreator
import kotlinx.coroutines.flow.Flow

/**
 * The Game at the core of Code Word works by accepting and checking [Constraint]s on each input
 * guess [String]. Constraints represent a complete, per-character comparison between the candidate
 * guess and the underlying secret (or space of potential secrets, solution cloud). However,
 * the guesser is only presented with a portion of this information. Not only do they not know the
 * underlying secret but are not necessarily presented with the by-letter comparison between it
 * and their guesses.
 *
 * The GameFeedbackProvider translates the complete information used by the data layer into a more
 * limited representation conveying only the appropriate subset of that information to the
 * presentation layer. For instance, while a [Constraint] always holds per-character markup, a [Guess]
 * may not convey that information -- only the number of exact and included matches. Further,
 * that information may be obfuscated, e.g. by combining the exact and included matches reported
 * by the Constraint into a single value, as appropriate for the game configuration.
 *
 * Feedback may also integrate information that is not explicitly represented at the [Game] or
 * [Constraint] but is still useful to the user. For example, per-character (not per-guess)
 * information like "best markup revealed" and # of instances. It also incorporates hints:
 * analysis of the possible solution space that is distinct from how a Solver tends to see things.
 * Where a Solver eliminates candidate solutions until one is left, a "hint" provides inferences
 * about solution structure that the guesser may not have made on their own but which can improve
 * their own intuition: for example, that a particular position must be one of two letters, or that
 * a series of whole-word evaluations has revealed that a particular letter must not be part of the
 * solution.
 */
interface GameFeedbackProvider: GuessCreator {

    //region Feedback / Character Feedback
    //---------------------------------------------------------------------------------------------

    /**
     * Given the provided constraints, creates a Feedback object. The object returned is
     * identical to the final Feedback provided by [getFeedbackFlow] (the instance accompanied
     * by a 'true' Boolean value).
     *
     * @param constraints The Constraints to consider in creating the Feedback.
     */
    suspend fun getFeedback(constraints: List<Constraint>): Feedback


    /**
     * Return a Flow which produces Feedback of increasing specificity. Each Pair provided
     * represents a Feedback instance and whether it is the final instance in the Flow.
     * The final such instance, with pair.second == true, is identical to the output of
     * [getFeedback].
     *
     * @param constraints The Constraints to consider in creating the Feedbacks.
     */
    fun getFeedbackFlow(constraints: List<Constraint>): Flow<Pair<Feedback, Boolean>>

    /**
     * Quickly and synchronously generate a Feedback pair to act as a placeholder. The result
     * matches the output of `getFullFeedback(emptyList())` but can be called outside of a
     * coroutine.
     */
    fun getPlaceholderFeedback(): Feedback

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Guess Update Flow
    //---------------------------------------------------------------------------------------------

    /**
     * Returns a list of Guesses created by applying the given Feedback to the provided Constraints.
     *
     * @param constraints The Constraints to represent as Guesses.
     * @param feedback Feedback to apply in constructing hints.
     * @return A list of Guesses resulting from applying Feedback to the Constraints.
     */
    suspend fun toGuesses(constraints: List<Constraint>, feedback: Feedback): List<Guess>

    /**
     * Creates and returns a Flow which provides Guesses created from the indicated Constraints,
     * in order.
     *
     * @param constraints The Constraints to represent as Guesses.
     * @param feedback Feedback to apply in constructing hints.
     * @param reverse Process the Constraints in reverse order (the indices provided as flow emissions
     * will be in descending order).
     * @return A Flow of Pairs giving the list index of a Constraint and the corresponding Guess.
     */
    fun toGuessesFlow(constraints: List<Constraint>, feedback: Feedback, reverse: Boolean = false): Flow<Pair<Int, Guess>>

    //---------------------------------------------------------------------------------------------
    //endregion

}