package com.peaceray.codeword.presentation.view.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.ActivityGameSetupBinding
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.view.fragments.GameFragment
import com.peaceray.codeword.presentation.view.fragments.GameInfoFragment
import com.peaceray.codeword.presentation.view.fragments.GameSetupFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameSetupActivity: AppCompatActivity(), GameSetupFragment.OnInteractionListener, GameInfoFragment.OnInteractionListener {

    //region Creation, Arguments, Result
    //---------------------------------------------------------------------------------------------
    companion object {
        private const val BASE = "GameSetupActivity"
        const val RESULT_EXTRA_SEED = "$BASE.RESULT_SEED"
        const val RESULT_EXTRA_SETUP = "$BASE.RESULT_SETUP"

        const val ARG_GAME_SEED = "$BASE.GAME_SEED"
        const val ARG_GAME_SETUP = "$BASE.GAME_SETUP"

        fun getIntentForSetup(context: Context) = Intent(context, GameSetupActivity::class.java)

        fun getIntentForInfo(context: Context, seed: String?, gameSetup: GameSetup): Intent {
            val intent = Intent(context, GameSetupActivity::class.java)
            intent.putExtra(ARG_GAME_SEED, seed)
            intent.putExtra(ARG_GAME_SETUP, gameSetup)
            return intent
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Creation and Setup
    //---------------------------------------------------------------------------------------------
    private lateinit var binding: ActivityGameSetupBinding
    private var isSetup: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setHomeButtonEnabled(true)

        if (savedInstanceState == null) {
            val seed = intent.getStringExtra(ARG_GAME_SEED)
            val setup = intent.getParcelableExtra<GameSetup>(ARG_GAME_SETUP)
            isSetup = setup == null
            supportFragmentManager.commit {
                val fragment = if (setup == null) {
                    GameSetupFragment.newInstance(GameSetupContract.Type.SEEDED)
                } else {
                    GameInfoFragment.newInstance(seed, setup)
                }

                setReorderingAllowed(true)
                add(R.id.fragmentContainerView, fragment)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Add action bar items
        menuInflater.inflate(R.menu.toolbar_setup, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId) {
        R.id.action_start_puzzle -> {
            // pass to fragment
            if (isSetup) {
                binding.fragmentContainerView.getFragment<GameSetupFragment>().onLaunchButtonClicked()
            } else {
                binding.fragmentContainerView.getFragment<GameInfoFragment>().onLaunchButtonClicked()
            }

            true
        }
        android.R.id.home -> {
            // pass to fragment
            binding.fragmentContainerView.getFragment<GameSetupFragment>().onCancelButtonClicked()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameSetupFragment.OnInteractionListener
    //---------------------------------------------------------------------------------------------
    override fun onSetupFinished(fragment: GameSetupFragment, seed: String?, gameSetup: GameSetup) {
        val intent = Intent()
        intent.putExtra(RESULT_EXTRA_SEED, seed)
        intent.putExtra(RESULT_EXTRA_SETUP, gameSetup)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onSetupCanceled(fragment: GameSetupFragment) {
        setResult(RESULT_CANCELED)
        finish()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameInfoFragment.OnInteractionListener
    //---------------------------------------------------------------------------------------------
    override fun onInfoFinished(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup) {
        val intent = Intent()
        intent.putExtra(RESULT_EXTRA_SEED, seed)
        intent.putExtra(RESULT_EXTRA_SETUP, gameSetup)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onInfoCanceled(fragment: GameInfoFragment) {
        setResult(RESULT_CANCELED)
        finish()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}