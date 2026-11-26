package com.example.backroom.ui.redesign.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

/** Back arrow plus the screen's name. Used by the screens that are not the main flow. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    val c = backroomColors
    Column {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = c.muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
    }
}

/** A tappable row: name on the left, current value and a chevron on the right. */
@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    destructive: Boolean = false,
) {
    val c = backroomColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) c.danger else c.ink,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = c.muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A row whose whole point is the switch, so it carries no explanatory line. */
@Composable
fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = backroomColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
            ),
        )
    }
}

/** A quiet label that separates groups without shouting in capitals. */
@Composable
fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = backroomColors.muted,
        modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
    )
}
