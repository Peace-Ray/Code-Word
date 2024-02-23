package com.peaceray.codeword.presentation.manager.accessibility

/**
 * A Manager for Accessibility settings. Since these settings deal with user input devices,
 * display colors and styles, etc., and do not affect basic game data handling, the Manager
 * is implemented at the Presentation layer.
 */
interface AccessibilityManager {

    /**
     * Is it permitted to use a hardware keyboard to enter guesses? Useful for
     * users with such a device, but potentially interferes with other accessibility
     * functions by capturing key presses.
     */
    var isHardwareKeyboardAllowed: Boolean

}