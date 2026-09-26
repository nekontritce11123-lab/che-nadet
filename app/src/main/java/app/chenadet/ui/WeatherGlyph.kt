package app.chenadet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.chenadet.core.WeatherModifier

/** Lightweight vector illustration. Decorative: the condition is always present as text. */
@Composable
fun WeatherGlyph(modifier: WeatherModifier?, night: Boolean) {
    val ink = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.primaryContainer
    Canvas(Modifier.size(64.dp).clearAndSetSemantics { }) {
        val s = size.minDimension
        if (night) {
            drawCircle(ink, s * .28f, Offset(s * .48f, s * .40f))
            drawCircle(surface, s * .25f, Offset(s * .60f, s * .29f))
        } else {
            drawCircle(ink, s * .19f, Offset(s * .42f, s * .35f))
            for (i in 0..7) {
                val angle = i * Math.PI / 4
                val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                drawLine(ink, Offset(s * .42f, s * .35f) + direction * (s * .25f),
                    Offset(s * .42f, s * .35f) + direction * (s * .30f), s * .035f, StrokeCap.Round)
            }
        }
        if (modifier != WeatherModifier.SUNNY) {
            drawCircle(ink, s * .19f, Offset(s * .38f, s * .60f))
            drawCircle(ink, s * .23f, Offset(s * .59f, s * .52f))
            drawCircle(ink, s * .15f, Offset(s * .79f, s * .64f))
            drawRoundRect(ink, Offset(s * .27f, s * .59f), Size(s * .58f, s * .20f), CornerRadius(s * .09f))
        }
        if (modifier == WeatherModifier.RAINY) {
            for (x in listOf(.38f, .58f, .78f)) drawLine(ink, Offset(s * x, s * .84f), Offset(s * (x - .06f), s * .97f), s * .035f, StrokeCap.Round)
        }
        if (modifier == WeatherModifier.THUNDERSTORM) {
            val bolt = Path().apply { moveTo(s * .57f, s * .73f); lineTo(s * .44f, s * .90f); lineTo(s * .57f, s * .90f); lineTo(s * .51f, s); lineTo(s * .71f, s * .83f); lineTo(s * .59f, s * .83f); close() }
            drawPath(bolt, surface)
        }
        if (modifier == WeatherModifier.SNOWY) {
            for (x in listOf(.40f, .65f)) {
                drawLine(ink, Offset(s * (x - .04f), s * .9f), Offset(s * (x + .04f), s * .9f), s * .025f)
                drawLine(ink, Offset(s * x, s * .86f), Offset(s * x, s * .94f), s * .025f)
            }
        }
    }
}
