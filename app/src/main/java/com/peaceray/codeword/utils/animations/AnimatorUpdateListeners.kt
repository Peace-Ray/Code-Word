package com.peaceray.codeword.utils.animations

import android.animation.ValueAnimator

open class AnimatorUpdateListeners(val listeners: List<ValueAnimator.AnimatorUpdateListener>): ValueAnimator.AnimatorUpdateListener {
    constructor(vararg listeners: ValueAnimator.AnimatorUpdateListener): this(listeners.asList())

    override fun onAnimationUpdate(animator: ValueAnimator?) {
        listeners.forEach { it.onAnimationUpdate(animator) }
    }
}