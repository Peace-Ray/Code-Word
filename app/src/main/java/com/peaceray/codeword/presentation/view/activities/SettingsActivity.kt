package com.peaceray.codeword.presentation.view.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import com.peaceray.codeword.R
import com.peaceray.codeword.databinding.ActivitySettingsBinding
import com.peaceray.codeword.presentation.view.fragments.GameInfoFragment
import com.peaceray.codeword.presentation.view.fragments.GameSetupFragment
import com.peaceray.codeword.presentation.view.fragments.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class SettingsActivity : CodeWordActivity() {
    //region Initiation and Setup
    //---------------------------------------------------------------------------------------------
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar!!.title = getString(R.string.pref_title)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragmentContainerView, SettingsFragment())
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId) {
        android.R.id.home -> {
            // end
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}