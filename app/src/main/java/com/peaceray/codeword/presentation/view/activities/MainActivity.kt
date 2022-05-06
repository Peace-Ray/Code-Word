package com.peaceray.codeword.presentation.view.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.ActivityMainBinding
import com.peaceray.codeword.domain.manager.game.GameSessionManager
import com.peaceray.codeword.domain.manager.game.GameSetupManager
import com.peaceray.codeword.presentation.view.fragments.GameFragment
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(), GameFragment.OnInteractionListener {

    //region Creation and Setup
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var gameSetupManager: GameSetupManager
    @Inject lateinit var gameSessionManager: GameSessionManager
    private lateinit var binding: ActivityMainBinding

    // Activity Launchers
    private lateinit var gameSetupLauncher: ActivityResultLauncher<Intent>
    private lateinit var gameInfoLauncher:  ActivityResultLauncher<Intent>

    // State
    private var game: Pair<String?, GameSetup>? = null
    private var isGameOver = false

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

            setNewGameButtonVisible(false)
        }

        setSupportActionBar(binding.toolbar)

        if (savedInstanceState == null) {
            loadGame()
        }

        gameSetupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                val seed = data?.getStringExtra(GameSetupActivity.RESULT_EXTRA_SEED)
                val setup = data?.getParcelableExtra<GameSetup>(GameSetupActivity.RESULT_EXTRA_SETUP)

                if (setup != null) {
                    startGame(seed, setup)
                } else {
                    Timber.e("GameSetupActivity reported RESULT_OK but no GameSetup found. Has seed $seed")
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                setNewGameButtonVisible(isGameOver)
            }
        }

        gameInfoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                val seed = data?.getStringExtra(GameSetupActivity.RESULT_EXTRA_SEED)
                val setup = data?.getParcelableExtra<GameSetup>(GameSetupActivity.RESULT_EXTRA_SETUP)

                if (setup != null) {
                    updateGame(setup)
                } else {
                    Timber.e("GameSetupActivity reported RESULT_OK but no GameSetup found. Has seed $seed")
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                setNewGameButtonVisible(isGameOver)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Add action bar items
        menuInflater.inflate(R.menu.toolbar_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId) {
        R.id.action_puzzle_info -> {
            if (game != null) {
                val intent = GameSetupActivity.getIntentForInfo(this, game!!.first, game!!.second)
                gameInfoLauncher.launch(intent)
            }
            true
        }
        R.id.action_settings -> {
            // TODO add Settings
            Toast.makeText(this, "TODO: add Settings", Toast.LENGTH_SHORT).show()
            true
        }
        R.id.action_app_info -> {
            // TODO open app info
            Toast.makeText(this, "Todo: add App Info", Toast.LENGTH_SHORT).show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Game Launch
    //---------------------------------------------------------------------------------------------
    private fun loadGame() {
        // load or create gameSetup
        Single.defer {
            val loaded = try {
                gameSessionManager.loadSave()
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
            Single.just(game)
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { game -> startGame(game.first, game.second) },
                { err -> Timber.e(err, "An error occurred preparing a game")}
            )
    }

    private fun startGame(seed: String?, gameSetup: GameSetup) {
        Timber.v("Creating GameFragment for seed $seed gameSetup $gameSetup")
        game = Pair(seed, gameSetup)
        isGameOver = false
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(R.id.fragmentContainerView, GameFragment.newInstance(seed, gameSetup))
        }
    }

    private fun updateGame(gameSetup: GameSetup) {
        if (game != null && !isGameOver) {
            Timber.v("Updating GameFragment for seed ${game!!.first} gameSetup ${game!!.second}")
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragmentContainerView, GameFragment.newInstance(game!!.first, game!!.second, gameSetup))
            }
            game = Pair(game!!.first, gameSetup)
        }

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
    override fun onGameOver(
        fragment: GameFragment,
        seed: String?,
        gameSetup: GameSetup,
        solution: String?,
        rounds: Int,
        solved: Boolean,
        playerVictory: Boolean
    ) {
        isGameOver = true
        setNewGameButtonVisible(true)
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}