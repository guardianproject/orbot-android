package org.torproject.android.ui.widget

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
                topMargin = 12.dp
                bottomMargin = 12.dp
                leftMargin = 12.dp
                rightMargin = 12.dp
            }
            isClickable = false
            z = -10f
        }
        addView(highlight)

        pillContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
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
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_pill_transparent)
            elevation = 8f * resources.displayMetrics.density
        }

        val iv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                marginEnd = 4.dp
            }
            setImageDrawable(icon)
            imageTintList = ContextCompat.getColorStateList(context, R.color.pill_icon_color)
        }

        val tv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.pill_text_color))
            isVisible = false
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
        moveHighlightToIndex(idx, animate)
        updatePillAppearance(idx)
    }

    private fun findIndexForId(@IdRes id: Int): Int {
        pillContainer.forEachIndexed { index, view ->
            if (view.tag == id) return index
        }
        return -1
    }

    private fun updatePillAppearance(selectedIdx: Int) {
        pillContainer.forEachIndexed { index, view ->
            val pill = view as LinearLayout
            val iv = pill.getChildAt(0) as? ImageView
            val tv = pill.getChildAt(1) as? TextView

            if (index == selectedIdx) {
                tv?.isVisible = true
                pill.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 8.dp
                    marginEnd = 8.dp
                }
                (iv?.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 8.dp
                iv?.requestLayout()
                pill.scaleX = 1.02f
                pill.scaleY = 1.02f
            } else {
                tv?.isVisible = false
                pill.layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                (iv?.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 0
                iv?.requestLayout()
                pill.scaleX = 1f
                pill.scaleY = 1f
            }
        }
    }

    private fun moveHighlightToIndex(index: Int, animate: Boolean) {
        val target = pillContainer.getChildAt(index) ?: return

        target.post {
            val targetLeft = target.left
            val targetWidth = target.width

            if (animate) {
                val startX = highlight.x
                val startWidth = highlight.width.toFloat()

                val deltaX = targetLeft - startX
                val deltaWidth = targetWidth - startWidth

                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = animDuration
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        val t = animation.animatedFraction
                        highlight.x = startX + deltaX * t
                        val newWidth = startWidth + deltaWidth * t
                        highlight.layoutParams = (highlight.layoutParams).apply {
                            width = newWidth.toInt()
                        }
                        highlight.requestLayout()
                    }
                }
                animator.start()
            } else {
                highlight.x = targetLeft.toFloat()
                highlight.layoutParams = highlight.layoutParams.apply {
                    width = targetWidth
                }
                highlight.requestLayout()
            }
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}

private inline fun ViewGroup.forEachIndexed(action: (index: Int, view: View) -> Unit) {
    for (i in 0 until childCount) action(i, getChildAt(i))
}
