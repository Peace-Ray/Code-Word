package com.peaceray.codeword.presentation.view.component.viewholders.guess

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.cardview.widget.CardView
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterMarkupAppearance
import com.peaceray.codeword.utils.extensions.view.setScale
import kotlin.random.Random

class GuessLetterViewHolder(
    itemView: View,
    val colorSwatchManager: ColorSwatchManager,
    var appearance: GuessLetterAppearance,
    var markupAppearance: Map<GuessMarkup, GuessLetterAppearance> = emptyMap()
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
        get() = appearance.getCardElevation(guess)

    constructor(itemView: View, colorSwatchManager: ColorSwatchManager): this(
        itemView,
        colorSwatchManager,
        GuessLetterMarkupAppearance(itemView.context, GuessLetterCellLayout.create(itemView.resources))
    )

    init {
        backgroundView = itemView.findViewById(R.id.backgroundView) ?: itemView
        textView = itemView.findViewById(R.id.textView)

        val context = itemView.context

        dimenCardElevation = context.resources.getDimension(R.dimen.guess_letter_cell_large_elevation)
        dimenCardRadius = context.resources.getDimension(R.dimen.guess_letter_cell_large_corner_radius)

        durationShortAnimation = context.resources.getInteger(android.R.integer.config_shortAnimTime)
        durationMediumAnimation = context.resources.getInteger(android.R.integer.config_mediumAnimTime)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data
    //---------------------------------------------------------------------------------------------
    private var bound = false

    private var _guess = GuessLetter(0)
    var guess
        get() = _guess
        set(value) = bind(value)

    fun bind(guess: GuessLetter) {
        _guess = guess
        setViewContent(guess)
        setViewStyle(GuessViewValues.create(appearance, colorSwatchManager.colorSwatch, guess))

        bound = true
    }

    private fun setViewContent(guess: GuessLetter) {
        textView.text = "${guess.character}"
    }

    private fun setViewStyle(cellStyle: GuessViewValues) {
        if (backgroundView is CardView) {
            // support elevation, corners, scale
            backgroundView.cardElevation = cellStyle.cell.elevation
            backgroundView.radius = cellStyle.corners.radius
            backgroundView.setScale(cellStyle.cell.scale)
        } else {
            backgroundView.z = cellStyle.cell.elevation
        }

        if (backgroundView is MaterialCardView) {
            // support strokes
            backgroundView.strokeColor = cellStyle.stroke.color
            backgroundView.strokeWidth = cellStyle.stroke.width.toInt()
            backgroundView.setCardBackgroundColor(cellStyle.solid.color)
        } else if (backgroundView is CardView) { // no stroke; use fill color
            backgroundView.setCardBackgroundColor(cellStyle.solid.fillColor)
        } else { // no stroke support; use fill color for background
            backgroundView.setBackgroundColor(cellStyle.solid.fillColor)
        }

        // style independent of background view type
        textView.setTextColor(cellStyle.value.color)
        textView.alpha = cellStyle.value.alpha
    }

    private fun createViewStyleAnimatorUpdateListener(fromStyle: GuessViewValues, toStyle: GuessViewValues): ValueAnimator.AnimatorUpdateListener {
        // Tools (color evaluator, etc.)
        val argbEvaluator = ArgbEvaluatorCompat()

        // animator
        return ValueAnimator.AnimatorUpdateListener { animator ->
            val a = animator?.animatedFraction ?: 1.0f
            if (backgroundView is CardView) {
                // support elevation, corners, scale
                backgroundView.cardElevation = fromStyle.cell.elevation + a * (toStyle.cell.elevation - fromStyle.cell.elevation)
                backgroundView.radius = fromStyle.corners.radius + a * (toStyle.corners.radius - fromStyle.corners.radius)
                backgroundView.setScale(fromStyle.cell.scale + a * (toStyle.cell.scale - fromStyle.cell.scale))
            } else {
                backgroundView.z = fromStyle.cell.elevation + a * (toStyle.cell.elevation - fromStyle.cell.elevation)
            }

            when (backgroundView) {
                is MaterialCardView -> {
                    // support strokes
                    backgroundView.strokeColor = argbEvaluator.evaluate(a, fromStyle.stroke.color, toStyle.stroke.color)
                    backgroundView.strokeWidth = (fromStyle.stroke.width + a * (toStyle.stroke.width - fromStyle.stroke.width)).toInt()
                    backgroundView.setCardBackgroundColor(argbEvaluator.evaluate(a, fromStyle.solid.color, toStyle.solid.color))
                }
                is CardView -> {
                    backgroundView.setCardBackgroundColor(argbEvaluator.evaluate(a, fromStyle.solid.fillColor, toStyle.solid.fillColor))
                }
                else -> {
                    backgroundView.setBackgroundColor(argbEvaluator.evaluate(a, fromStyle.solid.fillColor, toStyle.solid.fillColor))
                }
            }

            // style independent of background view type
            textView.setTextColor(argbEvaluator.evaluate(a, fromStyle.value.color, toStyle.value.color))
            textView.alpha = fromStyle.value.alpha + a * (toStyle.value.alpha - fromStyle.value.alpha)
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
            val cardView = itemView
            animator.doOnEnd {
                // if another animation affects this, correct at the end
                if (cardView is CardView) {
                    cardView.cardElevation = itemElevation
                } else {
                    cardView.elevation = itemElevation
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
                val cardView = itemView
                if (cardView is CardView) {
                    cardView.cardElevation = itemElevation
                } else {
                    cardView.elevation = itemElevation
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

    /**
     * A quick data class for holding the View configuration values determined by
     * guess letter, game progress, current theming, etc. Useful in styling views, especially
     * when the style details are to be referenced multiple times (such as during animation).
     * Shares a lot of structure with a ShapeDrawable XML definition, but has some distinct
     * fields and functions.
     */
    internal data class GuessViewValues(
        val value: Value,
        val stroke: Stroke,
        val solid: Solid,
        val corners: Corners,
        val cell: Cell
    ) {
        data class Value(@ColorInt val color: Int, val alpha: Float)
        data class Stroke(@ColorInt val color: Int, @Dimension val width: Float)
        data class Solid(@ColorInt val color: Int, @ColorInt val fillColor: Int)
        data class Corners(@Dimension val radius: Float)
        data class Cell(val scale: Float, @Dimension val elevation: Float)

        companion object {
            fun create(appearance: GuessLetterAppearance, swatch: ColorSwatch, guess: GuessLetter): GuessViewValues {
                val bg = appearance.getColorBg(guess, swatch)
                return GuessViewValues(
                    Value(appearance.getColorText(guess, swatch), appearance.getAlphaText(guess)),
                    Stroke(appearance.getColorBgAccent(guess, swatch), appearance.getCardStrokeWidth(guess)),
                    Solid(if (guess.isPlaceholder) swatch.container.background else bg, bg),
                    Corners(appearance.getCardCornerRadius(guess)),
                    Cell(appearance.getCardScale(guess), appearance.getCardElevation(guess))
                )
            }
        }

        fun isSameColor(values: GuessViewValues?): Boolean {
            return values != null
                    && value.color == values.value.color
                    && stroke.color == values.stroke.color
                    && solid.color == values.solid.color
        }
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
        // interpolators and other tools
        private val overshootInterpolator = OvershootInterpolator(3.0f)

        // animation map
        private val animatorMap: MutableMap<RecyclerView.ViewHolder, ValueAnimator> = mutableMapOf()

        class GuessLetterItemHolderInfo: ItemHolderInfo() {
            var guess: GuessLetter = GuessLetter(0)
            internal var style: GuessViewValues? = null
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
                val appearance = viewHolder.markupAppearance[viewHolder.guess.markup] ?: viewHolder.appearance
                info.guess = viewHolder.guess
                info.style = GuessViewValues.create(appearance, viewHolder.colorSwatchManager.colorSwatch, viewHolder.guess)
            }
            return info
        }

        override fun recordPostLayoutInformation(
            state: RecyclerView.State,
            viewHolder: RecyclerView.ViewHolder
        ): ItemHolderInfo {
            val info = super.recordPostLayoutInformation(state, viewHolder)
            if (viewHolder is GuessLetterViewHolder && info is GuessLetterItemHolderInfo) {
                val appearance = viewHolder.markupAppearance[viewHolder.guess.markup] ?: viewHolder.appearance
                info.guess = viewHolder.guess
                info.style = GuessViewValues.create(appearance, viewHolder.colorSwatchManager.colorSwatch, viewHolder.guess)
            }
            return info
        }

        override fun canReuseUpdatedViewHolder(
            viewHolder: RecyclerView.ViewHolder,
            payloads: MutableList<Any>
        ): Boolean {
            return viewHolder is GuessLetterViewHolder
        }

        override fun animateDisappearance(
            viewHolder: RecyclerView.ViewHolder,
            preLayoutInfo: ItemHolderInfo,
            postLayoutInfo: ItemHolderInfo?
        ): Boolean {
            animatorMap[viewHolder]?.cancel()
            return super.animateDisappearance(viewHolder, preLayoutInfo, postLayoutInfo)
        }

        override fun animateAppearance(
            viewHolder: RecyclerView.ViewHolder,
            preLayoutInfo: ItemHolderInfo?,
            postLayoutInfo: ItemHolderInfo
        ): Boolean {
            if (viewHolder is GuessLetterViewHolder) {
                animatorMap[viewHolder]?.cancel()
                if (postLayoutInfo is GuessLetterItemHolderInfo) {
                    viewHolder.setViewContent(postLayoutInfo.guess)
                    viewHolder.setViewStyle(postLayoutInfo.style!!)
                }
            }
            // allow parent animation
            return super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo)
        }

        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder,
            holder: RecyclerView.ViewHolder,
            preInfo: ItemHolderInfo,
            postInfo: ItemHolderInfo
        ): Boolean {
            // use default animation of not dealing with GuessLetterViewHolder
            if (holder !is GuessLetterViewHolder) {
                return super.animateChange(oldHolder, holder, preInfo, postInfo)
            }

            if (preInfo is GuessLetterItemHolderInfo && postInfo is GuessLetterItemHolderInfo) {
                // deal with previous animations, if any
                animatorMap[holder]?.cancel()

                // configure a ViewPropertyAnimator, and set this variable to its value
                val animator: ValueAnimator? = when {
                    // from placeholder to non-placeholder
                    preInfo.guess.isPlaceholder && !postInfo.guess.isPlaceholder -> {

                        // set style to "before" state
                        holder.setViewStyle(preInfo.style!!)

                        // animate shape and color transition from previous to new
                        val a = ObjectAnimator.ofFloat(0f, 1f)

                        a.duration = holder.durationShortAnimation.toLong()
                        a.addUpdateListener(holder.createViewStyleAnimatorUpdateListener(preInfo.style!!, postInfo.style!!))
                        a.doOnEnd {
                            animatorMap.remove(holder)
                            dispatchAnimationFinished(holder)
                        }

                        a
                    }

                    // from non-placeholder (guess) to placeholder
                    !preInfo.guess.isPlaceholder && postInfo.guess.isPlaceholder -> {

                        // set style and content to "before" state
                        holder.setViewContent(preInfo.guess)
                        holder.setViewStyle(preInfo.style!!)

                        // animate shape and color back to "placeholder" status. Note that this
                        // requires careful management of text content, which should change only
                        // once the animation is complete
                        val a = ObjectAnimator.ofFloat(0f, 1f)

                        a.duration = holder.durationShortAnimation.toLong()
                        a.addUpdateListener(holder.createViewStyleAnimatorUpdateListener(preInfo.style!!, postInfo.style!!))
                        a.doOnEnd {
                            animatorMap.remove(holder)

                            holder.setViewContent(postInfo.guess)
                            dispatchAnimationFinished(holder)
                        }

                        a
                    }

                    // a change in markup producing a change in color
                    preInfo.guess.isSameCandidateAs(postInfo.guess)
                            && preInfo.guess.markup != postInfo.guess.markup
                            && !preInfo.style!!.isSameColor(postInfo.style) -> {

                        // set style to "before" content
                        holder.setViewStyle(preInfo.style!!)

                        // animate a flip. The content and style change after 0.5 mark
                        var duration = holder.durationMediumAnimation.toLong()
                        var setPostContent = false
                        val delay = postInfo.guess.position * (duration * 0.2)

                        val a = ObjectAnimator.ofFloat(0f, 1f)

                        a.duration = duration
                        a.startDelay = delay.toLong()
                        a.interpolator = overshootInterpolator
                        a.addUpdateListener {
                            if (!setPostContent && it.animatedFraction > 0.5f) {
                                holder.setViewStyle(postInfo.style!!)
                                setPostContent = true
                            } else if (setPostContent && it.animatedFraction < 0.5f) {
                                holder.setViewStyle(preInfo.style!!)
                                setPostContent = false
                            }
                            // adjust rotation s.t. there's no "back side" to the view;
                            // skip the 90-270 degree range (fraction 0.25-0.75).
                            val a = if (it.animatedFraction < 0.5f) it.animatedFraction / 2 else {
                                it.animatedFraction / 2 + 0.5f
                            }
                            holder.itemView.rotationY = 360.0f * a
                        }
                        a.doOnEnd {
                            animatorMap.remove(holder)

                            holder.itemView.rotationY = 0f
                            dispatchAnimationFinished(holder)
                        }

                        a
                    }

                    // from guess to evaluation, but markup did not change
                    preInfo.guess.isSameCandidateAs(postInfo.guess)
                            && preInfo.guess.isGuess && postInfo.guess.isEvaluation -> {

                        // set style to "before" content
                        holder.setViewStyle(preInfo.style!!)
                        holder.setViewContent(postInfo.guess)

                        // animate shape and color to the new status.
                        val a = ObjectAnimator.ofFloat(0f, 1f)

                        a.duration = holder.durationShortAnimation.toLong()
                        a.addUpdateListener(holder.createViewStyleAnimatorUpdateListener(preInfo.style!!, postInfo.style!!))
                        a.doOnEnd {
                            animatorMap.remove(holder)

                            holder.setViewContent(postInfo.guess)
                            dispatchAnimationFinished(holder)
                        }

                        a
                    }

                    // by default: no animation
                    else -> null
                }

                // add to map and start animation
                if (animator != null) {
                    animator.start()
                    animatorMap[holder] = animator
                    return true
                }
            }

            // superclass can handle all other cases
            return super.animateChange(oldHolder, holder, preInfo, postInfo)
        }

        override fun isRunning(): Boolean {
            return super.isRunning() || animatorMap.isNotEmpty()
        }

        override fun endAnimation(item: RecyclerView.ViewHolder) {
            super.endAnimation(item)
            animatorMap[item]?.cancel()
        }

        override fun endAnimations() {
            super.endAnimations()
            if (animatorMap.isNotEmpty()) {
                animatorMap.values.forEach { it.cancel() }
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}