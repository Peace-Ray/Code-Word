package com.peaceray.codeword.presentation.manager.color.impl

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.peaceray.codeword.R
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.datamodel.CodeColorScheme
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.EvaluationColorScheme
import com.peaceray.codeword.presentation.manager.color.ColorSettingsManager
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorManager @Inject constructor(
    @ForApplication private val context: Context,
    @ForApplication private val resources: Resources
): ColorSettingsManager, ColorSwatchManager {

    //region SharedPreferences
    //---------------------------------------------------------------------------------------------
    private companion object {
        const val PREFS_FILENAME = "COLOR_MANAGER_PREFERENCES"
        // night mode
        const val KEY_NIGHT_MODE = "NIGHT_MODE"
        // colors
        const val KEY_EVALUATION_COLORS = "EVALUATION_COLORS"
        const val KEY_CODE_COLORS = "CODE_COLORS"
        const val KEY_CODE_COLORS_INVERTED = "CODE_COLORS_INVERTED"
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    private inline fun <reified T: Enum<T>> SharedPreferences.getEnum(key: String): T? {
        val storedName = getString(key, null)
        return if (storedName == null) null else {
            try {
                enumValueOf<T>(storedName)
            } catch (err: Exception) {
                Timber.e(err, "Persisted $key setting $storedName not recognized")
                null
            }
        }
    }

    private inline fun <reified T: Enum<T>> SharedPreferences.getEnum(key: String, defaultValue: T): T {
        val storedName = getString(key, null)
        return if (storedName == null) defaultValue else {
            try {
                enumValueOf<T>(storedName)
            } catch (err: Exception) {
                Timber.e(err, "Persisted $key setting $storedName not recognized")
                defaultValue
            }
        }
    }

    private inline fun <reified T: Enum<T>> SharedPreferences.Editor.putEnum(
        key: String,
        value: T?
    ): SharedPreferences.Editor {
        putString(key, value?.name)
        return this
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Night Mode
    //---------------------------------------------------------------------------------------------
    override var nightModeSetting: ColorSettingsManager.NightMode
        get() = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> ColorSettingsManager.NightMode.YES
            AppCompatDelegate.MODE_NIGHT_NO -> ColorSettingsManager.NightMode.NO
            else -> ColorSettingsManager.NightMode.DEVICE
        }
        set(value) {
           when(value) {
               ColorSettingsManager.NightMode.YES -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
               ColorSettingsManager.NightMode.NO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
               ColorSettingsManager.NightMode.DEVICE -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
           }
            onNightModeChange()
        }

    override val nightMode: Boolean
        get() = when (nightModeSetting) {
            ColorSettingsManager.NightMode.YES -> true
            ColorSettingsManager.NightMode.NO -> false
            ColorSettingsManager.NightMode.DEVICE -> {
                val deviceSetting = resources.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
                deviceSetting == Configuration.UI_MODE_NIGHT_YES
            }
        }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Color Scheme
    //---------------------------------------------------------------------------------------------
    override var evaluationColorScheme: EvaluationColorScheme
        get() = preferences.getEnum(KEY_EVALUATION_COLORS, EvaluationColorScheme.SEMAPHORE)
        set(value) {
            preferences.edit { putEnum(KEY_EVALUATION_COLORS, value) }
            onColorChange()
        }
    override var codeColorScheme: CodeColorScheme
        get() = preferences.getEnum(KEY_CODE_COLORS, CodeColorScheme.QUANTRO)
        set(value) {
            preferences.edit { putEnum(KEY_CODE_COLORS, value) }
            onColorChange()
        }
    override var codeColorsInverted: Boolean
        get() = preferences.getBoolean(KEY_CODE_COLORS_INVERTED, false)
        set(value) {
            preferences.edit { putBoolean(KEY_CODE_COLORS_INVERTED, value) }
            onColorChange()
        }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Color Swatches
    //---------------------------------------------------------------------------------------------
    private data class ColorSwatchContainerKey(
        val nightMode: Boolean
    )

    private data class ColorSwatchEvaluationKey(
        val nightMode: Boolean,
        val evaluationColors: EvaluationColorScheme
    )

    private data class ColorSwatchCodeKey(
        val nightMode: Boolean,
        val codeColors: CodeColorScheme,
        val codeColorsInverted: Boolean
    )

    private val colorSwatchContainers = mutableMapOf<ColorSwatchContainerKey, ColorSwatch.Container>()
    private val colorSwatchEvaluations = mutableMapOf<ColorSwatchEvaluationKey, ColorSwatch.Evaluation>()
    private val colorSwatchCodes = mutableMapOf<ColorSwatchCodeKey, ColorSwatch.Code>()

    private fun loadSwatch(): ColorSwatch {
        val contKey = ColorSwatchContainerKey(nightMode)
        val evalKey = ColorSwatchEvaluationKey(nightMode, evaluationColorScheme)
        val codeKey = ColorSwatchCodeKey(nightMode, codeColorScheme, codeColorsInverted)

        return ColorSwatch(
            colorSwatchContainers.getOrPut(contKey) {
                // TODO load real color schemes
                ColorSwatch.Container(
                    background = resources.getColor(R.color.white),
                    onBackground = resources.getColor(R.color.gray_600),

                    primary = resources.getColor(R.color.gray_600),
                    primaryVariant = resources.getColor(R.color.gray_800),
                    onPrimary = resources.getColor(R.color.white)
                )
            },
            colorSwatchEvaluations.getOrPut(evalKey) {
                // TODO load real color schemes
                ColorSwatch.Evaluation(
                    untried = resources.getColor(R.color.gray_200),
                    untriedVariant = resources.getColor(R.color.gray_300),
                    onUntried = resources.getColor(R.color.black),

                    exact = resources.getColor(R.color.green_500),
                    exactVariant = resources.getColor(R.color.green_400),
                    onExact = resources.getColor(R.color.white),

                    included = resources.getColor(R.color.amber_300),
                    includedVariant = resources.getColor(R.color.amber_50),
                    onIncluded = resources.getColor(R.color.white),

                    no = resources.getColor(R.color.gray_600),
                    noVariant = resources.getColor(R.color.gray_800),
                    onNo = resources.getColor(R.color.white)
                )
            },
            colorSwatchCodes.getOrPut(codeKey) {
                // TODO load real color schemes
                ColorSwatch.Code(
                    listOf(
                        Color.argb(255, 255, 0, 0),
                        Color.argb(255, 0, 255, 0),
                        Color.argb(255, 0, 0, 255),
                        Color.argb(255, 255, 255, 0),
                        Color.argb(255, 255, 0, 255),
                        Color.argb(255, 0, 255, 255)
                    ),
                    listOf(resources.getColor(R.color.white))
                )
            }
        )
    }

    override var colorSwatch: ColorSwatch = loadSwatch()
        private set(value) {
            field = value
            _colorSwatchLiveData.postValue(value)
        }

    private val _colorSwatchLiveData by lazy { MutableLiveData(colorSwatch) }
    override val colorSwatchLiveData: LiveData<ColorSwatch> by lazy {
        MutableLiveData(colorSwatch)
    }

    fun onColorChange() {
        colorSwatch = loadSwatch()
    }

    private fun onNightModeChange() {
        colorSwatch = loadSwatch()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}