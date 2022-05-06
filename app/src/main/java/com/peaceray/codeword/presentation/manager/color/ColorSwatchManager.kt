package com.peaceray.codeword.presentation.manager.color

import androidx.lifecycle.LiveData
import com.peaceray.codeword.presentation.datamodel.ColorSwatch

/**
 * A Manager for color schemes and swatches. Allows observation of color swatch changes via
 * LiveData container. Updates in response to visual settings changes, loading the appropriate
 * colors for new night mode or settings changes.
 */
interface ColorSwatchManager {

    /**
     * Provide the current ColorSwatch for coloring codes and evaluations.
     */
    val colorSwatch: ColorSwatch

    /**
     * Provide a LiveData wrapper for watching the current ColorSwatch.
     */
    val colorSwatchLiveData: LiveData<ColorSwatch>

}