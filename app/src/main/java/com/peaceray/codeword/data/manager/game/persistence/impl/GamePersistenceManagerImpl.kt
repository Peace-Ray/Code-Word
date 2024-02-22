package com.peaceray.codeword.data.manager.game.persistence.impl

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.data.manager.game.persistence.impl.legacy.V01GameSaveData
import com.peaceray.codeword.data.manager.game.persistence.impl.legacy.V01GameSetup
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.glue.ForLocalIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamePersistenceManagerImpl @Inject constructor(
    @ForApplication val context: Context,
    @ForLocalIO private val ioDispatcher: CoroutineDispatcher,
    @ForComputation private val computationDispatcher: CoroutineDispatcher
): GamePersistenceManager {

    //region GamePersistenceManager Interface
    //---------------------------------------------------------------------------------------------
    private var lastSaveData: GameSaveData? = null
    private val saveMutex = Mutex()

    override suspend fun loadState(seed: String?, setup: GameSetup?): Game.State? {
        // currently no more efficient than [load]
        // TODO: a way to load State _AND_ verify seed / setup matches without full read/deserialization?
        return load(seed, setup)?.state
    }

    override suspend fun load(seed: String?, setup: GameSetup?): GameSaveData? {
        // held in memory
        val lastSave = lastSaveData
        if (isGameSaveData(seed, setup, lastSave)) return lastSave

        // serialization from disk
        val persistedSerialization = withContext(ioDispatcher) {
            saveMutex.withLock { loadSerializedGameSaveDataFromDisk(seed, setup) }
        }

        // deserialize
        return if (persistedSerialization == null) null else withContext(computationDispatcher) {
            val save = deserialize(persistedSerialization)
            if (isGameSaveData(seed, setup, save)) save else null
        }
    }

    override suspend fun save(saveData: GameSaveData) {
        // serialize
        val serialized = withContext(computationDispatcher) { serialize(saveData) }

        // write
        withContext(ioDispatcher) {
            saveMutex.withLock {
                lastSaveData = saveData
                getFilesOnDisk(saveData, extant = false).forEach {
                    Timber.v("saveGame writing game file to ${it.absolutePath}")
                    it.writeText(serialized)
                }
            }
        }
    }

    override suspend fun clearSave(seed: String?, setup: GameSetup) {
        saveMutex.withLock {
            // clear last save if appropriate
            if (isGameSaveData(seed, setup, lastSaveData)) lastSaveData = null

            // load save files from disk
            val filesAndSerializations = withContext(ioDispatcher) {
                getFilesOnDisk(seed, extant = true).map { Pair(it, it.readText()) }
            }

            // filter only those that match input
            val matchingFiles = withContext(computationDispatcher) {
                filesAndSerializations.filter {
                    val save = deserialize(it.second)
                    isGameSaveData(seed, setup, save)
                }
            }

            // delete those that pass
            withContext(ioDispatcher) { matchingFiles.forEach { it.first.delete() } }
        }
    }

    override suspend fun clearSaves() {
        withContext(ioDispatcher) {
            try {
                saveMutex.withLock {
                    lastSaveData = null
                    File(context.filesDir, GAME_SAVE_FILENAME).delete()
                    File(context.filesDir, GAME_RECORD_DIRNAME).deleteRecursively()
                }
            } catch (err: Exception) {
                Timber.e(err, "An error occurred deleting saved games")
            }
        }
    }

    private fun isGameSaveData(seed: String?, setup: GameSetup?, save: GameSaveData?): Boolean {
        return save != null
                && (seed == null || save.seed == seed)
                && (setup == null || save.setup == setup)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region File Access and I/O (use within IO Dispatcher)
    //---------------------------------------------------------------------------------------------
    companion object {
        const val GAME_SAVE_FILENAME = "gameSave.json"
        const val GAME_RECORD_DIRNAME = "gameRecords"
        const val GAME_RECORD_EXT = "json"
    }

    // Do not apply Mutex or CoroutineContexts in these functions

    /**
     * Returns, in order of preference, the Files which may possible contain the save
     * data referenced. Use [extant] to ensure that the files actually exist on disk
     * (extant = false is appropriate for saving the game, which would create those files).
     *
     * Note that the Files themselves are not read, so it is possible that they contain
     * save data unrelated to the specified [GameSaveData]. Load the contents and compare
     * before using.
     */
    private fun getFilesOnDisk(gameSaveData: GameSaveData, extant: Boolean): List<File> {
        return getFilesOnDisk(gameSaveData.seed, extant)
    }

    /**
     * Returns, in order of preference, the Files which may possible contain the save
     * data for the seed referenced. Use [extant] to ensure that the files actually exist on disk
     * (extant = false is appropriate for saving the game, which would create those files).
     *
     * Note that the Files themselves are not read, so it is possible that they contain
     * save data unrelated to the specified seed. Load the contents and compare
     * before using.
     */
    private fun getFilesOnDisk(seed: String?, extant: Boolean): List<File> {
        try {
            val currentFile = File(context.filesDir, GAME_SAVE_FILENAME)
            val fileList = if (seed == null) listOf(currentFile) else {
                val recordDir = File(context.filesDir, GAME_RECORD_DIRNAME)
                if (!recordDir.exists()) recordDir.mkdirs()
                val recordFile = File(recordDir, "${seed.replace('/', '_')}.$GAME_RECORD_EXT")
                listOf(recordFile, currentFile)
            }
            return if (!extant) fileList else fileList.filter { it.exists() }
        } catch (err: SecurityException) {
            Timber.w(err, "Couldn't access game save (seed $seed)")
        } catch (err: FileNotFoundException) {
            Timber.w(err, "Couldn't find game save (seed $seed)")
        }

        return emptyList()
    }

    private fun loadSerializedGameSaveDataFromDisk(seed: String?, setup: GameSetup?): String? {
        return try {
            val fileList = getFilesOnDisk(seed, extant = true)
            if (fileList.isEmpty()) null else {
                Timber.v("loading game file from ${fileList[0].absolutePath}")
                fileList[0].readText()
            }
        } catch (err: SecurityException) {
            Timber.w(err, "Couldn't access game save (seed $seed)")
            null
        } catch (err: FileNotFoundException) {
            Timber.w(err, "Couldn't find game save (seed $seed)")
            null
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Serialization and Legacy Support (use within Computation Dispatcher)
    //---------------------------------------------------------------------------------------------

    // Do not apply Mutex or CoroutineContexts in these functions

    private val gson = Gson()

    private enum class LegacyVersion {
        /**
         * Launch version. Did not support vocabulary filters (e.g. "no letter repetitions").
         * This version was not saved wrapped in VersionedSerialization.
         */
        V01,

        /**
         * Add vocabulary filters and different Constraint types, e.g. "no letter repetitions"
         * and "report Included counts only".
         * First use of VersionedSerialization.
         */
        V02;

        companion object {
            val CURRENT = V02
        }
    }

    private data class VersionedSerialization(
        val version: LegacyVersion,
        val serialization: String
    )

    private fun serialize(saveData: GameSaveData): String {
        val versionedSerialization = VersionedSerialization(
            LegacyVersion.CURRENT,
            gson.toJson(saveData)
        )
        return gson.toJson(versionedSerialization)
    }

    private fun deserialize(text: String): GameSaveData {
        val versionedSerialization = deserializeToVersionedSerialization(text)
        return when(versionedSerialization?.version) {
            LegacyVersion.CURRENT -> gson.fromJson(versionedSerialization.serialization, GameSaveData::class.java)
            null -> deserializeLegacySave(LegacyVersion.V01, text)
            else -> deserializeLegacySave(versionedSerialization.version, versionedSerialization.serialization)
        }
    }

    private fun deserializeToVersionedSerialization(text: String): VersionedSerialization? {
        return try {
            gson.fromJson(text, VersionedSerialization::class.java)
        } catch (error: JsonSyntaxException) {
            null
        }
    }

    private fun deserializeLegacySave(version: LegacyVersion, serialization: String): GameSaveData = when (version) {
        LegacyVersion.V01 -> convertLegacySave(gson.fromJson(serialization, V01GameSaveData::class.java))
        LegacyVersion.V02 -> gson.fromJson(serialization, GameSaveData::class.java)
    }

    private fun convertLegacySave(legacyData: V01GameSaveData): GameSaveData {
        val legacySetup = legacyData.setup
        val gameSetup = GameSetup(
            board = GameSetup.Board(rounds = legacySetup.board.rounds),
            evaluation = GameSetup.Evaluation(
                type = legacySetup.evaluation.type,
                enforced = legacySetup.evaluation.enforced
            ),
            vocabulary = GameSetup.Vocabulary(
                language = legacySetup.vocabulary.language,
                type = when (legacySetup.vocabulary.type) {
                    V01GameSetup.Vocabulary.VocabularyType.LIST -> GameSetup.Vocabulary.VocabularyType.LIST
                    V01GameSetup.Vocabulary.VocabularyType.ENUMERATED -> GameSetup.Vocabulary.VocabularyType.ENUMERATED
                },
                length = legacySetup.vocabulary.length,
                characters = legacySetup.vocabulary.characters,
                characterOccurrences = legacySetup.vocabulary.length,
                secret = legacySetup.vocabulary.secret
            ),
            solver = when (legacySetup.solver) {
                V01GameSetup.Solver.PLAYER -> GameSetup.Solver.PLAYER
                V01GameSetup.Solver.BOT -> GameSetup.Solver.BOT
            },
            evaluator = when (legacySetup.evaluator) {
                V01GameSetup.Evaluator.PLAYER -> GameSetup.Evaluator.PLAYER
                V01GameSetup.Evaluator.HONEST -> GameSetup.Evaluator.HONEST
                V01GameSetup.Evaluator.CHEATER -> GameSetup.Evaluator.CHEATER
            },
            randomSeed = legacySetup.randomSeed,
            daily = legacySetup.daily,
            version = legacySetup.version
        )

        return GameSaveData(
            seed = legacyData.seed,
            setup = gameSetup,
            settings = legacyData.settings,
            constraints = legacyData.constraints,
            currentGuess = legacyData.currentGuess,
            uuid = legacyData.uuid
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}