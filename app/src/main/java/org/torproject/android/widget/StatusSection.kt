package org.torproject.android.widget

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.torproject.android.R

private const val ANIMATION_MILLIS = 300
private const val DISMISS_GUARD_MILLIS = 150L

private val ICON_SIZE = 24.dp
private val END_PADDING = 12.dp
private val POPUP_OFFSET_Y = 30.dp
private val ICON_TRANSFORM_ORIGIN = TransformOrigin(1f, 0f)

@Composable
fun StatusSection(
    httpPort: Int,
    socksPort: Int,
    orbotVersion: String,
    torVersion: String
) {
    var open by remember { mutableStateOf(false) }
    var mounted by remember { mutableStateOf(false) }
    var suppressDismissUntil by remember { mutableStateOf(0L) }

    fun toggle() {
        suppressDismissUntil = SystemClock.uptimeMillis() + DISMISS_GUARD_MILLIS
        if (open) {
            open = false
        } else if (mounted) {
            open = true
        } else {
            mounted = true
        }
    }

    LaunchedEffect(mounted) {
        if (mounted && !open) {
            open = true
        }
    }

    LaunchedEffect(open) {
        if (!open) {
            delay(ANIMATION_MILLIS.toLong())
            mounted = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(R.drawable.ic_tooltip),
            contentDescription = stringResource(R.string.menu_about),
            tint = colorResource(R.color.panel_card_image),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = END_PADDING)
                .size(ICON_SIZE)
                .clip(CircleShape)
                .background(colorResource(R.color.panel_widget_background))
                .clickable { toggle() }
                .padding(4.dp)
        )

        if (mounted) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(LocalDensity.current) {
                    IntOffset((-END_PADDING).roundToPx(), POPUP_OFFSET_Y.roundToPx())
                },
                onDismissRequest = {
                    if (SystemClock.uptimeMillis() >= suppressDismissUntil) {
                        open = false
                    }
                },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visible = open,
                    enter = scaleIn(
                        animationSpec = tween(ANIMATION_MILLIS),
                        transformOrigin = ICON_TRANSFORM_ORIGIN
                    ) + fadeIn(animationSpec = tween(ANIMATION_MILLIS)),
                    exit = scaleOut(
                        animationSpec = tween(ANIMATION_MILLIS),
                        transformOrigin = ICON_TRANSFORM_ORIGIN
                    ) + fadeOut(animationSpec = tween(ANIMATION_MILLIS))
                ) {
                    StatusPanel(httpPort, socksPort, orbotVersion, torVersion)
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(
    httpPort: Int,
    socksPort: Int,
    orbotVersion: String,
    torVersion: String
) {
    val notSet = "——"

    SelectionContainer {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(colorResource(R.color.panel_widget_background))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            StatusRow(
                stringResource(R.string.http_port),
                if (httpPort != -1) httpPort.toString() else notSet
            )

            StatusRow(
                stringResource(R.string.socks_port),
                if (socksPort != -1) socksPort.toString() else notSet
            )

            StatusRow("Orbot", orbotVersion)
            StatusRow("Tor", torVersion)
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "\t$value",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
