package org.torproject.android.ui.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.view.get
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.torproject.android.R

class PillNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pillContainer: LinearLayout
    private val highlight: View
    private var selectedIndex = 0
    private val animDuration = 260L
    private val highlightMargin = 12.dp
    private val pillSpacing = 0.dp

    var bottomNav: BottomNavigationView? = null
        set(value) {
            field = value
            value?.let { bn ->
                buildPillsFromBottomNav(bn)
                bn.setOnItemSelectedListener { item ->
                    selectById(item.itemId, animate = true)
                    false
                }
            }
        }

    init {
        setPadding(0, 0, 0, 0)

        highlight = View(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_pill_highlight)
            elevation = 6f * resources.displayMetrics.density
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = highlightMargin
                bottomMargin = highlightMargin
            }
            isClickable = false
            z = -10f
        }
        addView(highlight)

        pillContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                leftMargin = highlightMargin
                rightMargin = highlightMargin
            }
        }
        addView(pillContainer)
    }

    private fun buildPillsFromBottomNav(bn: BottomNavigationView) {
        val menu = bn.menu
        pillContainer.removeAllViews()

        val itemCount = menu.size
        for (i in 0 until itemCount) {
            val mi = menu[i]
            val pill = createPill(mi.itemId, mi.title?.toString() ?: "", mi.icon)
            pillContainer.addView(pill)
        }

        val initialId = bn.selectedItemId.takeIf { it != NO_ID } ?: menu[0].itemId
        selectById(initialId, animate = false)

        post { moveHighlightToIndex(selectedIndex, animate = false) }
    }

    private fun createPill(@IdRes id: Int, title: String, icon: Drawable?): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16.dp, 8.dp, 16.dp, 8.dp)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = pillSpacing
                marginEnd = pillSpacing
            }
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_pill_transparent)
            elevation = 8f * resources.displayMetrics.density
        }

        val iv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp)
            setImageDrawable(icon)
            imageTintList = ContextCompat.getColorStateList(context, R.color.pill_icon_color)
        }

        val tv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 4.dp
            }
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.pill_text_color))
            isVisible = false
            alpha = 0f
            setTypeface(typeface, Typeface.BOLD)
        }

        pill.addView(iv)
        pill.addView(tv)

        pill.setOnClickListener {
            bottomNav?.let { bn ->
                bn.selectedItemId = id
            }
            selectById(id, animate = true)
        }

        pill.tag = id
        return pill
    }

    private fun selectById(@IdRes id: Int, animate: Boolean) {
        val idx = findIndexForId(id)
        if (idx == -1) return
        selectedIndex = idx
        updatePillAppearance(idx, animate)
        moveHighlightToIndex(idx, animate)
    }

    private fun findIndexForId(@IdRes id: Int): Int {
        pillContainer.forEachIndexed { index, view ->
            if (view.tag == id) return index
        }
        return -1
    }

    private fun updatePillAppearance(selectedIdx: Int, animate: Boolean) {
        pillContainer.forEachIndexed { index, view ->
            val pill = view as LinearLayout
            val tv = pill.getChildAt(1) as? TextView

            if (index == selectedIdx) {
                pill.layoutParams = (pill.layoutParams as LinearLayout.LayoutParams).apply {
                    width = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0f
                    marginStart = pillSpacing
                    marginEnd = pillSpacing
                }

                if (animate) {
                    tv?.let {
                        it.isVisible = true
                        ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = animDuration
                            interpolator = DecelerateInterpolator()
                            addUpdateListener { anim ->
                                it.alpha = anim.animatedFraction
                                val margin = (4.dp * anim.animatedFraction).toInt()
                                (it.layoutParams as? LinearLayout.LayoutParams)?.marginStart = margin
                                it.requestLayout()
                            }
                            start()
                        }
                    }

                    ValueAnimator.ofFloat(1f, 1.02f).apply {
                        duration = animDuration
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            val scale = anim.animatedValue as Float
                            pill.scaleX = scale
                            pill.scaleY = scale
                        }
                        start()
                    }
                } else {
                    tv?.isVisible = true
                    tv?.alpha = 1f
                    (tv?.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 4.dp
                    pill.scaleX = 1.02f
                    pill.scaleY = 1.02f
                }
            } else {
                pill.layoutParams = (pill.layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    weight = 1f
                    marginStart = pillSpacing
                    marginEnd = pillSpacing
                }

                if (animate && tv?.isVisible == true) {
                    ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = animDuration
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            val progress = anim.animatedFraction
                            tv.alpha = 1f - progress
                            val margin = (4.dp * (1f - progress)).toInt()
                            (tv.layoutParams as? LinearLayout.LayoutParams)?.marginStart = margin
                            tv.requestLayout()
                        }
                        doOnEnd {
                            tv.isVisible = false
                            (tv.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 4.dp
                        }
                        start()
                    }

                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = animDuration
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            val scale = anim.animatedValue as Float
                            pill.scaleX = scale
                            pill.scaleY = scale
                        }
                        start()
                    }
                } else {
                    tv?.isVisible = false
                    tv?.alpha = 0f
                    (tv?.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 4.dp
                    pill.scaleX = 1f
                    pill.scaleY = 1f
                }
            }
        }
        pillContainer.requestLayout()
    }

    private fun moveHighlightToIndex(index: Int, animate: Boolean) {
        val target = pillContainer.getChildAt(index) ?: return

        target.post {
            val targetLeft = target.left + pillContainer.left
            val targetWidth = target.width

            if (animate) {
                val startX = highlight.x
                val startWidth = highlight.width.toFloat()

                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = animDuration
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        val t = animation.animatedFraction
                        highlight.x = startX + (targetLeft - startX) * t
                        val newWidth = startWidth + (targetWidth - startWidth) * t
                        highlight.layoutParams = (highlight.layoutParams as LayoutParams).apply {
                            width = newWidth.toInt()
                        }
                        highlight.requestLayout()
                    }
                    start()
                }
            } else {
                highlight.x = targetLeft.toFloat()
                highlight.layoutParams = (highlight.layoutParams as LayoutParams).apply {
                    width = targetWidth
                }
                highlight.requestLayout()
            }
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) { action() }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }
}

private inline fun ViewGroup.forEachIndexed(action: (index: Int, view: View) -> Unit) {
    for (i in 0 until childCount) action(i, getChildAt(i))
}
