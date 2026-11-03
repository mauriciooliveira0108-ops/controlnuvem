package com.flippermobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flippermobile.ui.theme.FlipperColors

/**
 * Estrutura comum de tela: cabeçalho retrô, área de conteúdo limitada em largura
 * (tablets/paisagem) e host de snackbar.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState? = null,
    icon: List<String>? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(FlipperColors.Orange)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clickable(role = Role.Button, onClick = onBack)
                            .semantics { contentDescription = "Voltar" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("<", style = MaterialTheme.typography.headlineSmall, color = FlipperColors.Black)
                    }
                }
                if (icon != null) {
                    PixelArt(icon, size = 22.dp, color = FlipperColors.Black, accent = FlipperColors.Black)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = FlipperColors.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                content()
            }
        }
        if (snackbarHostState != null) {
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
fun HeaderAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .height(36.dp)
            .background(if (enabled) FlipperColors.Black else FlipperColors.OrangeDim)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (enabled) FlipperColors.Orange else FlipperColors.Black)
    }
}
