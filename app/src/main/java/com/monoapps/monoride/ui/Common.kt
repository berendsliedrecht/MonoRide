package com.monoapps.monoride.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

/** Hand-built top bar: TopAppBarMMD renders with a transparent background on the Kompakt. */
@Composable
fun AppBar(
    title: @Composable () -> Unit,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (navigationIcon == null) 12.dp else 4.dp),
            ) {
                title()
            }
            actions()
        }
        HorizontalDividerMMD()
    }
}

@Composable
fun BlackButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    ButtonMMD(
        onClick = onClick,
        enabled = enabled,
        // Pure black/white so e-ink doesn't dither the fill
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
            disabledContainerColor = Color.White,
            disabledContentColor = Color.Black,
        ),
        border = BorderStroke(2.dp, Color.Black),
        modifier = modifier,
    ) {
        TextMMD(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ConfirmDialog(message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color.White, border = BorderStroke(2.dp, Color.Black)) {
            Column(modifier = Modifier.padding(20.dp)) {
                TextMMD(message, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlackButton(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
                    OutlinedButtonMMD(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        TextMMD("Cancel", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

fun elapsed(millis: Long): String {
    val s = millis / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
