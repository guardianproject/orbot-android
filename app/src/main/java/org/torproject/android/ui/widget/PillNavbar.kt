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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.torproject.android.R

class PillNavBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val _selectedId = MutableStateFlow(-1)
    val selectedId = _selectedId.asStateFlow()

    private val pillContainer: LinearLayout
    private val highlight: View
    private val animDuration = 260L
    private val highlightMargin = 12.dp
    private val pillSpacing = 0.dp

    var bottomNav: BottomNavigationView? = null
        set(value) {
            field = value
            value?.let { bn ->
                buildPillsFromBottomNav(bn)
                bn.setOnItemSelectedListener { item ->
                    if (_selectedId.value == item.itemId) return@setOnItemSelectedListener true
                    _selectedId.value = item.itemId
                    true
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

        CoroutineScope(Dispatchers.Main).launch {
            selectedId.collect { id ->
                if (id == -1) return@collect
                selectById(id, animate = true)
                bottomNav?.selectedItemId = id
            }
        }
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
        _selectedId.value = initialId
        selectById(initialId, animate = false)

        post {
            val idx = findIndexForId(initialId)
            if (idx != -1) moveHighlightToIndex(idx, animate = false)
        }
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
            layoutParams = LinearLayout.LayoutParams(
                24.dp,
                24.dp
            ).apply {
                marginStart = 8.dp
            }
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
            if (_selectedId.value == id) return@setOnClickListener
            _selectedId.value = id
        }

        pill.tag = id
        return pill
    }

    private fun selectById(@IdRes id: Int, animate: Boolean) {
        val idx = findIndexForId(id)
        if (idx == -1) return
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
            val icon = pill.getChildAt(0) as ImageView
            val text = pill.getChildAt(1) as TextView

            val isSelected = index == selectedIdx

            if (isSelected) {
                pill.layoutParams = (pill.layoutParams as LinearLayout.LayoutParams).apply {
                    width = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0f
                    marginStart = pillSpacing
                    marginEnd = pillSpacing
                }

                if (animate) {
                    text.isVisible = true
                    text.alpha = 0f
                    (text.layoutParams as LinearLayout.LayoutParams).marginStart = 4.dp

                    text.animate()
                        .alpha(1f)
                        .setDuration(animDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    icon.animate()
                        .translationX(-6.dp.toFloat())
                        .setDuration(animDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    pill.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(animDuration)
                        .start()
                } else {
                    text.isVisible = true
                    text.alpha = 1f
                    (text.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 4.dp
                    icon.translationX = -6.dp.toFloat()
                    pill.scaleX = 1.02f
                    pill.scaleY = 1.02f
                }
            } else {
                pill.layoutParams = (pill.layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    weight = 1f
                }

                if (animate) {
                    if (text.isVisible) {
                        text.animate()
                            .alpha(0f)
                            .setDuration(animDuration / 2)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                text.isVisible = false
                                text.alpha = 0f
                            }
                            .start()
                    }

                    icon.animate()
                        .translationX(0f)
                        .setDuration(animDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    pill.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(animDuration)
                        .start()
                } else {
                    text.isVisible = false
                    text.alpha = 0f
                    icon.translationX = 0f
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
}

private inline fun ViewGroup.forEachIndexed(action: (index: Int, view: View) -> Unit) {
    for (i in 0 until childCount) action(i, getChildAt(i))
}
