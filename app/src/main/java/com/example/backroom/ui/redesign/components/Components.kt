package com.example.backroom.ui.redesign.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.redesign.theme.backroomColors

/** The one action a screen commits to. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = backroomColors.accent,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(29.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = backroomColors.onAccent,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A way out that must not compete with the primary action. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = backroomColors.muted,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Used where two choices carry comparable weight. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = backroomColors.line,
    textColor: Color = backroomColors.ink,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = textColor,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

/**
 * Concentric discs, largest first. The app's one recurring image:
 * presence without a face.
 */
@Composable
fun Rings(
    sizes: List<Dp>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val breath = rememberInfiniteTransition(label = "rings")
    Box(modifier = modifier.size(sizes.first()), contentAlignment = Alignment.Center) {
        sizes.forEachIndexed { i, s ->
            // Only the outer rings breathe, and slowly: the core stays still so the
            // shape reads as alive rather than as a progress indicator.
            val spec = infiniteRepeatable<Float>(
                animation = tween(durationMillis = 7000, delayMillis = i * 900),
                repeatMode = RepeatMode.Reverse,
            )
            val scale by breath.animateFloat(
                initialValue = if (pulse && i < sizes.lastIndex) 0.88f else 1f,
                targetValue = 1f,
                animationSpec = spec,
                label = "ringScale$i",
            )
            Box(
                Modifier
                    .size(s)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(colors[i]),
            )
        }
    }
}

/** A hairline in the page's line colour. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(backroomColors.line),
    )
}

/** One of Welcome's three guarantees. */
@Composable
fun GuaranteeRow(text: String, icon: @Composable () -> Unit) {
    Column {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Text(text, style = MaterialTheme.typography.bodyMedium, color = backroomColors.ink)
        }
    }
}
