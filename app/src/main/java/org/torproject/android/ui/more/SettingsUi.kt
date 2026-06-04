package org.torproject.android.ui.more

import android.text.InputType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.torproject.android.R

private val PagePadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp)
private val PanelShape = RoundedCornerShape(16.dp)

@Composable
internal fun OrbotSettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(R.color.menu_header),
            background = colorResource(R.color.new_background),
            surface = colorResource(R.color.panel_widget_background),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = Color.White,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsToolbar(title = title, onBack = onBack)
            content()
        }
    }
}

@Composable
private fun SettingsToolbar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null
            )
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SettingsList(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.panel_widget_background), PanelShape)
                    .padding(vertical = 4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun SwitchSettingRow(
    checked: Boolean,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onChanged: (Boolean) -> Unit = {},
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChanged,
            )
        },
        onClick = {
            if (enabled) {
                onChanged(!checked)
            }
        },
    )
}

@Composable
internal fun EditTextSettingRow(
    value: String,
    title: String,
    summary: String? = null,
    dialogTitle: String = title,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    maxLength: Int? = null,
    password: Boolean = false,
    simpleSummary: Boolean = false,
    enabled: Boolean = true,
    onValueChanged: (String) -> Unit = {},
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val shownSummary = when {
        password && value.isNotEmpty() -> "•".repeat(value.length)
        simpleSummary && value.isNotBlank() -> value
        else -> summary
    }

    SettingRow(
        title = title,
        summary = shownSummary,
        enabled = enabled,
        onClick = { if (enabled) dialogVisible = true },
    )

    if (dialogVisible) {
        TextSettingDialog(
            title = dialogTitle,
            initialValue = value,
            inputType = inputType,
            maxLength = maxLength,
            password = password,
            onDismiss = { dialogVisible = false },
            onSave = {
                onValueChanged(it)
                dialogVisible = false
            },
        )
    }
}

@Composable
internal fun ListSettingRow(
    value: String,
    title: String,
    entries: List<String>,
    entryValues: List<String>,
    enabled: Boolean = true,
    onValueChanged: (String) -> Unit = {},
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val selectedLabel = entries.getOrNull(entryValues.indexOf(value)).orEmpty()

    SettingRow(
        title = title,
        summary = selectedLabel.takeIf { it.isNotBlank() },
        enabled = enabled,
        onClick = { if (enabled) dialogVisible = true },
    )

    if (dialogVisible) {
        ChoiceSettingDialog(
            title = title,
            entries = entries,
            entryValues = entryValues,
            selectedValue = value,
            onDismiss = { dialogVisible = false },
            onSelected = {
                onValueChanged(it)
                dialogVisible = false
            },
        )
    }
}

@Composable
internal fun SettingRow(
    title: String?,
    summary: String? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.45f
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, color = Color.White.copy(alpha = alpha), fontSize = 15.sp)
                }
                if (!summary.isNullOrBlank()) {
                    if (!title.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = summary,
                        color = Color.White.copy(alpha = alpha * 0.68f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun TextSettingDialog(
    title: String,
    initialValue: String,
    inputType: Int,
    maxLength: Int?,
    password: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialValue) {
        draft = initialValue
        errorMessage = null
    }

    val keyboardType = when {
        inputType and InputType.TYPE_CLASS_NUMBER == InputType.TYPE_CLASS_NUMBER -> KeyboardType.Number
        inputType and InputType.TYPE_TEXT_VARIATION_URI == InputType.TYPE_TEXT_VARIATION_URI -> KeyboardType.Uri
        password -> KeyboardType.Password
        else -> KeyboardType.Text
    }
    val multiline = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == InputType.TYPE_TEXT_FLAG_MULTI_LINE
    val isNumeric = inputType and InputType.TYPE_CLASS_NUMBER == InputType.TYPE_CLASS_NUMBER

    fun validateInput(value: String): String? = when {
        value.isBlank() -> null
        isNumeric -> runCatching { value.toInt() }.fold(
            onSuccess = { if (it < 0) "Must be non-negative" else if (it > 65535) "Must be 65535 or less" else null },
            onFailure = { "Must be a valid number" }
        )
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { next ->
                        draft = if (maxLength == null) next else next.take(maxLength)
                        errorMessage = validateInput(draft)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (multiline) 5 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    isError = errorMessage != null,
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = errorMessage == null && draft.isNotEmpty(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ChoiceSettingDialog(
    title: String,
    entries: List<String>,
    entryValues: List<String>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(entries.size) { index ->
                    val value = entryValues.getOrElse(index) { "" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedValue == value, onClick = { onSelected(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(entries[index])
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun stringArrayResourceList(id: Int): List<String> {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current

    return remember(id, configuration) {
        resources.getStringArray(id).toList()
    }
}
