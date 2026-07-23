package org.torproject.android.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.torproject.android.R

@Composable
fun StatusSection(
    httpPort: Int,
    socksPort: Int,
    orbotVersion: String,
    torVersion: String
) {
    val notSet = "——"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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

@Composable
fun StatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.6f),
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
