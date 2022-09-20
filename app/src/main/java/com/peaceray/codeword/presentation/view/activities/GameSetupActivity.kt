package com.peaceray.codeword.presentation.view.activities

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.iterator
import androidx.core.view.postDelayed
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.tabs.TabLayout
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.ActivityGameSetupBinding
import com.peaceray.codeword.domain.manager.genie.GenieGameSetupSettingsManager
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.FeatureAvailabilityContract
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.view.fragments.GameInfoFragment
import com.peaceray.codeword.presentation.view.fragments.GameSetupFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GameSetupActivity:
    CodeWordActivity(),
    FeatureAvailabilityContract.View,
    GameSetupFragment.OnInteractionListener,
    GameInfoFragment.OnInteractionListener
{

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
    private var menu: Menu? = null
    private var isSetup: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val seed = intent.getStringExtra(ARG_GAME_SEED)
        val setup = intent.getParcelableExtra<GameSetup>(ARG_GAME_SETUP)
        isSetup = setup == null

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setTitle(
            if (isSetup) R.string.game_setup_title
            else R.string.game_info_title
        )

        if (savedInstanceState == null) {
            if (!isSetup) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    add(R.id.fragmentContainerView, GameInfoFragment.newInstance(seed, setup!!))
                }
            }
        }

        if (isSetup) {
            addTab(GameSetupContract.Type.DAILY)
            addTab(GameSetupContract.Type.SEEDED)
            addTab(GameSetupContract.Type.CUSTOM)
            binding.tabLayout.addOnTabSelectedListener(tabListener)
            binding.tabLayout.getTabFor(GameSetupContract.Type.SEEDED)?.select()
        } else {
            binding.tabLayout.visibility = View.GONE
        }

        // genie action
        if (genie.allowCustomVersionCheck) {
            setFeatureAvailabilityFromGenie()
        } else {
            // attach to presenter for logic
            attach(presenter)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Retain menu
        this.menu = menu

        // Add and configure action bar items
        menuInflater.inflate(R.menu.toolbar_setup, menu)
        setLaunchButtonEnabled()

        // Done
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
            if (isSetup) {
                binding.fragmentContainerView.getFragment<GameSetupFragment>().onCancelButtonClicked()
            } else {
                binding.fragmentContainerView.getFragment<GameInfoFragment>().onLaunchButtonClicked()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // pass to fragment
        if (isSetup) {
            binding.fragmentContainerView.getFragment<GameSetupFragment>().onCancelButtonClicked()
        } else {
            binding.fragmentContainerView.getFragment<GameInfoFragment>().onLaunchButtonClicked()
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Launch Control
    //---------------------------------------------------------------------------------------------
    private fun setLaunchButtonEnabled(enabled: Boolean? = null) {
        val setEnabled = enabled ?: isTabLaunchButtonEnabled()
        menu?.let {
            for (i in 0 until it.size) {
                val item = it.getItem(i)
                if (item.itemId == R.id.action_start_puzzle) {
                    item.isEnabled = setEnabled
                    // delay "disabled" alpha change to avoid flicker on tab change
                    if (setEnabled) item.icon?.alpha = 255 else {
                        binding.fragmentContainerView.postDelayed(250) {
                            if (!item.isEnabled) item.icon?.alpha = 128
                        }
                    }
                }
            }
        }
    }

    private fun isTabLaunchButtonEnabled(): Boolean {
        val fragment = binding.tabLayout.selectedTabFragment()
        return if (fragment is GameSetupFragment) fragment.isLaunchAvailable() else false
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tabs
    //---------------------------------------------------------------------------------------------
    val fragmentMap: MutableMap<GameSetupContract.Type, GameSetupFragment> = mutableMapOf()

    private fun addTab(type: GameSetupContract.Type, select: Boolean = false): TabLayout.Tab {
        val textId = when(type) {
            GameSetupContract.Type.DAILY -> R.string.game_setup_tab_daily
            GameSetupContract.Type.SEEDED -> R.string.game_setup_tab_seeded
            GameSetupContract.Type.CUSTOM -> R.string.game_setup_tab_custom
            GameSetupContract.Type.ONGOING -> TODO("Allow 'Ongoing' tab?")
        }

        val tab = binding.tabLayout.newTab()
            .setText(textId)
            .setTag(type)

        binding.tabLayout.addTab(tab)
        if (select) tab.select()

        return tab
    }

    private fun TabLayout.getTabFor(type: GameSetupContract.Type): TabLayout.Tab? {
        for (i in 0 until tabCount) {
            val tab = getTabAt(i)
            if (tab?.tag == type) return tab
        }
        return null
    }

    private fun TabLayout.selectedTabFragment(): Fragment? {
        val tab = getTabAt(selectedTabPosition)
        val type = tab?.tag as GameSetupContract.Type?
        if (type != null) {
            return fragmentMap[type]
        }
        return null
    }

    private val tabListener = object: TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            val type = tab?.tag as GameSetupContract.Type?
            if (type != null) {
                var fragment = fragmentMap[type]
                if (fragment == null) {
                    fragment = GameSetupFragment.newInstance(type, gameTypeQualifiers[type])
                    fragmentMap[type] = fragment
                    supportFragmentManager.commit {
                        add(R.id.fragmentContainerView, fragment, type.toString())
                    }
                } else {
                    supportFragmentManager.commit { attach(fragment) }
                }

                setLaunchButtonEnabled(fragment.isLaunchAvailable())
            }
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {
            val fragment = supportFragmentManager.findFragmentByTag(tab?.tag.toString())
            if (fragment != null) {
                supportFragmentManager.commit { detach(fragment) }
            }
        }

        override fun onTabReselected(tab: TabLayout.Tab?) {
            // TODO clear a badge?
        }

    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region FeatureAvailabilityContract.View
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: FeatureAvailabilityContract.Presenter

    @Inject lateinit var genie: GenieGameSetupSettingsManager
    @Inject lateinit var inflater: LayoutInflater

    val gameTypeQualifiers = mutableMapOf(
        Pair(GameSetupContract.Type.DAILY, setOf(GameSetupContract.Qualifier.VERSION_CHECK_PENDING)),
        Pair(GameSetupContract.Type.SEEDED, setOf(GameSetupContract.Qualifier.VERSION_CHECK_PENDING)),
        Pair(GameSetupContract.Type.CUSTOM, setOf(GameSetupContract.Qualifier.VERSION_CHECK_PENDING))
    )
    override fun getFeatures() = listOf(
        FeatureAvailabilityContract.Feature.SEED,
        FeatureAvailabilityContract.Feature.DAILY
    )

    override fun setFeatureAvailability(availability: Map<FeatureAvailabilityContract.Feature, FeatureAvailabilityContract.Availability>) {
        for (feature in availability) {
            setFeatureAvailability(feature.key, feature.value)
        }
    }

    override fun setFeatureAvailability(
        feature: FeatureAvailabilityContract.Feature,
        availability: FeatureAvailabilityContract.Availability
    ) {
        if (genie.allowCustomVersionCheck) return
        applyFeatureAvailability(feature, availability)
    }

    private fun applyFeatureAvailability(
        feature: FeatureAvailabilityContract.Feature,
        availability: FeatureAvailabilityContract.Availability
    ) {
        Timber.v("applyFeatureAvailability $feature $availability")
        when (feature) {
            FeatureAvailabilityContract.Feature.SEED -> {
                // custom updates are only ever "available"
                // seeded updates are never required, even when the minimum is set above current.
                val seededQualifier = when (availability) {
                    FeatureAvailabilityContract.Availability.AVAILABLE -> null
                    FeatureAvailabilityContract.Availability.UPDATE_AVAILABLE,
                    FeatureAvailabilityContract.Availability.UPDATE_URGENT -> GameSetupContract.Qualifier.VERSION_UPDATE_AVAILABLE
                    FeatureAvailabilityContract.Availability.UPDATE_REQUIRED,
                    FeatureAvailabilityContract.Availability.RETIRED -> GameSetupContract.Qualifier.VERSION_UPDATE_RECOMMENDED
                    FeatureAvailabilityContract.Availability.UNKNOWN -> null
                }
                updateVersionQualifier(GameSetupContract.Type.SEEDED, seededQualifier)
                val customQualifier = when (availability) {
                    FeatureAvailabilityContract.Availability.AVAILABLE,
                    FeatureAvailabilityContract.Availability.UPDATE_AVAILABLE,
                    FeatureAvailabilityContract.Availability.UPDATE_URGENT -> null
                    FeatureAvailabilityContract.Availability.UPDATE_REQUIRED,
                    FeatureAvailabilityContract.Availability.RETIRED -> GameSetupContract.Qualifier.VERSION_UPDATE_AVAILABLE
                    FeatureAvailabilityContract.Availability.UNKNOWN -> null
                }
                updateVersionQualifier(GameSetupContract.Type.CUSTOM, customQualifier)
            }
            FeatureAvailabilityContract.Feature.DAILY -> {
                // always recommended to perform an update!
                val qualifier = when(availability) {
                    FeatureAvailabilityContract.Availability.AVAILABLE -> null
                    FeatureAvailabilityContract.Availability.UPDATE_AVAILABLE,
                    FeatureAvailabilityContract.Availability.UPDATE_URGENT -> GameSetupContract.Qualifier.VERSION_UPDATE_RECOMMENDED
                    FeatureAvailabilityContract.Availability.UPDATE_REQUIRED,
                    FeatureAvailabilityContract.Availability.RETIRED -> GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED
                    FeatureAvailabilityContract.Availability.UNKNOWN -> GameSetupContract.Qualifier.VERSION_CHECK_FAILED
                }
                updateVersionQualifier(GameSetupContract.Type.DAILY, qualifier)
            }
            FeatureAvailabilityContract.Feature.APPLICATION -> {
                // nothing to do
            }
        }
    }

    private fun updateVersionQualifier(type: GameSetupContract.Type, qualifier: GameSetupContract.Qualifier?) {
        val qualifiers = if (qualifier == null) emptySet() else setOf(qualifier)
        gameTypeQualifiers[type] = qualifiers
        val fragment = fragmentMap[type]
        if (fragment is GameSetupFragment) {
            fragment.onTypeChanged(type, qualifiers)
        }
    }

    private fun setFeatureAvailabilityFromGenie() {
        val view = inflater.inflate(R.layout.genie_versions_entry, null)
        val dailySpinner = view.findViewById<AppCompatSpinner>(R.id.dailyAvailabilitySpinner)
        val seedSpinner = view.findViewById<AppCompatSpinner>(R.id.seedAvailabilitySpinner)
        val delayEditText = view.findViewById<EditText>(R.id.delay)
        delayEditText.setText("")

        var dailyAvailability: FeatureAvailabilityContract.Availability = FeatureAvailabilityContract.Availability.AVAILABLE
        var seedAvailability: FeatureAvailabilityContract.Availability = FeatureAvailabilityContract.Availability.AVAILABLE

        val spinnerStringToAvailability: (String) -> FeatureAvailabilityContract.Availability = {
            when (it) {
                getString(R.string.genie_set_version_availability_available) ->
                    FeatureAvailabilityContract.Availability.AVAILABLE
                getString(R.string.genie_set_version_availability_update_available) ->
                    FeatureAvailabilityContract.Availability.UPDATE_AVAILABLE
                getString(R.string.genie_set_version_availability_update_urgent) ->
                    FeatureAvailabilityContract.Availability.UPDATE_URGENT
                getString(R.string.genie_set_version_availability_update_required) ->
                    FeatureAvailabilityContract.Availability.UPDATE_REQUIRED
                getString(R.string.genie_set_version_availability_retired) ->
                    FeatureAvailabilityContract.Availability.RETIRED
                getString(R.string.genie_set_version_availability_unknown) ->
                    FeatureAvailabilityContract.Availability.UNKNOWN
                else -> FeatureAvailabilityContract.Availability.UNKNOWN
            }
        }

        dailySpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.genie_set_version_availabilities,
            android.R.layout.simple_spinner_item
        ).also { adapter -> adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        dailySpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val text: String = parent.getItemAtPosition(pos) as String
                dailyAvailability = spinnerStringToAvailability(text)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // nothing to do
            }
        }

        seedSpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.genie_set_version_availabilities,
            android.R.layout.simple_spinner_item
        ).also { adapter -> adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        seedSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val text: String = parent.getItemAtPosition(pos) as String
                seedAvailability = spinnerStringToAvailability(text)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // nothing to do
            }
        }

        val builder = AlertDialog.Builder(this, R.style.ThemeOverlay_CodeWord_AlertDialog)

        builder.setTitle(R.string.genie_set_version_prompt)
        builder.setMessage(R.string.genie_set_version_prompt_detail)

        builder.setView(view)
        builder.setPositiveButton(
            R.string.genie_set_version_confirm,
            object: DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, p1: Int) {
                    val delayText = delayEditText.text.toString()
                    val delay = ((delayText.toFloatOrNull() ?: 0f) * 1000).toLong()

                    view.postDelayed(delay) {
                        applyFeatureAvailability(FeatureAvailabilityContract.Feature.DAILY, dailyAvailability)
                        applyFeatureAvailability(FeatureAvailabilityContract.Feature.SEED, seedAvailability)
                    }

                    dialog?.dismiss()
                }
            }
        )
        builder.setNegativeButton(
            R.string.genie_set_version_cancel,
            object: DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, p1: Int) {
                    dialog?.cancel()
                    applyFeatureAvailability(FeatureAvailabilityContract.Feature.DAILY, FeatureAvailabilityContract.Availability.AVAILABLE)
                    applyFeatureAvailability(FeatureAvailabilityContract.Feature.SEED, FeatureAvailabilityContract.Availability.AVAILABLE)
                }
            }
        )
        builder.setCancelable(true)
        builder.show()
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameSetupFragment.OnInteractionListener
    //---------------------------------------------------------------------------------------------
    override fun onLaunchAvailabilityChanged(fragment: GameSetupFragment, available: Boolean) {
        if (fragment == binding.tabLayout.selectedTabFragment()) {
            setLaunchButtonEnabled(available)
        }
    }

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
    override fun onInfoCreatePuzzleClicked(fragment: GameInfoFragment) {
        TODO("Implement onInfoCreatePuzzleClicked")
    }

    override fun onInfoFinished(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup) {
        val intent = Intent()
        intent.putExtra(RESULT_EXTRA_SEED, seed)
        intent.putExtra(RESULT_EXTRA_SETUP, gameSetup)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onInfoChanged(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup) {
        // nothing to do; wait for [onInfoFinished]
    }

    override fun onInfoCanceled(fragment: GameInfoFragment) {
        setResult(RESULT_CANCELED)
        finish()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}