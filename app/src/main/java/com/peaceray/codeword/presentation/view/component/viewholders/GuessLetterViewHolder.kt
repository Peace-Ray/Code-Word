package com.peaceray.codeword.presentation.view.component.viewholders

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.R
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GuessLetter
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.utils.animations.AnimatorUpdateListeners
import com.peaceray.codeword.utils.extensions.view.setScale
import kotlin.random.Random


class GuessLetterViewHolder(
    itemView: View,
    val layoutInflater: LayoutInflater,
    val colorSwatchManager: ColorSwatchManager
): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    private val backgroundView: View
    private val textView: TextView

    private val dimenCardElevation: Float
    private val dimenCardRadius: Float

    private val durationShortAnimation: Int
    private val durationMediumAnimation: Int

    private val itemElevation
        get() = if (guess.character == ' ') 0f else dimenCardElevation

    init {
        backgroundView = itemView.findViewById(R.id.backgroundView) ?: itemView
        textView = itemView.findViewById(R.id.textView)

        val context = itemView.context
        if (context is LifecycleOwner) {
            colorSwatchManager.colorSwatchLiveData.observe(context) { swatch ->
                setViewColors(guess, swatch)
            }
        }

        dimenCardElevation = context.resources.getDimension(R.dimen.guess_letter_cell_elevation)
        dimenCardRadius = context.resources.getDimension(R.dimen.guess_letter_cell_corner_radius)

        durationShortAnimation = context.resources.getInteger(android.R.integer.config_shortAnimTime)
        durationMediumAnimation = context.resources.getInteger(android.R.integer.config_mediumAnimTime)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data
    //---------------------------------------------------------------------------------------------
    private var bound = false

    private var _guess = GuessLetter.placeholder
    var guess
        get() = _guess
        set(value) = bind(value)

    fun bind(guess: GuessLetter) {
        _guess = guess

        setViewContent(guess)
        setViewShape(guess)
        setViewColors(guess, colorSwatchManager.colorSwatch)

        bound = true
    }

    private fun setViewContent(guess: GuessLetter) {
        textView.text = "${guess.character}"
    }

    private fun setViewShape(guess: GuessLetter) {
        textView.alpha = if (guess.isPlaceholder) 0.0f else 1.0f
        if (itemView is CardView) {
            itemView.cardElevation = if (guess.character == ' ') 0f else dimenCardElevation
            itemView.radius = if (guess.character == ' ') 0f else dimenCardRadius
            itemView.setScale(if (guess.character == ' ') 0.9f else 1.0f)
        }
    }

    private fun setViewColors(guess: GuessLetter, swatch: ColorSwatch) {
        val bg = swatch.evaluation.color(guess.markup)
        when (backgroundView) {
            is MaterialCardView -> {
                if (guess.isPlaceholder) {
                    backgroundView.strokeColor = bg
                    backgroundView.setCardBackgroundColor(swatch.container.background)
                } else {
                    backgroundView.strokeColor = bg
                    backgroundView.setCardBackgroundColor(bg)
                }
            }
            is CardView -> {
                backgroundView.setCardBackgroundColor(bg)
            }
            else -> backgroundView.setBackgroundColor(bg)
        }

        textView.setTextColor(swatch.evaluation.onColor(guess.markup))
    }

    private fun createViewAppearanceAnimatorUpdateListener(guess: GuessLetter): ValueAnimator.AnimatorUpdateListener {
        // target settings
        val textAlpha: Float
        val itemElevation: Float; val itemRadius: Float; val itemScale: Float;
        if (guess.isPlaceholder) {
            textAlpha = 0.0f
            itemElevation = 0.0f
            itemRadius = 0.0f
            itemScale = 0.9f
        } else {
            textAlpha = 1.0f
            itemElevation = dimenCardElevation.toFloat()
            itemRadius = dimenCardRadius.toFloat()
            itemScale = 1.0f
        }

        // diff
        val textAlphaDiff = textView.alpha - textAlpha
        val itemElevationDiff: Float; val itemRadiusDiff: Float; val itemScaleDiff: Float;
        when (itemView) {
            is MaterialCardView -> {
                itemElevationDiff = itemView.cardElevation - itemElevation
                itemRadiusDiff = itemView.radius - itemRadius
                itemScaleDiff = itemView.scaleX - itemScale
            }
            is CardView -> {
                itemElevationDiff = itemView.cardElevation - itemElevation
                itemRadiusDiff = itemView.radius - itemRadius
                itemScaleDiff = itemView.scaleX - itemScale
            }
            else -> {
                itemElevationDiff = itemView.elevation - itemElevation
                itemRadiusDiff = 0.0f
                itemScaleDiff = itemView.scaleX - itemScale
            }
        }

        return ValueAnimator.AnimatorUpdateListener { animator ->
            val p = 1.0f - (animator?.animatedFraction ?: 1.0f)
            textView.alpha = textAlpha + p * textAlphaDiff
            when (itemView) {
                is CardView -> {
                    itemView.cardElevation = itemElevation + p * itemElevationDiff
                    itemView.radius = itemRadius + p * itemRadiusDiff
                    itemView.setScale(itemScale + p * itemScaleDiff)
                }
                else -> {
                    itemView.elevation = itemElevation + p * itemElevationDiff
                    itemView.setScale(itemScale + p * itemScaleDiff)
                }
            }
        }
    }

    fun createViewColorAnimatorUpdateListener(fromGuess: GuessLetter, guess: GuessLetter, swatch: ColorSwatch): ValueAnimator.AnimatorUpdateListener {
        // target settings
        val textColor = swatch.evaluation.onColor(guess.markup)
        val strokeColor = swatch.evaluation.color(guess.markup)
        val bgColor = if (backgroundView is MaterialCardView && guess.isPlaceholder) {
            swatch.container.background
        } else {
            strokeColor
        }

        // initial settings
        val fromTextColor = swatch.evaluation.onColor(fromGuess.markup)
        val fromStrokeColor = swatch.evaluation.color(fromGuess.markup)
        val fromBgColor = if (backgroundView is MaterialCardView && fromGuess.isPlaceholder) {
            swatch.container.background
        } else {
            fromStrokeColor
        }

        val evaluator = ArgbEvaluatorCompat()
        return ValueAnimator.AnimatorUpdateListener { animator ->
            val a = animator.animatedFraction
            textView.setTextColor(evaluator.evaluate(a, fromTextColor, textColor))
            when (backgroundView) {
                is MaterialCardView -> {
                    backgroundView.strokeColor = evaluator.evaluate(a, fromStrokeColor, strokeColor)
                    backgroundView.setCardBackgroundColor(evaluator.evaluate(a, fromBgColor, bgColor))
                }
                is CardView -> {
                    backgroundView.setCardBackgroundColor(evaluator.evaluate(a, fromBgColor, bgColor))
                }
                else -> backgroundView.setBackgroundColor(evaluator.evaluate(a, fromBgColor, bgColor))
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Animations and Other Helpers
    //---------------------------------------------------------------------------------------------
    /**
     * Animate the View represented by this ViewHolder with a "rumble": a shake along one or
     * more random vectors.
     *
     * @param delay The time to delay the start of the rumble.
     */
    fun rumble(delay: Long = 0) {
        // a Rumble animation is represented by jostling, potentially on multiple vectors. The Card
        // is slightly elevated above its normal height during this process to avoid slipping under
        // peers; it is then translated back and forth on a random vector.
        val animators = mutableListOf<ObjectAnimator>()

        if (itemElevation > 0) {
            val elevationTo = itemElevation + itemView.resources.getDimension(R.dimen.guess_letter_animation_elevation)
            val property = if (itemView is CardView) "cardElevation" else "elevation"
            val animator = ObjectAnimator.ofFloat(itemView, property, elevationTo, itemElevation)
            animator.doOnEnd {
                // if another animation affects this, correct at the end
                if (itemView is CardView) {
                    itemView.cardElevation = itemElevation
                } else {
                    itemView.elevation = itemElevation
                }
            }
            animators.add(animator)
        }

        val translateBy = itemView.resources.getDimension(R.dimen.guess_letter_rumble_size)
        val dist = translateBy * (Random.nextFloat() / 4 + 0.75f)
        // pick a random vector and animate a wobble along that axis that plays for
        // the indicated number of cycles, then attenuates.
        val vector = randomVector()
        val x = mutableListOf<Float>()
        val y = mutableListOf<Float>()

        val addWobble: (Float) -> Unit = { distance ->
            x.add((distance * vector.first).toFloat())
            y.add((distance * vector.second).toFloat())
            if (distance != 0.0f) {
                x.add(-(distance * vector.first).toFloat())
                y.add(-(distance * vector.second).toFloat())
            }
        }

        for (i in 1..2) {
            addWobble(dist)
        }
        // attenuate
        addWobble(dist * 0.6f)
        addWobble(dist * 0.25f)
        addWobble(0.0f)

        animators.add(ObjectAnimator.ofFloat(itemView, "translationX", *x.toFloatArray()))
        animators.add(ObjectAnimator.ofFloat(itemView, "translationY", *y.toFloatArray()))

        animators.forEach {
            it.duration = durationMediumAnimation.toLong()
            it.startDelay = delay
            it.start()
        }
    }

    /**
     * Animate the View represented by this ViewHolder with a "pulse": a slight size and elevation
     * increase, quickly reversed, to draw attention.
     *
     * @param delay The time to delay the start of the rumble.
     */
    fun pulse(delay: Long = 0) {
        val animators = mutableListOf<ObjectAnimator>()
        val duration = durationShortAnimation.toLong()

        if (itemElevation > 0) {
            val elevationTo = itemElevation + itemView.resources.getDimension(R.dimen.guess_letter_animation_elevation)
            val property = if (itemView is CardView) "cardElevation" else "elevation"
            val animator = ObjectAnimator.ofFloat(itemView, property, elevationTo, itemElevation)
            animator.doOnEnd {
                // if another animation affects this, correct at the end
                if (itemView is CardView) {
                    itemView.cardElevation = itemElevation
                } else {
                    itemView.elevation = itemElevation
                }
            }
            animator.duration = duration
            animators.add(animator)
        }

        // might already have a scale; use the equivalent of ViewPropertyAnimator's "scaleXBy"
        val scaleTo = 1.1f * itemView.scaleX
        val scaleAnimator = ObjectAnimator.ofPropertyValuesHolder(
            itemView,
            PropertyValuesHolder.ofFloat("scaleX", scaleTo),
            PropertyValuesHolder.ofFloat("scaleY", scaleTo)
        )

        scaleAnimator.repeatCount = 1
        scaleAnimator.repeatMode = ObjectAnimator.REVERSE
        scaleAnimator.duration = duration / 2
        scaleAnimator.interpolator = FastOutSlowInInterpolator()

        animators.add(scaleAnimator)

        animators.forEach {
            it.startDelay = delay
            it.start()
        }
    }

    private fun randomVector(): Pair<Double, Double> {
        val angle = Random.nextFloat() * Math.PI * 2
        return Pair(Math.cos(angle), Math.sin(angle))
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region ItemAnimator
    //---------------------------------------------------------------------------------------------
    /**
     * An [ItemAnimator] for [GuessLetterViewHolder]s. Provides custom animations for transitions
     * between guess and markup states, allowing the default [RecyclerView] animations to handle
     * all other content changes.
     *
     * Transitions supported:
     *
     * Empty -> Non-Empty: Slightly expand the cell, round its corners and fill it in, elevating it.
     *      The text of the guess fades in.
     * Non-Empty -> Empty: Reverse the above.
     * Guess -> Evaluation: Spin on the Y axis, left-side-up; alter the view color when not visible.
     */
    class ItemAnimator: DefaultItemAnimator() {
        class GuessLetterItemHolderInfo: ItemHolderInfo() {
            var guess: GuessLetter = GuessLetter.placeholder
        }

        override fun obtainHolderInfo(): ItemHolderInfo {
            return GuessLetterItemHolderInfo()
        }

        override fun recordPreLayoutInformation(
            state: RecyclerView.State,
            viewHolder: RecyclerView.ViewHolder,
            changeFlags: Int,
            payloads: MutableList<Any>
        ): ItemHolderInfo {
            val info = super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads)
            if (viewHolder is GuessLetterViewHolder && info is GuessLetterItemHolderInfo) {
                info.guess = viewHolder.guess
            }
            return info
        }

        override fun recordPostLayoutInformation(
            state: RecyclerView.State,
            viewHolder: RecyclerView.ViewHolder
        ): ItemHolderInfo {
            val info = super.recordPostLayoutInformation(state, viewHolder)
            if (viewHolder is GuessLetterViewHolder && info is GuessLetterItemHolderInfo) {
                info.guess = viewHolder.guess
            }
            return info
        }

        override fun canReuseUpdatedViewHolder(
            viewHolder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
        ): Boolean {
            return true
        }

        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder,
            newHolder: RecyclerView.ViewHolder,
            preInfo: ItemHolderInfo,
            postInfo: ItemHolderInfo
        ): Boolean {
            val holder = newHolder as GuessLetterViewHolder

            if (preInfo is GuessLetterItemHolderInfo && postInfo is GuessLetterItemHolderInfo) {
                val swatch = holder.colorSwatchManager.colorSwatch

                // from placeholder to non-placeholder (guess)
                if (preInfo.guess.isPlaceholder && !postInfo.guess.isPlaceholder) {
                    // animate shape and color transition from previous to new
                    holder.setViewShape(preInfo.guess)
                    holder.setViewColors(preInfo.guess, swatch)
                    holder.itemView.animate()
                        .setUpdateListener(AnimatorUpdateListeners(
                            holder.createViewAppearanceAnimatorUpdateListener(postInfo.guess),
                            holder.createViewColorAnimatorUpdateListener(
                                preInfo.guess,
                                postInfo.guess,
                                swatch
                            )
                        ))
                        .setDuration(holder.durationShortAnimation.toLong())
                        .withEndAction { dispatchAnimationFinished(holder) }
                        .start()

                    return true
                }

                // from non-placeholder (guess) to placeholder
                if (!preInfo.guess.isPlaceholder && postInfo.guess.isPlaceholder) {
                    // animate shape and color back to "placeholder" status. Note that this
                    // requires careful management of text content, which should change only
                    // once the animation is complete
                    holder.setViewContent(preInfo.guess)
                    holder.setViewShape(preInfo.guess)
                    holder.setViewColors(preInfo.guess, swatch)

                    holder.itemView.animate()
                        .setUpdateListener(AnimatorUpdateListeners(
                            holder.createViewAppearanceAnimatorUpdateListener(postInfo.guess),
                            holder.createViewColorAnimatorUpdateListener(
                                preInfo.guess,
                                postInfo.guess,
                                swatch
                            )
                        ))
                        .setDuration(holder.durationShortAnimation.toLong())
                        .withEndAction {
                            holder.setViewContent(postInfo.guess)
                            dispatchAnimationFinished(holder)
                        }
                        .start()

                    return true
                }

                // from guess to evaluation
                if (
                    preInfo.guess.isGuess
                    && postInfo.guess.isEvaluation
                    && preInfo.guess.guess.candidate == postInfo.guess.guess.candidate
                ) {
                    // shape: no change
                    // content and color: change after 0.5 mark
                    holder.setViewContent(preInfo.guess)
                    holder.setViewColors(preInfo.guess, swatch)

                    var duration = holder.durationMediumAnimation.toLong()
                    var setPostContent = false
                    val delay = postInfo.guess.position * (duration * 0.2)
                    holder.itemView.animate()
                        .setUpdateListener {
                            if (!setPostContent && it.animatedFraction > 0.5f) {
                                holder.setViewContent(postInfo.guess)
                                holder.setViewColors(postInfo.guess, swatch)
                                setPostContent = true
                            } else if (setPostContent && it.animatedFraction < 0.5f) {
                                holder.setViewContent(preInfo.guess)
                                holder.setViewColors(preInfo.guess, swatch)
                                setPostContent = false
                            }
                            // adjust rotation s.t. there's no "back side" to the view;
                            // skip the 90-270 degree range (fraction 0.25-0.75).
                            val a = if (it.animatedFraction < 0.5f) it.animatedFraction / 2 else {
                                it.animatedFraction / 2 + 0.5f
                            }
                            holder.itemView.rotationY = 360.0f * a
                        }
                        .setDuration(holder.durationMediumAnimation.toLong())
                        .setInterpolator(OvershootInterpolator(3.0f))
                        .withEndAction { dispatchAnimationFinished(holder) }
                        .setStartDelay(delay.toLong())
                        .start()

                    return true
                }
            }

            // handle view positioning
            return super.animateChange(oldHolder, newHolder, preInfo, postInfo)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}