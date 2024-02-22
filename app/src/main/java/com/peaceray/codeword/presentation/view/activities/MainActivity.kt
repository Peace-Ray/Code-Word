package com.peaceray.codeword.presentation.view.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat.animate
import androidx.core.view.postDelayed
import androidx.core.view.size
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.ActivityMainBinding
import com.peaceray.codeword.data.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.data.manager.game.setup.GameSetupManager
import com.peaceray.codeword.data.manager.genie.GenieSettingsManager
import com.peaceray.codeword.presentation.manager.tutorial.TutorialManager
import com.peaceray.codeword.presentation.view.fragments.GameFragment
import com.peaceray.codeword.presentation.view.fragments.GameInfoFragment
import com.peaceray.codeword.presentation.view.fragments.dialog.CodeWordDialogFragment
import com.peaceray.codeword.presentation.view.fragments.dialog.GameInfoDialogFragment
import com.peaceray.codeword.presentation.view.fragments.dialog.GameOutcomeDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Exception
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : CodeWordActivity(),
    GameFragment.OnInteractionListener,
    GameInfoFragment.OnInteractionListener,
    GameOutcomeDialogFragment.OnInteractionListener,
    CodeWordDialogFragment.OnLifecycleListener
{

    //region Creation and Setup
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "MainActivity"
        const val ARG_GAME_SEED = "${NAME}_GAME_SEED"
        const val ARG_GAME_SETUP = "${NAME}_GAME_SETUP"
        const val ARG_GAME_UUID = "${NAME}_GAME_UUID"
        const val ARG_IS_GAME_OVER = "${NAME}_IS_GAME_OVER"
    }

    @Inject lateinit var gameSetupManager: GameSetupManager
    @Inject lateinit var gamePersistenceManager: GamePersistenceManager
    @Inject lateinit var tutorialManager: TutorialManager
    @Inject lateinit var genieSettingsManager: GenieSettingsManager
    private lateinit var binding: ActivityMainBinding
    private var menu: Menu? = null

    // Activity Launchers
    private lateinit var gameSetupLauncher: ActivityResultLauncher<Intent>
    private lateinit var gameInfoLauncher:  ActivityResultLauncher<Intent>

    // State
    private var game: Pair<String?, GameSetup>? = null
    private var gameUUID: UUID? = null
    private var isGameOver = false
        set(value) {
            if (field != value) setNewGameButtonVisible(value)
            field = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.newPuzzleButton.visibility = View.GONE
        binding.newPuzzleButton.isClickable = false
        binding.newPuzzleButton.setOnClickListener { view ->
            // open up a "create puzzle" screen
            val intent = GameSetupActivity.getIntentForSetup(this)
            gameSetupLauncher.launch(intent)
        }

        setSupportActionBar(binding.toolbar)

        if (savedInstanceState != null) {
            val seed = savedInstanceState.getString(ARG_GAME_SEED)
            val setup = savedInstanceState.getParcelable<GameSetup>(ARG_GAME_SETUP)
            val uuid = savedInstanceState.getString(ARG_GAME_UUID)
            game = if (setup == null) null else Pair(seed, setup)
            gameUUID = if (uuid == null) null else UUID.fromString(uuid)
            isGameOver = savedInstanceState.getBoolean(ARG_IS_GAME_OVER)
        }

        if (game == null) {
            loadGame()
        }

        gameSetupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Timber.v("game setup: OK")
                // There are no request codes
                val data: Intent? = result.data
                val seed = data?.getStringExtra(GameSetupActivity.RESULT_EXTRA_SEED)
                val setup = data?.getParcelableExtra<GameSetup>(GameSetupActivity.RESULT_EXTRA_SETUP)

                if (setup != null) {
                    startGame(seed, setup)
                } else {
                    Timber.e("GameSetupActivity reported RESULT_OK but no GameSetup found. Has seed $seed")
                }
            }
        }

        gameInfoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.v("gameInfoLauncher: updating game? result code is ${result.resultCode} ok is ${Activity.RESULT_OK}")
            if (result.resultCode == Activity.RESULT_OK) {
                Timber.v("game info: OK")
                // There are no request codes
                val data: Intent? = result.data
                val seed = data?.getStringExtra(GameSetupActivity.RESULT_EXTRA_SEED)
                val setup = data?.getParcelableExtra<GameSetup>(GameSetupActivity.RESULT_EXTRA_SETUP)

                if (setup != null) {
                    updateGame(setup)
                } else {
                    Timber.e("GameSetupActivity reported RESULT_OK but no GameSetup found. Has seed $seed")
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(ARG_GAME_SEED, game?.first)
        outState.putParcelable(ARG_GAME_SETUP, game?.second)
        outState.putString(ARG_GAME_UUID, gameUUID?.toString())
        outState.putBoolean(ARG_IS_GAME_OVER, isGameOver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Keep a reference to the menu
        this.menu = menu
        // Add action bar items
        menuInflater.inflate(R.menu.toolbar_main, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId) {
        R.id.action_set_hinting -> {
            Timber.v("Menu: clicked Set Hinting")
            toggleGameHinting()
            true
        }
        R.id.action_puzzle_info -> {
            Timber.v("Menu: clicked Puzzle Info")
            showPuzzleInfo()
            true
        }
        R.id.action_how_to_play -> {
            Timber.v("Menu: clicked Help / How To Play")
            val currentGame = game
            if (currentGame != null) {
                showHowToPlay(currentGame.first, currentGame.second)
            } else {
                Timber.w("Menu: clicked Help / How To Play but 'game' is null!")
            }
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.action_app_info -> {
            val intent = Intent(this, DocViewerActivity::class.java)
            intent.putExtras(DocViewerActivity.newBundle(document = DocViewerActivity.Documents.APP_INFO))
            startActivity(intent)
            true
        }
        R.id.action_credits -> {
            val intent = Intent(this, DocViewerActivity::class.java)
            intent.putExtras(DocViewerActivity.newBundle(document = DocViewerActivity.Documents.CREDITS))
            startActivity(intent)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Game Info
    //---------------------------------------------------------------------------------------------
    private fun showHowToPlay(seed: String?, gameSetup: GameSetup) {
        showGameInfoDialogFragment(seed, gameSetup, arrayOf(GameInfoFragment.Sections.HOW_TO_PLAY))

        // set tutorialized
        tutorialManager.setExplained(gameSetup)
    }

    private fun showPuzzleInfo() {
        if (game != null && !isGameOver) {
            Timber.v("Puzzle Info: showing setup")
            showGameInfoDialogFragment(
                game!!.first,
                game!!.second,
                arrayOf(
                    GameInfoFragment.Sections.SEED,
                    GameInfoFragment.Sections.PUZZLE_INFO,
                    GameInfoFragment.Sections.CREATE_PUZZLE,
                    GameInfoFragment.Sections.DIFFICULTY
                )
            )
        } else if (isGameOver && gameUUID != null) {
            Timber.v("Puzzle Info: showing outcome")
            val dialogFragment = GameOutcomeDialogFragment.newInstance(gameUUID!!, game?.first, game?.second)
            dialogFragment.show(supportFragmentManager, "dialog")
        } else {
            Timber.w("Puzzle Info: but no Game to show")
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Game Launch
    //---------------------------------------------------------------------------------------------

    private fun loadGame() {
        // TODO Best Practices would limit the creation of Coroutines to the Presenter layer
        // (equiv. the ViewModel layer). However, the MainActivity uses one single asynchronous
        // operation and does not have a Presenter. Apply best practices here by moving this
        // operation to a Presenter, especially if more asynchronous operations are added.
        lifecycleScope.launch {
            val loaded = try {
                gamePersistenceManager.load()
            } catch (err: Exception) {
                Timber.w(err, "An error occurred loading game save")
                null
            }
            val game = if (loaded != null) Pair(loaded.seed, loaded.setup) else {
                val gameSetup = gameSetupManager.getSetup()
                val seed = gameSetupManager.getSeed(gameSetup)
                Timber.v("created setup with seed $seed")
                Pair(seed, gameSetup)
            }
            startGame(game.first, game.second)
        }
    }

    private fun startGame(seed: String?, gameSetup: GameSetup) {
        Timber.v("Creating GameFragment for seed $seed gameSetup $gameSetup")
        game = Pair(seed, gameSetup)
        isGameOver = false

        // forfeit ongoing game, if any
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (fragment is GameFragment) fragment.forfeit()

        // genie announcement(s)?
        if (genieSettingsManager.developerMode) {
            if (gameSetup.vocabulary.secret != null) {
                Toast.makeText(
                    this,
                    getString(R.string.genie_set_secret_toast, gameSetup.vocabulary.secret),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainerView, GameFragment.newInstance(seed, gameSetup))
        }
    }

    private fun updateGame(gameSetup: GameSetup) {
        if (game != null && !isGameOver) {
            Timber.v("Updating GameFragment for seed ${game!!.first} gameSetup ${game!!.second}")

            if (game!!.second == gameSetup) {
                Timber.v("Identical gameSetup: nothing to  update")
            } else {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                val currentGuess = if (fragment is GameFragment) fragment.getCurrentGuess() else null

                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    setCustomAnimations(R.anim.blink_in, R.anim.blink_out)
                    replace(R.id.fragmentContainerView, GameFragment.newInstance(game!!.first, game!!.second, gameSetup, currentGuess).swapIn())
                }
                game = Pair(game!!.first, gameSetup)
            }
        }
    }

    private fun toggleGameHinting() {
        setGameHinting(!gameHinting)
    }

    private fun setGameHinting(on: Boolean) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
        if (fragment is GameFragment) fragment.setHinting(on)
    }

    private fun setNewGameButtonVisible(visible: Boolean) {
        binding.newPuzzleButton.apply {
            // set initial state
            if (visibility == View.GONE) {
                alpha = 0.0f
                isClickable = false
            }

            if (visible) {
                visibility = View.VISIBLE
            }

            animate()
                .alpha(if (visible) 1.0f else 0.0f)
                .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!visible) {
                            visibility = View.GONE
                        } else {
                            isClickable = true
                        }
                    }
                })
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameFragment.OnInteractionListener
    //---------------------------------------------------------------------------------------------
    private var gameHinting: Boolean = false

    override fun onGameStart(fragment: Fragment, seed: String?, gameSetup: GameSetup) {
        if (!tutorialManager.hasExplained(gameSetup)) {
            Timber.v("onGameStart: not tutorialized; showing How To Play for $gameSetup")
            showHowToPlay(seed, gameSetup)
        }
    }

    override fun onGameOver(
        fragment: GameFragment,
        seed: String?,
        gameSetup: GameSetup,
        uuid: UUID,
        solution: String?,
        rounds: Int,
        solved: Boolean,
        playerVictory: Boolean
    ) {
        Timber.v("onGameOver isGameOver $isGameOver uuid $uuid gameUUID $gameUUID isAlive $isAlive solved $solved")
        // update action button colors
        // convention: solved puzzles tend to have the EXACT
        // color near the bottom of the layout, both in the puzzle itself and on the keyboard.
        // To contrast, use the INCLUDED color for the New Puzzle action button.
        val colors = colorSwatchManager.colorSwatch.evaluation.let {
            if (solved) Triple(it.included, it.includedVariant, it.onIncluded)
            else Triple(it.exact, it.exactVariant, it.onExact)
        }

        // TODO: replace this constructor, deprecated in API 23
        binding.newPuzzleButton.backgroundTintList = ColorStateList(arrayOf(
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(0)
        ), intArrayOf(colors.second, colors.first))
        binding.newPuzzleButton.imageTintList = ColorStateList.valueOf(colors.third)

        if (!isGameOver || !uuid.equals(gameUUID)) {
            isGameOver = true
            gameUUID = uuid

            if (isAlive) {
                GameOutcomeDialogFragment.newInstance(
                    uuid,
                    seed,
                    gameSetup
                ).show(supportFragmentManager, "dialog")
            }
        }
    }

    override fun onHintStatusUpdated(on: Boolean, ready: Boolean, supported: Boolean) {
        gameHinting = on
        // update menu settings
        menu?.let {
            for (i in 0 until it.size) {
                val item = it.getItem(i)
                if (item.itemId == R.id.action_set_hinting) {
                    item.title = if (on) getString(R.string.action_set_hinting_is_on) else getString(R.string.action_set_hinting_is_off)
                    item.isEnabled = supported
                    item.icon = ResourcesCompat.getDrawable(resources, if (on) R.drawable.round_lightbulb_on_white_48 else R.drawable.round_lightbulb_off_white_48, theme)
                    item.icon?.alpha = if (supported) 255 else 128
                }
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameInfoFragment: Dialog and Listener
    //---------------------------------------------------------------------------------------------
    private var infoDialogSetup: GameSetup? = null
    private var infoDialogChanged: Boolean = false

    private fun showGameInfoDialogFragment(seed: String?, gameSetup: GameSetup, sections: Array<GameInfoFragment.Sections>) {
        infoDialogChanged = false
        GameInfoDialogFragment.newInstance(game!!.first, game!!.second, sections)
            .show(supportFragmentManager, "dialog")
    }

    private fun updateGameFromGameInfoDialog() {
        if (infoDialogChanged && infoDialogSetup != null) {
            infoDialogChanged = false
            updateGame(infoDialogSetup!!)
        }
    }

    override fun onInfoCreatePuzzleClicked(fragment: GameInfoFragment) {
        // dismiss dialog
        (supportFragmentManager.findFragmentByTag("dialog") as? DialogFragment)?.dismiss()
        // create game
        val intent = GameSetupActivity.getIntentForSetup(this)
        gameSetupLauncher.launch(intent)
    }

    override fun onInfoFinished(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup) {
        // dismiss dialog
        (supportFragmentManager.findFragmentByTag("dialog") as? DialogFragment)?.dismiss()
        // update game
        updateGame(gameSetup)
    }

    override fun onInfoChanged(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup) {
        infoDialogChanged = true
        infoDialogSetup = gameSetup
    }

    override fun onInfoCanceled(fragment: GameInfoFragment) {
        // dismiss dialog
        (supportFragmentManager.findFragmentByTag("dialog") as? DialogFragment)?.dismiss()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameOutcomeDialogFragment: Dialog and Listener
    //---------------------------------------------------------------------------------------------
    override fun onOutcomeCreatePuzzleClicked(fragment: GameOutcomeDialogFragment) {
        // dismiss dialog
        (supportFragmentManager.findFragmentByTag("dialog") as? DialogFragment)?.dismiss()
        // create game
        val intent = GameSetupActivity.getIntentForSetup(this)
        gameSetupLauncher.launch(intent)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region CodeWordDialogFragment.OnLifecycleListener
    //---------------------------------------------------------------------------------------------
    override fun onCancel(dialogFragment: CodeWordDialogFragment) {
        // nothing to do; wait for dismiss
    }

    override fun onDismiss(dialogFragment: CodeWordDialogFragment) {
        when(dialogFragment) {
            is GameInfoDialogFragment -> {
                updateGameFromGameInfoDialog()
            }
            else -> Timber.v("onDismiss from $dialogFragment but nothing to do")
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}