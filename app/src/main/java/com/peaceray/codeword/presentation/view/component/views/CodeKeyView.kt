package com.peaceray.codeword.presentation.view.component.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.IdRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.peaceray.codeword.R
import com.peaceray.codeword.utils.extensions.isVisibleToUser
import com.peaceray.codeword.utils.getEnum
import timber.log.Timber

class CodeKeyView: FrameLayout {

    //region KeyTypes, Codes, Values
    //---------------------------------------------------------------------------------------------
    enum class KeyType { CHARACTER, ENTER, DELETE, NONE }

    var character: Char? = null
    lateinit var label: String
    lateinit var type: KeyType

    var textView: TextView? = null
    var imageView: ImageView? = null
    var backgroundView: View? = null

    @IdRes private var textViewId: Int = R.id.keyTextView
    @IdRes private var imageViewId: Int = R.id.keyImageView
    @IdRes private var backgroundViewId: Int = R.id.keyBackgroundView
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Construction / Inflation
    //---------------------------------------------------------------------------------------------
    constructor(context: Context): super(context) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
        initialize(context, attrs, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ): super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr)
    }

    constructor(context: Context, type: KeyType, label: String, character: Char?): super(context) {
        this.type = type
        this.label = label
        this.character = character
    }

    constructor(context: Context, character: Char, label: String? = null): super(context) {
        this.type = KeyType.CHARACTER
        this.label = label ?: "$character"
        this.character = character
    }

    private fun initialize(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int) {
        context.withStyledAttributes(attrs, R.styleable.CodeKey) {
            val characterStr = getString(R.styleable.CodeKey_keyCodeCharacter)
            if (characterStr != null) character = characterStr[0]

            val defaultType = if (character == null) KeyType.NONE else KeyType.CHARACTER
            type = getEnum(R.styleable.CodeKey_keyCodeType, defaultType)

            val defaultLabel = when(type) {
                KeyType.CHARACTER -> "$character"
                KeyType.ENTER -> context.getString(R.string.key_label_enter)
                KeyType.DELETE -> context.getString(R.string.key_label_delete)
                KeyType.NONE -> ""
            }
            label = getString(R.styleable.CodeKey_keyCodeLabel) ?: defaultLabel

            textViewId = getInt(R.styleable.CodeKey_keyTextViewId, textViewId)
            imageViewId = getInt(R.styleable.CodeKey_keyImageViewId, imageViewId)
            backgroundViewId = getInt(R.styleable.CodeKey_keyBackgroundViewId, backgroundViewId)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // get text and image views
        textView = findViewById(textViewId)
        imageView = findViewById(imageViewId)
        backgroundView = findViewById(backgroundViewId)

        textView?.text = label
        // TODO set image src?
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Property Overrides
    //---------------------------------------------------------------------------------------------
    private var lastSetEnabled: Boolean? = null

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        if (enabled != lastSetEnabled) {
            lastSetEnabled = enabled
            val zValue = if (enabled) resources.getDimension(R.dimen.keyboard_key_elevation) else 0.0f
            val alphaValue = if (enabled) 1.0f else 0.7f
            if (isVisibleToUser()) {
                Timber.v("animating Keyboard change")
                backgroundView?.animate()
                    ?.z(zValue)
                    ?.alpha(alphaValue)
                    ?.setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
            } else {
                Timber.v("setting keyboard change")
                z = zValue
                alpha = alphaValue
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}