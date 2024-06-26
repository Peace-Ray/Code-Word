package com.peaceray.codeword.presentation.manager.color.impl

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.peaceray.codeword.R
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.datamodel.CodeColorScheme
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.EvaluationColorScheme
import com.peaceray.codeword.presentation.manager.preferences.AbstractPreferencesManager
import com.peaceray.codeword.presentation.manager.color.ColorSettingsManager
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorManager @Inject constructor(
    @ForApplication context: Context,
    @ForApplication resources: Resources
): ColorSettingsManager, ColorSwatchManager, SharedPreferences.OnSharedPreferenceChangeListener, AbstractPreferencesManager(context, resources) {

    //region SharedPreferences and Preference Mapping
    //---------------------------------------------------------------------------------------------
    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun ColorSettingsManager.DarkModeSetting.toPreferenceValue() = resources.getString(when(this) {
        ColorSettingsManager.DarkModeSetting.YES -> R.string.pref_entry_value_dark_mode_on
        ColorSettingsManager.DarkModeSetting.NO -> R.string.pref_entry_value_dark_mode_off
        ColorSettingsManager.DarkModeSetting.DEVICE -> R.string.pref_entry_value_dark_mode_system
    })

    private fun String.toDarkModeSetting() = when(this) {
        resources.getString(R.string.pref_entry_value_dark_mode_on) -> ColorSettingsManager.DarkModeSetting.YES
        resources.getString(R.string.pref_entry_value_dark_mode_off) -> ColorSettingsManager.DarkModeSetting.NO
        resources.getString(R.string.pref_entry_value_dark_mode_system) -> ColorSettingsManager.DarkModeSetting.DEVICE
        else -> {
            Timber.e("Don't recognize DarkModeSetting entry value $this")
            ColorSettingsManager.DarkModeSetting.DEVICE
        }
    }

    private fun EvaluationColorScheme.toPreferenceValue() = resources.getString(when(this) {
        EvaluationColorScheme.SKY -> R.string.pref_entry_value_color_scheme_sky
        EvaluationColorScheme.SEMAPHORE -> R.string.pref_entry_value_color_scheme_semaphore
        EvaluationColorScheme.DAHLIA -> R.string.pref_entry_value_color_scheme_dahlia
        EvaluationColorScheme.BLAZE -> R.string.pref_entry_value_color_scheme_blaze
        EvaluationColorScheme.GHOST -> R.string.pref_entry_value_color_scheme_ghost
        EvaluationColorScheme.CONTRAST -> R.string.pref_entry_value_color_scheme_contrast
    })

    private fun String.toEvaluationColorScheme() = when(this) {
        resources.getString(R.string.pref_entry_value_color_scheme_sky) -> EvaluationColorScheme.SKY
        resources.getString(R.string.pref_entry_value_color_scheme_semaphore) -> EvaluationColorScheme.SEMAPHORE
        resources.getString(R.string.pref_entry_value_color_scheme_dahlia) -> EvaluationColorScheme.DAHLIA
        resources.getString(R.string.pref_entry_value_color_scheme_blaze) -> EvaluationColorScheme.BLAZE
        resources.getString(R.string.pref_entry_value_color_scheme_ghost) -> EvaluationColorScheme.GHOST
        resources.getString(R.string.pref_entry_value_color_scheme_contrast) -> EvaluationColorScheme.CONTRAST
        else -> {
            Timber.e("Don't recognize EvaluationColorScheme entry value $this")
            EvaluationColorScheme.SEMAPHORE
        }
    }

    private fun CodeColorScheme.toPreferenceValue() = resources.getString(when(this) {
        CodeColorScheme.SUNRISE -> R.string.pref_entry_value_code_colors_sunrise
        CodeColorScheme.SUNSET -> R.string.pref_entry_value_code_colors_sunset
        CodeColorScheme.HORIZON -> R.string.pref_entry_value_code_colors_horizon
        CodeColorScheme.PLAYROOM -> R.string.pref_entry_value_code_colors_playroom
        CodeColorScheme.PRIMARY -> R.string.pref_entry_value_code_colors_primary
        CodeColorScheme.QUANTRO -> R.string.pref_entry_value_code_colors_quantro
        CodeColorScheme.RETRO -> R.string.pref_entry_value_code_colors_retro
        CodeColorScheme.PLAIN -> R.string.pref_entry_value_code_colors_plain
        CodeColorScheme.PASTELS -> R.string.pref_entry_value_code_colors_pastels
        CodeColorScheme.PRIDE -> R.string.pref_entry_value_code_colors_pride
    })

    private fun String.toCodeColorScheme() = when(this) {
        resources.getString(R.string.pref_entry_value_code_colors_sunrise) -> CodeColorScheme.SUNRISE
        resources.getString(R.string.pref_entry_value_code_colors_sunset) -> CodeColorScheme.SUNSET
        resources.getString(R.string.pref_entry_value_code_colors_horizon) -> CodeColorScheme.HORIZON
        resources.getString(R.string.pref_entry_value_code_colors_playroom) -> CodeColorScheme.PLAYROOM
        resources.getString(R.string.pref_entry_value_code_colors_primary) -> CodeColorScheme.PRIMARY
        resources.getString(R.string.pref_entry_value_code_colors_quantro) -> CodeColorScheme.QUANTRO
        resources.getString(R.string.pref_entry_value_code_colors_retro) -> CodeColorScheme.RETRO
        resources.getString(R.string.pref_entry_value_code_colors_plain) -> CodeColorScheme.PLAIN
        resources.getString(R.string.pref_entry_value_code_colors_pastels) -> CodeColorScheme.PASTELS
        resources.getString(R.string.pref_entry_value_code_colors_pride) -> CodeColorScheme.PRIDE
        else -> {
            Timber.e("Don't recognize CodeColorScheme entry value $this")
            CodeColorScheme.PRIMARY
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Timber.d("onSharedPreferenceChanged $key")
        when(key) {
            resources.getString(R.string.pref_key_dark_mode),
            resources.getString(R.string.pref_key_color_scheme),
            resources.getString(R.string.pref_key_color_scheme_invert),
            resources.getString(R.string.pref_key_code_colors),
            resources.getString(R.string.pref_key_code_colors_invert),
            resources.getString(R.string.pref_key_code_characters) -> onColorChange()
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Night Mode
    //---------------------------------------------------------------------------------------------
    override val darkModeSetting: ColorSettingsManager.DarkModeSetting
        get() = preferences.getString(R.string.pref_key_dark_mode)?.toDarkModeSetting()
            ?: ColorSettingsManager.DarkModeSetting.DEVICE

    override val darkMode: Boolean
        get() = when (darkModeSetting) {
            ColorSettingsManager.DarkModeSetting.YES -> true
            ColorSettingsManager.DarkModeSetting.NO -> false
            ColorSettingsManager.DarkModeSetting.DEVICE -> {
                val deviceSetting = resources.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
                deviceSetting == Configuration.UI_MODE_NIGHT_YES
            }
        }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Color Scheme
    //---------------------------------------------------------------------------------------------
    override var evaluationColorScheme: EvaluationColorScheme
        get() = preferences.getString(R.string.pref_key_color_scheme)?.toEvaluationColorScheme()
            ?: EvaluationColorScheme.SKY
        set(value) {
            preferences.edit { putString(R.string.pref_key_color_scheme, value.toPreferenceValue()) }
            onColorChange()
        }
    override var evaluationColorsInverted: Boolean
        get() = preferences.getBoolean(R.string.pref_key_color_scheme_invert, false)
        set(value) {
            preferences.edit { putBoolean(R.string.pref_key_color_scheme_invert, value) }
            onColorChange()
        }
    override var codeColorScheme: CodeColorScheme
        get() = preferences.getString(R.string.pref_key_code_colors)?.toCodeColorScheme()
            ?: CodeColorScheme.PRIMARY
        set(value) {
            preferences.edit { putString(R.string.pref_key_code_colors, value.toPreferenceValue()) }
            onColorChange()
        }

    override var codeColorsReversed: Boolean
        get() = preferences.getBoolean(R.string.pref_key_code_colors_reverse, false)
        set(value) {
            preferences.edit { putBoolean(R.string.pref_key_code_colors_reverse, value) }
            onColorChange()
        }

    override var codeColorsInverted: Boolean
        get() = preferences.getBoolean(R.string.pref_key_code_colors_invert, false)
        set(value) {
            preferences.edit { putBoolean(R.string.pref_key_code_colors_invert, value) }
            onColorChange()
        }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Updates
    //---------------------------------------------------------------------------------------------
    /**
     * It's possible that colors have been updated at an alternative source. Reload from
     * that source, potentially updating property values.
     */
    override fun reloadColors() {
        onColorChange()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Color Swatches
    //---------------------------------------------------------------------------------------------
    private val colorSwatchContainers = mutableMapOf<ColorSwatchContainerKey, ColorSwatch.Container>()
    private val colorSwatchInformations = mutableMapOf<ColorSwatchInformationKey, ColorSwatch.InformationSet>()
    private val colorSwatchEvaluations = mutableMapOf<ColorSwatchEvaluationKey, ColorSwatch.Evaluation>()
    private val colorSwatchEmojiSets = mutableMapOf<ColorSwatchEmojiKey, ColorSwatch.EmojiSet>()
    private val colorSwatchCodes = mutableMapOf<ColorSwatchCodeKey, ColorSwatch.Code>()

    private fun loadSwatch(): ColorSwatch {
        val themKey = ColorSwatchThemeKey(evaluationColorScheme)
        val contKey = ColorSwatchContainerKey(darkMode)
        val infoKey = ColorSwatchInformationKey(darkMode, evaluationColorScheme)
        val evalKey = ColorSwatchEvaluationKey(darkMode, evaluationColorScheme, evaluationColorsInverted)
        val emojKey = ColorSwatchEmojiKey(darkMode, evaluationColorScheme, evaluationColorsInverted)
        val codeKey = ColorSwatchCodeKey(darkMode, codeColorScheme, codeColorsReversed, codeColorsInverted)

        Timber.d("loadSwatch for color scheme $evaluationColorScheme inverted $evaluationColorsInverted dark mode $darkMode")

        return ColorSwatch(
            create(themKey),
            colorSwatchContainers.getOrPut(contKey) { create(contKey) },
            colorSwatchInformations.getOrPut(infoKey) { create(infoKey) },
            colorSwatchEvaluations.getOrPut(evalKey) { create(evalKey) },
            colorSwatchEmojiSets.getOrPut(emojKey) { create(emojKey) },
            colorSwatchCodes.getOrPut(codeKey) { create(codeKey )}
        )
    }

    override var colorSwatch: ColorSwatch = loadSwatch()
        private set(value) {
            field = value
            Timber.d("postValue to colorSwatch liveData")
            _colorSwatchLiveData.postValue(value)
        }

    private val _colorSwatchLiveData by lazy { MutableLiveData(colorSwatch) }
    override val colorSwatchLiveData: LiveData<ColorSwatch> by lazy { _colorSwatchLiveData }

    private fun onColorChange() {
        colorSwatch = loadSwatch()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Swatch Creation
    //---------------------------------------------------------------------------------------------
    private data class ColorSwatchThemeKey(
        // "darkMode" should be automatically applied by application framework
        val evaluationColors: EvaluationColorScheme
    )

    @StyleRes private fun create(key: ColorSwatchThemeKey): Int {
        return when(key.evaluationColors) {
            EvaluationColorScheme.SKY -> R.style.Theme_CodeWord_Sky
            EvaluationColorScheme.SEMAPHORE -> R.style.Theme_CodeWord_Semaphore
            EvaluationColorScheme.DAHLIA -> R.style.Theme_CodeWord_Dahlia
            EvaluationColorScheme.BLAZE -> R.style.Theme_CodeWord_Blaze
            EvaluationColorScheme.GHOST -> R.style.Theme_CodeWord_Ghost
            EvaluationColorScheme.CONTRAST -> R.style.Theme_CodeWord_Contrast
        }
    }

    private data class ColorSwatchContainerKey(
        val darkMode: Boolean
    )

    private fun create(key: ColorSwatchContainerKey): ColorSwatch.Container {
        return if (key.darkMode) {
            ColorSwatch.Container(
                background = getColor(R.color.md_grey_900),
                onBackground = getColor(R.color.white),
                onBackgroundAccent = getColor(R.color.black, alpha = 40), // getColor(R.color.none),

                primary = getColor(R.color.md_grey_900),
                primaryVariant = getColor(R.color.md_grey_600),
                onPrimary = getColor(R.color.white)
            )
        } else {
            ColorSwatch.Container(
                background = getColor(R.color.white),
                onBackground = getColor(R.color.md_grey_600),
                onBackgroundAccent = getColor(R.color.black, alpha = 40),

                primary = getColor(R.color.md_grey_600),
                primaryVariant = getColor(R.color.md_grey_800),
                onPrimary = getColor(R.color.white)
            )
        }
    }

    private data class ColorSwatchInformationKey(
        val darkMode: Boolean,
        val evaluationColors: EvaluationColorScheme
    )

    private fun create(key: ColorSwatchInformationKey): ColorSwatch.InformationSet {
        // create color-independent placeholders based on darkMode
        var onBackground: ColorSwatch.Information
        var onPrimary: ColorSwatch.Information

        if (key.darkMode) {
            onBackground = ColorSwatch.Information(
                getColor(R.color.white),
                getColor(R.color.md_grey_200)
            )
            onPrimary = onBackground
        } else {
            onBackground = ColorSwatch.Information(
                getColor(R.color.md_grey_600),
                getColor(R.color.md_grey_500)
            )
            onPrimary = ColorSwatch.Information(
                getColor(R.color.white),
                getColor(R.color.md_grey_100)
            )
        }

        // higher info levels have brighter colors, which may be color-scheme dependent. e.g.:
        // dark mode: warn = included, error = includedVariant, fatal = bold red (or other "distinct" color)
        // light mode: warn = includedVariant, error = red, fatal = bold red (or other "distinct" color)
        // treat this as 4 color steps, with "darkMode" determining which 3 are used.
        // note that in Light mode, the "included" color is used for other interactables, so
        val fullColorSteps: List<Int>
        when (key.evaluationColors) {
            EvaluationColorScheme.SKY -> {
                fullColorSteps = listOf(
                    getColor(R.color.md_amber_300),
                    getColor(R.color.md_amber_500),
                    getColor(R.color.md_orange_500),
                    getColor(R.color.md_red_500)
                )
            }
            EvaluationColorScheme.SEMAPHORE -> {
                fullColorSteps = listOf(
                    getColor(R.color.md_amber_300),
                    getColor(R.color.md_amber_500),
                    getColor(R.color.md_orange_500),
                    getColor(R.color.md_red_500)
                )
            }
            EvaluationColorScheme.DAHLIA -> {
                fullColorSteps = listOf(
                    getColor(R.color.md_pink_A100),
                    getColor(R.color.md_pink_A200),
                    getColor(R.color.md_pink_A400),
                    getColor(R.color.md_red_500)
                )
            }
            EvaluationColorScheme.BLAZE -> {
                fullColorSteps = listOf(
                    getColor(R.color.md_deep_orange_600),
                    getColor(R.color.md_deep_orange_800),
                    getColor(R.color.md_light_blue_A200),
                    getColor(R.color.md_light_blue_A400)
                )
            }
            EvaluationColorScheme.GHOST -> {
                fullColorSteps = listOf(
                    getColor(R.color.md_blue_300),
                    getColor(R.color.md_blue_A200),
                    getColor(R.color.md_purple_A200),
                    getColor(R.color.md_purple_A400)
                )
            }
            EvaluationColorScheme.CONTRAST -> {
                fullColorSteps = listOf(
                    getColor(R.color.md_light_blue_A200),
                    getColor(R.color.md_light_blue_A400),
                    getColor(R.color.md_cyan_A400),
                    getColor(R.color.md_cyan_A200)
                )
            }
        }

        val colorSteps = if (key.darkMode) {
            listOf(fullColorSteps[0], fullColorSteps[1], fullColorSteps[3])
        } else {
            listOf(fullColorSteps[1], fullColorSteps[2], fullColorSteps[3])
        }

        onBackground = onBackground.copy(
            warn = colorSteps[0],
            error = colorSteps[1],
            fatal = colorSteps[2]
        )

        onPrimary = onPrimary.copy(
            warn = colorSteps[0],
            error = colorSteps[1],
            fatal = colorSteps[2]
        )

        return ColorSwatch.InformationSet(onBackground = onBackground, onPrimary = onPrimary)
    }

    private data class ColorSwatchEvaluationKey(
        val darkMode: Boolean,
        val evaluationColors: EvaluationColorScheme,
        val evaluationColorsInverted: Boolean
    )

    private fun create(key: ColorSwatchEvaluationKey): ColorSwatch.Evaluation {
        // create a basic, non-inverted scheme
        val baseScheme = when(key.evaluationColors) {
            EvaluationColorScheme.SKY -> if (key.darkMode) {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_500),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.white),

                    exact = getColor(R.color.md_light_blue_300),
                    exactVariant = getColor(R.color.md_light_blue_500),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_amber_300),
                    includedVariant = getColor(R.color.md_amber_500),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_grey_800),
                    noVariant = getColor(R.color.md_grey_600),
                    onNo = getColor(R.color.white)
                )
            } else {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_300),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_light_blue_300),
                    exactVariant = getColor(R.color.md_light_blue_500),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_amber_300),
                    includedVariant = getColor(R.color.md_amber_500),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_grey_200),
                    noVariant = getColor(R.color.md_grey_200),
                    onNo = getColor(R.color.md_grey_400)
                )
            }
            EvaluationColorScheme.SEMAPHORE -> if (key.darkMode) {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_500),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.white),

                    exact = getColor(R.color.md_green_500),
                    exactVariant = getColor(R.color.md_green_700),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_amber_300),
                    includedVariant = getColor(R.color.md_amber_500),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_grey_800),
                    noVariant = getColor(R.color.md_grey_600),
                    onNo = getColor(R.color.white)
                )
            } else {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_200),
                    untriedVariant = getColor(R.color.md_grey_300),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_green_500),
                    exactVariant = getColor(R.color.md_green_700),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_amber_300),
                    includedVariant = getColor(R.color.md_amber_500),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_grey_600),
                    noVariant = getColor(R.color.md_grey_800),
                    onNo = getColor(R.color.white)
                )
            }
            EvaluationColorScheme.DAHLIA -> if (key.darkMode) {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_500),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.white),

                    exact = getColor(R.color.md_pink_A100),
                    exactVariant = getColor(R.color.md_pink_A200),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_yellow_A100),
                    includedVariant = getColor(R.color.md_yellow_A200),
                    onIncluded = getColor(R.color.md_yellow_700),

                    no = getColor(R.color.md_grey_200),
                    noVariant = getColor(R.color.md_grey_200),
                    onNo = getColor(R.color.md_grey_400)
                )
            } else {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_300),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_pink_A100),
                    exactVariant = getColor(R.color.md_pink_A200),
                    onExact = getColor(R.color.md_pink_50),

                    included = getColor(R.color.md_yellow_A100),
                    includedVariant = getColor(R.color.md_yellow_A200),
                    onIncluded = getColor(R.color.md_yellow_700),

                    no = getColor(R.color.md_grey_200),
                    noVariant = getColor(R.color.md_grey_200),
                    onNo = getColor(R.color.md_grey_400)
                )
            }
            EvaluationColorScheme.BLAZE -> if (key.darkMode) {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_200),
                    untriedVariant = getColor(R.color.md_grey_300),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_amber_A200),
                    exactVariant = getColor(R.color.md_amber_A400),
                    onExact = getColor(R.color.black),

                    included = getColor(R.color.md_deep_orange_500),
                    includedVariant = getColor(R.color.md_deep_orange_600),
                    onIncluded = getColor(R.color.md_brown_800),

                    no = getColor(R.color.md_brown_200),
                    noVariant = getColor(R.color.md_brown_400),
                    onNo = getColor(R.color.md_brown_800)
                )
            } else {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_200),
                    untriedVariant = getColor(R.color.md_grey_300),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_amber_300),
                    exactVariant = getColor(R.color.md_amber_500),
                    onExact = getColor(R.color.black),

                    included = getColor(R.color.md_deep_orange_800),
                    includedVariant = getColor(R.color.md_deep_orange_600),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_brown_900), //getColor(R.color.md_grey_800),
                    noVariant = getColor(R.color.md_brown_800), //getColor(R.color.md_grey_900),
                    onNo = getColor(R.color.white)
                )
            }
            EvaluationColorScheme.GHOST -> if (key.darkMode) {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_400),
                    untriedVariant = getColor(R.color.md_grey_600),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_purple_A200),
                    exactVariant = getColor(R.color.md_purple_A700),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_blue_100),
                    includedVariant = getColor(R.color.md_blue_300),
                    onIncluded = getColor(R.color.md_grey_700),

                    no = getColor(R.color.md_grey_700),
                    noVariant = getColor(R.color.md_grey_900),
                    onNo = getColor(R.color.md_grey_300)
                )
            } else {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_300),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.md_grey_700),

                    exact = getColor(R.color.md_purple_A200),
                    exactVariant = getColor(R.color.md_purple_A700),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_blue_100),
                    includedVariant = getColor(R.color.md_blue_300),
                    onIncluded = getColor(R.color.md_grey_700),

                    no = getColor(R.color.md_grey_50),
                    noVariant = getColor(R.color.md_grey_200),
                    onNo = getColor(R.color.md_grey_400)
                )
            }
            EvaluationColorScheme.CONTRAST -> if (key.darkMode) {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_500),
                    untriedVariant = getColor(R.color.md_grey_400),
                    onUntried = getColor(R.color.white),

                    exact = getColor(R.color.md_deep_orange_500),
                    exactVariant = getColor(R.color.md_deep_orange_700),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_light_blue_A200),
                    includedVariant = getColor(R.color.md_light_blue_A400),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_grey_800),
                    noVariant = getColor(R.color.md_grey_600),
                    onNo = getColor(R.color.white)
                )
            } else {
                ColorSwatch.Evaluation(
                    untried = getColor(R.color.md_grey_200),
                    untriedVariant = getColor(R.color.md_grey_300),
                    onUntried = getColor(R.color.black),

                    exact = getColor(R.color.md_deep_orange_500),
                    exactVariant = getColor(R.color.md_deep_orange_700),
                    onExact = getColor(R.color.white),

                    included = getColor(R.color.md_light_blue_A200),
                    includedVariant = getColor(R.color.md_light_blue_A400),
                    onIncluded = getColor(R.color.white),

                    no = getColor(R.color.md_grey_600),
                    noVariant = getColor(R.color.md_grey_800),
                    onNo = getColor(R.color.white)
                )
            }
        }

        // invert if necessary
        return if (!key.evaluationColorsInverted) baseScheme else ColorSwatch.Evaluation(
            untried = baseScheme.untried,
            untriedVariant = baseScheme.untriedVariant,
            onUntried = baseScheme.onUntried,

            exact = baseScheme.included,
            exactVariant = baseScheme.includedVariant,
            onExact = baseScheme.onExact,

            included = baseScheme.exact,
            includedVariant = baseScheme.exactVariant,
            onIncluded = baseScheme.onExact,

            no = baseScheme.no,
            noVariant = baseScheme.noVariant,
            onNo = baseScheme.onNo
        )
    }

    private data class ColorSwatchEmojiKey(
        val darkMode: Boolean,
        val evaluationColors: EvaluationColorScheme,
        val evaluationColorsInverted: Boolean
    )

    private fun create(key: ColorSwatchEmojiKey): ColorSwatch.EmojiSet {
        // a basic set, not inverted
        val baseSet = when(key.evaluationColors) {
            EvaluationColorScheme.SKY -> ColorSwatch.EmojiSet(
                positioned = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⬛" else "⬜",
                    exact = "\uD83D\uDFE6",  // blue square
                    included = "\uD83D\uDFE8",  // yellow square
                    no = if (key.darkMode) "⬛" else "⬜",
                ),
                aggregated = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⚫" else "⚪",
                    exact = "\uD83D\uDD35",  // blue circle
                    included = "\uD83D\uDFE1",  // yellow circle
                    no = if (key.darkMode) "⚫" else "⚪",
                )
            )
            EvaluationColorScheme.SEMAPHORE -> ColorSwatch.EmojiSet(
                positioned = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⬛" else "⬜",
                    exact = "\uD83D\uDFE9",     // green square
                    included = "\uD83D\uDFE8",  // yellow square
                    no = if (key.darkMode) "⬛" else "⬜",
                ),
                aggregated = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⚫" else "⚪",
                    exact = "\uD83D\uDFE2",     // green circle
                    included = "\uD83D\uDFE1",  // yellow circle
                    no = if (key.darkMode) "⚫" else "⚪",
                )
            )
            EvaluationColorScheme.DAHLIA -> ColorSwatch.EmojiSet(
                positioned = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⬛" else "⬜",
                    exact = "\uD83D\uDFEA",     // purple square
                    included = "\uD83D\uDFE8",  // yellow square
                    no = if (key.darkMode) "⬛" else "⬜",
                ),
                aggregated = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⚫" else "⚪",
                    exact = "\uD83D\uDFE3",     // purple circle
                    included = "\uD83D\uDFE1",  // yellow circle
                    no = if (key.darkMode) "⚫" else "⚪",
                )
            )
            EvaluationColorScheme.BLAZE -> ColorSwatch.EmojiSet(
                positioned = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⬛" else "⬜",
                    exact = "\uD83D\uDFE8",  // yellow square
                    included = "\uD83D\uDFE5",  // red square
                    no = if (key.darkMode)  "⬜" else "⬛"
                ),
                aggregated = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⚫" else "⚪",
                    exact = "\uD83D\uDFE1",     // yellow circle
                    included = "\uD83D\uDD34",     // red circle
                    no = if (key.darkMode)  "⚪" else "⚫",
                )
            )
            EvaluationColorScheme.GHOST -> ColorSwatch.EmojiSet(
                positioned = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⬛" else "⬜",
                    exact = "\uD83D\uDFEA",     // purple square
                    included = "\uD83D\uDFE6",  // blue square
                    no = if (key.darkMode) "⬛" else "⬜",
                ),
                aggregated = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⚫" else "⚪",
                    exact = "\uD83D\uDFE3",     // purple circle
                    included = "\uD83D\uDD35",  // blue circle
                    no = if (key.darkMode) "⚫" else "⚪",
                )
            )
            EvaluationColorScheme.CONTRAST -> ColorSwatch.EmojiSet(
                positioned = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⬛" else "⬜",
                    exact = "\uD83D\uDFE7",     // orange square
                    included = "\uD83D\uDFE6",  // blue square
                    no = if (key.darkMode) "⬛" else "⬜",
                ),
                aggregated = ColorSwatch.Emoji(
                    untried = if (key.darkMode) "⚫" else "⚪",
                    exact = "\uD83D\uDFE0",     // orange circle
                    included = "\uD83D\uDD35",  // blue circle
                    no = if (key.darkMode) "⚫" else "⚪",
                )
            )
        }

        // invert evaluation colors if necessary
        return if (!key.evaluationColorsInverted) baseSet else ColorSwatch.EmojiSet(
            positioned = ColorSwatch.Emoji(
                untried = baseSet.positioned.untried,
                exact = baseSet.positioned.included,
                included = baseSet.positioned.exact,
                no = baseSet.positioned.no
            ),
            aggregated = ColorSwatch.Emoji(
                untried = baseSet.aggregated.untried,
                exact = baseSet.aggregated.included,
                included = baseSet.aggregated.exact,
                no = baseSet.aggregated.no
            )
        )
    }

    private data class ColorSwatchCodeKey(
        val darkMode: Boolean,
        val codeColors: CodeColorScheme,
        val codeColorsReversed: Boolean,
        val codeColorsInverted: Boolean
    )

    private fun create(key: ColorSwatchCodeKey): ColorSwatch.Code {
        val baseCodes: List<Int>
        val baseOnCodes: List<Int>

        when (key.codeColors) {
            CodeColorScheme.SUNRISE -> {
                baseCodes = listOf(
                    getColor(R.color.cc_sunrise_pink),
                    getColor(R.color.cc_sunrise_red),
                    getColor(R.color.cc_sunrise_peach),
                    getColor(R.color.cc_sunrise_orange),
                    getColor(R.color.cc_sunrise_yellow),
                    getColor(R.color.cc_sunrise_light_pink),
                    getColor(R.color.cc_sunrise_light_red),
                    getColor(R.color.cc_sunrise_light_peach),
                    getColor(R.color.cc_sunrise_light_orange),
                    getColor(R.color.cc_sunrise_light_yellow),
                    getColor(R.color.cc_sunrise_dark_pink),
                    getColor(R.color.cc_sunrise_dark_red),
                    getColor(R.color.cc_sunrise_dark_peach),
                    getColor(R.color.cc_sunrise_dark_orange),
                    getColor(R.color.cc_sunrise_dark_yellow),
                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.SUNSET -> {
                baseCodes = listOf(
                    getColor(R.color.cc_sunset_deep_purple),
                    getColor(R.color.cc_sunset_purple),
                    getColor(R.color.cc_sunset_magenta),
                    getColor(R.color.cc_sunset_pink),
                    getColor(R.color.cc_sunset_blush),
                    getColor(R.color.cc_sunset_light_deep_purple),
                    getColor(R.color.cc_sunset_light_purple),
                    getColor(R.color.cc_sunset_light_magenta),
                    getColor(R.color.cc_sunset_light_pink),
                    getColor(R.color.cc_sunset_light_blush),
                    getColor(R.color.cc_sunset_dark_deep_purple),
                    getColor(R.color.cc_sunset_dark_purple),
                    getColor(R.color.cc_sunset_dark_magenta),
                    getColor(R.color.cc_sunset_dark_pink),
                    getColor(R.color.cc_sunset_dark_blush),
                    )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.HORIZON -> {
                baseCodes = listOf(
                    getColor(R.color.cc_sunrise_yellow),
                    getColor(R.color.cc_sunrise_orange),
                    getColor(R.color.cc_sunrise_peach),
                    getColor(R.color.cc_sunrise_red),
                    getColor(R.color.cc_sunrise_pink),
                    getColor(R.color.cc_sunset_blush),
                    getColor(R.color.cc_sunset_pink),
                    getColor(R.color.cc_sunset_magenta),
                    getColor(R.color.cc_sunset_purple),
                    getColor(R.color.cc_sunset_deep_purple),
                    getColor(R.color.cc_sunrise_light_yellow),
                    getColor(R.color.cc_sunrise_light_orange),
                    getColor(R.color.cc_sunrise_light_peach),
                    getColor(R.color.cc_sunrise_light_red),
                    getColor(R.color.cc_sunrise_light_pink),
                    getColor(R.color.cc_sunset_light_blush),
                    getColor(R.color.cc_sunset_light_pink),
                    getColor(R.color.cc_sunset_light_magenta),
                    getColor(R.color.cc_sunset_light_purple),
                    getColor(R.color.cc_sunset_light_deep_purple),
                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.PLAYROOM -> {
                baseCodes = listOf(
                    getColor(R.color.cc_playroom_teal),
                    getColor(R.color.cc_playroom_yellow),
                    getColor(R.color.cc_playroom_orange),
                    getColor(R.color.cc_playroom_purple),
                    getColor(R.color.cc_playroom_blue),
                    getColor(R.color.cc_playroom_light_teal),
                    getColor(R.color.cc_playroom_light_yellow),
                    getColor(R.color.cc_playroom_light_orange),
                    getColor(R.color.cc_playroom_light_purple),
                    getColor(R.color.cc_playroom_light_blue),
                    getColor(R.color.cc_playroom_dark_teal),
                    getColor(R.color.cc_playroom_dark_yellow),
                    getColor(R.color.cc_playroom_dark_orange),
                    getColor(R.color.cc_playroom_dark_purple),
                    getColor(R.color.cc_playroom_dark_blue),
                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.PRIMARY -> {
                baseCodes = listOf(
                    getColor(R.color.cs_purple_dark_orchid),
                    getColor(R.color.cs_blue_rich_electric_blue),
                    getColor(R.color.cs_light_green_kiwi),
                    getColor(R.color.cs_yellow_lemon_yellow),
                    getColor(R.color.cs_deep_orange_deep_saffron),
                    getColor(R.color.cs_red_red_salsa)
                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.QUANTRO -> {
                baseCodes = listOf(
                    getColor(R.color.quantro_standard_red),
                    getColor(R.color.quantro_standard_blue),
                    getColor(R.color.quantro_standard_gold),
                    getColor(R.color.quantro_standard_green),
                    getColor(R.color.quantro_standard_nile_fuschia),
                    getColor(R.color.quantro_standard_nile_ice),
                    getColor(R.color.quantro_standard_nile_fire),
                    getColor(R.color.quantro_standard_dawn_purple),
                    getColor(R.color.quantro_standard_dawn_orange),
                    getColor(R.color.quantro_standard_dawn_green),
                    getColor(R.color.quantro_standard_dawn_red),

                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.RETRO -> {
                baseCodes = listOf(
                    getColor(R.color.quantro_retro_red_fiery),
                    getColor(R.color.quantro_retro_yellow_gold),
                    getColor(R.color.quantro_retro_green_light),
                    getColor(R.color.quantro_retro_blue),
                    getColor(R.color.quantro_retro_purple),
                    getColor(R.color.quantro_retro_pink),
                    getColor(R.color.quantro_retro_red_blush),
                    getColor(R.color.quantro_retro_blue_deep),
                    getColor(R.color.quantro_retro_orange_burnt),
                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
            CodeColorScheme.PLAIN -> {  // same greys as SKY UNTRIED colors
                baseCodes = listOf(if (darkMode) getColor(R.color.md_grey_500) else getColor(R.color.md_grey_300))
                baseOnCodes = listOf(if (darkMode) getColor(R.color.white) else getColor(R.color.black))
            }
            CodeColorScheme.PASTELS -> { // https://www.schemecolor.com/light-rainbow-pastels.php
                baseCodes = listOf(
                    getColor(R.color.cs_purple_pale_lavendar),
                    getColor(R.color.cs_light_blue_water),
                    getColor(R.color.cs_cyan_light_cyan),
                    getColor(R.color.cs_yellow_floral_white),
                    getColor(R.color.cs_orange_seashell),
                    getColor(R.color.cs_pink_pale_pink)
                )
                baseOnCodes = listOf(getColor(R.color.md_grey_800))
            }
            CodeColorScheme.PRIDE -> { // see colors_flag.xml
                baseCodes = listOf(
                    // 6 gay pride colors
                    getColor(R.color.fl_pride_red),
                    getColor(R.color.fl_pride_orange),
                    getColor(R.color.fl_pride_yellow),
                    getColor(R.color.fl_pride_green),
                    getColor(R.color.fl_pride_blue),
                    getColor(R.color.fl_pride_purple),
                    // 2 trans flag colors (omit white)
                    getColor(R.color.fl_trans_blue),
                    getColor(R.color.fl_trans_pink),
                    // 3 pan flag colors
                    getColor(R.color.fl_pan_blue),
                    getColor(R.color.fl_pan_yellow),
                    getColor(R.color.fl_pan_magenta),
                    // 2 additional gay pride colors from the original 8-color flag
                    getColor(R.color.fl_pride_8_turquoise),
                    getColor(R.color.fl_pride_8_pink),
                    // 3 poly flag colors
                    getColor(R.color.fl_poly_blue),
                    getColor(R.color.fl_poly_green),
                    getColor(R.color.fl_poly_magenta),
                    // 2 intersex flag colors
                    getColor(R.color.fl_intersex_purple),
                    getColor(R.color.fl_intersex_yellow),
                )
                baseOnCodes = listOf(getColor(R.color.white))
            }
        }

        val codes: List<Int>
        val onCodes: List<Int>

        when {
            key.codeColorsReversed && key.codeColorsInverted -> {
                codes = baseOnCodes.reversed()
                onCodes = baseCodes.reversed()
            }

            key.codeColorsReversed -> {
                codes = baseCodes.reversed()
                onCodes = baseOnCodes.reversed()
            }

            key.codeColorsInverted -> {
                codes = baseOnCodes
                onCodes = baseCodes
            }

            else -> {
                codes = baseCodes
                onCodes = baseOnCodes
            }
        }

        return ColorSwatch.Code(codes, onCodes)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Helpers
    //---------------------------------------------------------------------------------------------
    private fun getColor(@ColorRes id: Int, alpha: Int = -1): Int {
        var color = ContextCompat.getColor(context, id)

        // apply modifier(s)
        if (alpha >= 0) {
            color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        return color
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}