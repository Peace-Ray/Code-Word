package com.peaceray.codeword.presentation.view.fragments.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.peaceray.codeword.R
import com.peaceray.codeword.presentation.manager.color.ColorSettingsManager
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment: PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject lateinit var colorSettingsManager: ColorSettingsManager

    override fun onAttach(context: Context) {
        super.onAttach(context)
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onDetach() {
        super.onDetach()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences != null) {
            // any extra changes that need to happen?
            when (key) {
                // updating Dark Mode automatically changes application theme (full redraw)
                getString(R.string.pref_key_dark_mode) -> {
                    val systemDefault = getString(R.string.pref_entry_value_dark_mode_system)
                    when(sharedPreferences.getString(key, systemDefault)) {
                        getString(R.string.pref_entry_value_dark_mode_off) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        getString(R.string.pref_entry_value_dark_mode_on) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }
                }

                // updating Color Scheme automatically changes application theme (full redraw)
                getString(R.string.pref_key_color_scheme) -> {
                    // nothing to do; redraw is automatic
                }

                // item color changes; push to ColorSwatch listeners
                getString(R.string.pref_key_color_scheme_invert),
                getString(R.string.pref_key_code_colors),
                getString(R.string.pref_key_code_colors_invert),
                getString(R.string.pref_key_code_characters) -> {
                    colorSettingsManager.reloadColors()
                }
            }
        }
    }
}