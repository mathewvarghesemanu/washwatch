package space.mathewvarghese.washwatch

import space.mathewvarghese.washwatch.ui.theme.LightPrimary
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How long the intro stays fully visible; the caller's 200ms fade-out brings the total to two seconds. */
const val INTRO_VISIBLE_MILLIS = 1800L

private data class IntroStep(val icon: ImageVector, val text: String)

private val steps = listOf(
    IntroStep(Icons.Filled.PhoneAndroid, "Stick it on"),
    IntroStep(Icons.Filled.Vibration, "It feels the spin"),
    IntroStep(Icons.Filled.NotificationsActive, "Buzzes when done"),
)

/** Two-second intro on brand blue: a shaking washer, then the three-step pitch popping in one after another. */
@Composable
fun IntroSplash() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1100, easing = LinearEasing)) }

    val shake by rememberInfiniteTransition(label = "shake").animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(70, easing = LinearEasing), RepeatMode.Reverse),
        label = "shakeAngle",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(LightPrimary)
            // Swallow taps so nothing underneath reacts during the intro.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(132.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.LocalLaundryService,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(84.dp).graphicsLayer { rotationZ = shake },
                )
            }
            Text("WashWatch", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 40.dp)) {
                steps.forEachIndexed { index, step ->
                    // Each step fades and slides in over its own slice of the timeline, staggered by a quarter.
                    val t = FastOutSlowInEasing.transform(((progress.value - index * 0.25f) / 0.5f).coerceIn(0f, 1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = t
                                translationX = (1f - t) * 60.dp.toPx()
                            },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                        ) {
                            Icon(step.icon, contentDescription = null, tint = LightPrimary, modifier = Modifier.size(22.dp))
                        }
                        Text(
                            step.text,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
