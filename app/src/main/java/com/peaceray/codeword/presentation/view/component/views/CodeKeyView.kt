package com.peaceray.codeword.presentation.view.component.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.IdRes
import androidx.core.content.withStyledAttributes
import com.peaceray.codeword.R
import com.peaceray.codeword.utils.extensions.isVisibleToUser
import com.peaceray.codeword.utils.extensions.getEnum
import timber.log.Timber
import java.lang.IllegalStateException

class CodeKeyView: FrameLayout {

    //region KeyTypes, Codes, Values
    //---------------------------------------------------------------------------------------------
    enum class KeyType { CHARACTER, ENTER, DELETE, AVAILABLE, NONE }

    lateinit var label: String private set
    lateinit var type: KeyType private set
    var character: Char? = null
        private set
    var group: Int = 0
        private set

    var textView: TextView? = null
        private set
    var imageView: ImageView? = null
        private set
    var backgroundView: View? = null
        private set

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

            val defaultType = if (character == null) KeyType.AVAILABLE else KeyType.CHARACTER
            type = getEnum(R.styleable.CodeKey_keyCodeType, defaultType)

            val defaultLabel = when(type) {
                KeyType.CHARACTER -> "$character"
                KeyType.ENTER -> context.getString(R.string.key_label_enter)
                KeyType.DELETE -> context.getString(R.string.key_label_delete)
                KeyType.AVAILABLE -> "_"
                KeyType.NONE -> ""
            }
            label = getString(R.styleable.CodeKey_keyCodeLabel) ?: defaultLabel
            group = getInt(R.styleable.CodeKey_keyCodeGroup, 0)

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

        updateViewContent()
    }

    private fun updateViewContent() {
        textView?.text = label
        // TODO set image src?
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Content Controls
    //---------------------------------------------------------------------------------------------
    fun setCharacter(character: Char?, label: String? = null) {
        when (type) {
            KeyType.CHARACTER, KeyType.AVAILABLE -> {
                if (type == KeyType.CHARACTER && character == null) {
                    throw IllegalStateException("Can't setCharacter to 'null' for CHARACTER type")
                }
                this.character = character
                this.label = label ?: if (character != null) "$character" else "_"
                updateViewContent()
            }
            else -> throw IllegalStateException("Can't setCharacter for key type $type")
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Property Overrides
    //---------------------------------------------------------------------------------------------
    private var lastSetEnabled: Boolean? = null
    private var lastSetEnabledAnimator: ViewPropertyAnimator? = null

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        if (enabled != lastSetEnabled) {
            lastSetEnabled = enabled
            lastSetEnabledAnimator?.cancel()
            val zValue = if (enabled) resources.getDimension(R.dimen.keyboard_key_elevation) else 0.0f
            val alphaValue = if (enabled) 1.0f else 0.7f
            if (isVisibleToUser()) {
                lastSetEnabledAnimator = backgroundView?.animate()
                    ?.z(zValue)
                    ?.alpha(alphaValue)
                    ?.setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                    ?.setStartDelay(300L)
            } else {
                backgroundView?.z = zValue
                backgroundView?.alpha = alphaValue
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}