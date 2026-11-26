package com.synq.features.sports.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private data class FountainParticleSpec(
    val angleRad: Float,
    val distanceDp: Float,
    val radiusDp: Float,
    val color: Color,
    val isStar: Boolean
)

private data class ConfettiPieceSpec(
    val fromLeft: Boolean,
    val startYFrac: Float,
    val vxDp: Float,
    val vyDp: Float,
    val delayFrac: Float,
    val rotSpeed: Float,
    val phaseOffset: Float,
    val color: Color,
    val isStar: Boolean
)

internal fun liveEventAccentColor(type: LiveEventType): Color = when (type) {
    LiveEventType.FOUR -> Color(0xFF2563EB)
    LiveEventType.SIX -> Color(0xFF9B51E0)
    LiveEventType.WICKET -> Color(0xFFEF4444)
    LiveEventType.NO_BALL -> Color(0xFFF59E0B)
    LiveEventType.FIFTY -> Color(0xFFF59E0B)
    LiveEventType.CENTURY -> Color(0xFFFBBF24)
}

/**
 * Animated card border color that glows briefly with the live event color and returns to [baseColor].
 */
@Composable
fun rememberAnimatedEventBorderColor(
    eventId: Long?,
    eventType: LiveEventType?,
    baseColor: Color
): Color {
    var showGlow by remember { mutableStateOf(false) }

    LaunchedEffect(eventId, eventType) {
        if (eventId != null && eventType != null) {
            showGlow = true
            delay(if (eventType == LiveEventType.NO_BALL) 2200L else 1800L)
            showGlow = false
        } else {
            showGlow = false
        }
    }

    val target = if (showGlow && eventType != null) {
        liveEventAccentColor(eventType).copy(alpha = 0.85f)
    } else {
        baseColor
    }
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (showGlow) 280 else 600),
        label = "liveCardBorder"
    )
    return animated
}

private data class FloatingPillStyle(
    val label: String,
    val textColor: Color,
    val bgColor: Color,
    val borderColor: Color
)

/**
 * Floating translucent pill (`+4`, `+6`, `W`, or `NB`) anchored above the changed score on
 * MatchDetailsSummaryCard without taking layout space, carrying attached M4 boundary sparks/stars
 * for `FOUR` and `SIX`.
 */
@Composable
fun FloatingScoreEventPill(
    eventType: LiveEventType?,
    triggerId: Long?,
    modifier: Modifier = Modifier
) {
    val pillStyle = when (eventType) {
        LiveEventType.FOUR -> FloatingPillStyle("+4", Color(0xFF60A5FA), Color(0x382563EB), Color(0x7360A5FA))
        LiveEventType.SIX -> FloatingPillStyle("+6", Color(0xFFC084FC), Color(0x389B51E0), Color(0x73C084FC))
        LiveEventType.WICKET -> FloatingPillStyle("W", Color(0xFFF87171), Color(0x38EF4444), Color(0x73F87171))
        LiveEventType.NO_BALL -> FloatingPillStyle("NB", Color(0xFFFBBF24), Color(0x38F59E0B), Color(0x80FBBF24))
        else -> null
    }
    val progress = remember { Animatable(0f) }
    val particleProgress = remember { Animatable(0f) }
    val boundaryType = eventType?.takeIf { it == LiveEventType.FOUR || it == LiveEventType.SIX }

    val particles = remember(triggerId, boundaryType) {
        if (triggerId == null || boundaryType == null) {
            emptyList()
        } else {
            val isSix = boundaryType == LiveEventType.SIX
            val rng = Random(triggerId * 31L + boundaryType.ordinal)
            val count = if (isSix) 14 else 8
            val palette = if (isSix) {
                listOf(Color(0xFF9B51E0), Color(0xFFC084FC), Color(0xFFE879F9))
            } else {
                listOf(Color(0xFF2563EB), Color(0xFF60A5FA), Color(0xFF93C5FD))
            }

            List(count) { idx ->
                val baseAngle = (idx.toFloat() / count) * (2.0 * PI)
                val jitter = (rng.nextFloat() - 0.5f) * 0.45f
                val distanceDp = 14f + rng.nextFloat() * (if (isSix) 40f else 26f)
                FountainParticleSpec(
                    angleRad = (baseAngle + jitter).toFloat(),
                    distanceDp = distanceDp,
                    radiusDp = if (isSix) 4.2f else 2.7f,
                    color = palette[idx % palette.size],
                    isStar = isSix
                )
            }
        }
    }

    LaunchedEffect(triggerId, pillStyle) {
        if (triggerId == null || pillStyle == null) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2450, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(triggerId, boundaryType) {
        if (triggerId == null || boundaryType == null) {
            particleProgress.snapTo(0f)
        } else {
            particleProgress.snapTo(0f)
            particleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (boundaryType == LiveEventType.SIX) 1300 else 1050,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    if (triggerId == null || pillStyle == null) return
    val t = progress.value
    if (t <= 0f || t >= 1f) return

    val alpha = when {
        t < 0.12f -> t / 0.12f
        t < 0.82f -> 1f
        else -> (1f - (t - 0.82f) / 0.18f).coerceIn(0f, 1f)
    }
    val offsetYDp = when {
        t < 0.12f -> 6f - 7f * (t / 0.12f)
        t < 0.82f -> -1f - 2f * ((t - 0.12f) / 0.70f)
        else -> -3f - 8f * ((t - 0.82f) / 0.18f)
    }
    val scale = when {
        t < 0.12f -> 0.78f + 0.27f * (t / 0.12f)
        t < 0.82f -> 1.05f - 0.05f * ((t - 0.12f) / 0.70f)
        else -> 1.0f - 0.06f * ((t - 0.82f) / 0.18f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationY = offsetYDp.dp.toPx()
            }
            .drawBehind {
                val pt = particleProgress.value
                if (pt > 0f && pt < 1f && particles.isNotEmpty()) {
                    val originX = size.width / 2f
                    val originY = size.height / 2f
                    val easeOut = 1f - (1f - pt).pow(2)
                    val pAlpha = (1f - pt) * 0.90f
                    for (p in particles) {
                        val distPx = p.distanceDp.dp.toPx() * easeOut
                        val px = originX + cos(p.angleRad) * distPx
                        val py = originY + sin(p.angleRad) * distPx * 0.75f
                        val radPx = p.radiusDp.dp.toPx()
                        val c = p.color.copy(alpha = pAlpha)
                        if (p.isStar) {
                            drawStar4(center = Offset(px, py), radius = radPx, rotationRad = pt * 3.2f, color = c)
                        } else {
                            drawCircle(color = c, radius = radPx, center = Offset(px, py))
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                }
                .clip(CircleShape)
                .background(pillStyle.bgColor)
                .border(1.dp, pillStyle.borderColor, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = pillStyle.label,
                color = pillStyle.textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Shared kinetic background overlay for Bowler `W` and Batter `4`/`6` events:
 * 3 travelling flash sweeps across the row background while the bold glyph zooms in,
 * rotates left-to-right 3 times, stops upright briefly, zooms out slightly, and fades out.
 */
@Composable
fun KineticGlyphSweepOverlay(
    glyph: String,
    accentColor: Color,
    triggerId: Long?,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(triggerId, glyph) {
        if (triggerId == null) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2500, easing = FastOutSlowInEasing)
            )
        }
    }

    if (triggerId == null) return

    val measuredGlyph = remember(glyph) {
        textMeasurer.measure(
            text = glyph,
            style = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Black)
        )
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas

        // 3 consecutive travelling background flash sweeps
        val passes = 3
        val passSpan = 1f / passes
        for (p in 0 until passes) {
            val pt = (t - p * passSpan) / passSpan
            if (pt in 0f..1f) {
                val sweepW = size.width * 0.52f
                val leftX = -sweepW + pt * (size.width + sweepW * 2f)
                val passAlpha = sin(pt * PI).toFloat() * 0.17f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, accentColor.copy(alpha = passAlpha), Color.Transparent),
                        startX = leftX,
                        endX = leftX + sweepW
                    )
                )
            }
        }

        val center = Offset(size.width * 0.56f, size.height * 0.52f)

        // Subtle expanding shockwave ring at zoom peak
        val ringT = (t / 0.34f).coerceIn(0f, 1f)
        if (ringT < 1f) {
            drawCircle(
                color = accentColor.copy(alpha = (1f - ringT) * 0.30f),
                radius = 8.dp.toPx() + 30.dp.toPx() * ringT,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 5-beat choreography: zoom in -> rotate L->R 3x -> stop -> zoom out a bit -> fade
        var scaleX = 1f
        var scaleY = 1f
        var alpha = 1f

        when {
            t < 0.18f -> {
                val z = t / 0.18f
                val easeOut = 1f - (1f - z).pow(3)
                val s = 0.25f + 0.90f * easeOut
                scaleX = s
                scaleY = s
                alpha = (z / 0.45f).coerceIn(0f, 1f)
            }
            t < 0.60f -> {
                val r = (t - 0.18f) / 0.42f
                val easeOutRot = 1f - (1f - r).pow(2)
                val baseScale = 1.15f - 0.07f * easeOutRot
                scaleX = baseScale * cos(easeOutRot * (2.0 * PI * 3.0)).toFloat()
                scaleY = baseScale
            }
            t < 0.74f -> {
                scaleX = 1.08f
                scaleY = 1.08f
            }
            t < 0.88f -> {
                val zo = (t - 0.74f) / 0.14f
                val smooth = zo * zo * (3f - 2f * zo)
                val s = 1.08f - 0.22f * smooth
                scaleX = s
                scaleY = s
            }
            else -> {
                val f = (t - 0.88f) / 0.12f
                scaleX = 0.86f
                scaleY = 0.86f
                alpha = (1f - f).coerceIn(0f, 1f)
            }
        }

        val safeScaleX = if (kotlin.math.abs(scaleX) < 0.02f) 0.02f else scaleX
        withTransform({
            scale(scaleX = safeScaleX, scaleY = scaleY, pivot = center)
        }) {
            drawText(
                textLayoutResult = measuredGlyph,
                color = accentColor.copy(alpha = 0.28f * alpha),
                topLeft = Offset(
                    center.x - measuredGlyph.size.width / 2f,
                    center.y - measuredGlyph.size.height / 2f
                )
            )
        }
    }
}

/**
 * Out-batsman row background animation matched to IconScout `cricket-wicket-animation_15700483`:
 * emerald pitch bar & green turf oval, red ball with white seam bouncing onto leftmost stump,
 * 3 golden impact lines, anchored amber stumps splaying `\  /  /`, navy bails splitting upward,
 * followed by an "OUT" stamp that holds cleanly until the next live update replaces the row.
 */
@Composable
fun IconScoutWicketOverlay(
    triggerId: Long?,
    modifier: Modifier = Modifier
) {
    if (triggerId == null) return

    val strikeProgress = remember(triggerId) { Animatable(0f) }
    val outBadgeProgress = remember(triggerId) { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val measuredOut = remember {
        textMeasurer.measure(
            text = "OUT",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp
            )
        )
    }

    LaunchedEffect(triggerId) {
        strikeProgress.snapTo(0f)
        strikeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(triggerId) {
        outBadgeProgress.snapTo(0f)
        delay(640L) // Right after the ball strikes the stump
        outBadgeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val t = strikeProgress.value
        val outT = outBadgeProgress.value
        if (t <= 0f) return@Canvas

        val entryFade = (t / 0.08f).coerceIn(0f, 1f)
        val alphaBase = 0.56f * entryFade

        val cx = size.width * 0.74f
        val baseY = size.height * 0.86f
        val sh = 32.dp.toPx()
        val sw = 3.8.dp.toPx()
        val gap = 7.8.dp.toPx()
        val impactAt = 0.42f

        // 1. Emerald pitch bar & dark green turf oval (cleanly tucked into the 56%..82% open lane)
        drawRoundRect(
            color = Color(0xFF00A878).copy(alpha = alphaBase),
            topLeft = Offset(cx - 34.dp.toPx(), baseY - 1.5.dp.toPx()),
            size = Size(50.dp.toPx(), 3.2.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawOval(
            color = Color(0xFF007E5A).copy(alpha = alphaBase),
            topLeft = Offset(cx - 14.dp.toPx(), baseY - 2.dp.toPx()),
            size = Size(28.dp.toPx(), 5.dp.toPx())
        )

        // 2. Springy splay ease for anchored stumps (\  /  /), holding splayed pose
        val rawImp = ((t - impactAt) / 0.28f).coerceIn(0f, 1f)
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val splay = if (rawImp == 0f) 0f else 1f + c3 * (rawImp - 1f).pow(3) + c1 * (rawImp - 1f).pow(2)

        val amberStump = Color(0xFFFFC46B).copy(alpha = alphaBase)
        val orangeBand = Color(0xFFE07A2B).copy(alpha = alphaBase)
        val pivotsAndAngles = listOf(
            cx - gap to -22f * splay,
            cx to 14f * splay,
            cx + gap to 24f * splay
        )
        for ((px, angle) in pivotsAndAngles) {
            rotate(degrees = angle, pivot = Offset(px, baseY)) {
                drawRoundRect(
                    color = amberStump,
                    topLeft = Offset(px - sw / 2f, baseY - sh),
                    size = Size(sw, sh),
                    cornerRadius = CornerRadius(sw / 2f)
                )
                drawRect(
                    color = orangeBand,
                    topLeft = Offset(px - sw / 2f, baseY - sh * 0.28f),
                    size = Size(sw, sh * 0.13f)
                )
            }
        }

        // 3. Navy-blue bails (resting bar -> split upward & exit cleanly)
        val bailNavy = Color(0xFF1E2B6B)
        val bailH = 3.4.dp.toPx()
        if (t < impactAt) {
            drawRoundRect(
                color = bailNavy.copy(alpha = alphaBase),
                topLeft = Offset(cx - gap - sw * 0.5f, baseY - sh - bailH),
                size = Size(gap * 2f + sw, bailH),
                cornerRadius = CornerRadius(bailH / 2f)
            )
        } else {
            val bailT = ((t - impactAt) / 0.50f).coerceIn(0f, 1f)
            if (bailT < 1f) {
                val bailEase = 1f - (1f - bailT).pow(2)
                val bailAlpha = if (bailT > 0.65f) (1f - bailT) / 0.35f else 1f
                val bailColor = bailNavy.copy(alpha = alphaBase * bailAlpha)
                val riseY = baseY - sh - bailH - 30.dp.toPx() * bailEase
                val bailW = gap + 0.5.dp.toPx()

                val leftBailCenter = Offset(cx - gap * 0.52f - 5.dp.toPx() * bailEase, riseY)
                rotate(degrees = -24f * bailEase, pivot = leftBailCenter) {
                    drawRoundRect(
                        color = bailColor,
                        topLeft = Offset(leftBailCenter.x - bailW / 2f, leftBailCenter.y - bailH / 2f),
                        size = Size(bailW, bailH),
                        cornerRadius = CornerRadius(bailH / 2f)
                    )
                }

                val rightBailCenter = Offset(cx + gap * 0.52f + 6.dp.toPx() * bailEase, riseY - 2.dp.toPx() * bailEase)
                rotate(degrees = 24f * bailEase, pivot = rightBailCenter) {
                    drawRoundRect(
                        color = bailColor,
                        topLeft = Offset(rightBailCenter.x - bailW / 2f, rightBailCenter.y - bailH / 2f),
                        size = Size(bailW, bailH),
                        cornerRadius = CornerRadius(bailH / 2f)
                    )
                }
            }
        }

        // 4. Red ball entering from upper-left, bouncing once, hitting left stump
        val bounceX = cx - 22.dp.toPx()
        val bounceY = baseY - 4.8.dp.toPx()
        val hitX = cx - gap - 3.dp.toPx()
        val hitY = baseY - sh * 0.38f

        var bx = 0f
        var byBall = 0f
        var ballScale = 0f
        when {
            t < 0.24f -> {
                val u = t / 0.24f
                bx = (cx - 38.dp.toPx()) + (bounceX - (cx - 38.dp.toPx())) * u
                byBall = (baseY - 34.dp.toPx()) + (bounceY - (baseY - 34.dp.toPx())) * (u * u)
                ballScale = 1f
            }
            t < impactAt -> {
                val u = (t - 0.24f) / (impactAt - 0.24f)
                bx = bounceX + (hitX - bounceX) * u
                byBall = bounceY + (hitY - bounceY) * sin(u * (PI / 2.0)).toFloat()
                ballScale = 1f
            }
            t < impactAt + 0.14f -> {
                val u = (t - impactAt) / 0.14f
                bx = hitX - 4.dp.toPx() * u
                byBall = hitY
                ballScale = (1f - u).coerceIn(0f, 1f)
            }
        }

        if (ballScale > 0.02f) {
            val r = 5.2.dp.toPx() * ballScale
            val centerBall = Offset(bx, byBall)
            drawCircle(color = Color(0xFFFF4D4D).copy(alpha = alphaBase), radius = r, center = centerBall)
            val seamR = r * 0.62f
            drawArc(
                color = Color.White.copy(alpha = alphaBase),
                startAngle = -43f,
                sweepAngle = 92f,
                useCenter = false,
                topLeft = Offset(centerBall.x - seamR, centerBall.y - seamR),
                size = Size(seamR * 2f, seamR * 2f),
                style = Stroke(width = 1.2.dp.toPx() * ballScale, cap = StrokeCap.Round)
            )
        }

        // 5. Three golden-yellow radiating burst lines on impact
        if (t >= impactAt && t < impactAt + 0.24f) {
            val bt = (t - impactAt) / 0.24f
            val burstAlpha = if (bt < 0.3f) bt / 0.3f else 1f - (bt - 0.3f) / 0.7f
            val burstColor = Color(0xFFFACC15).copy(alpha = alphaBase * burstAlpha)
            for (deg in listOf(145.0, 180.0, 215.0)) {
                val rad = Math.toRadians(deg)
                val r0 = (5f + 3f * bt).dp.toPx()
                val r1 = r0 + 7.dp.toPx()
                drawLine(
                    color = burstColor,
                    start = Offset(hitX + (cos(rad) * r0).toFloat(), hitY + (sin(rad) * r0).toFloat()),
                    end = Offset(hitX + (cos(rad) * r1).toFloat(), hitY + (sin(rad) * r1).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 6. "OUT" watermark arrives after stump impact cleanly in open center space (no stat overlap)
        if (outT > 0f) {
            val outEase = 1f - (1f - outT).pow(3)
            val outScale = 0.72f + 0.28f * outEase
            val outCenter = Offset(size.width * 0.55f, size.height * 0.44f)
            withTransform({
                scale(scaleX = outScale, scaleY = outScale, pivot = outCenter)
            }) {
                drawText(
                    textLayoutResult = measuredOut,
                    color = Color(0xFFEF4444).copy(alpha = 0.34f * outEase),
                    topLeft = Offset(
                        outCenter.x - measuredOut.size.width / 2f,
                        outCenter.y - measuredOut.size.height / 2f
                    )
                )
            }
        }
    }
}

/**
 * Milestone (`50` / `100`) background animation on LiveBatterRow (`B3+`):
 * stays active until the next live update arrives — left-to-right gold shimmer wipe revealing
 * larger `FIFTY`/`HUNDRED` text + raised bat silhouette, while dual-side confetti cannons
 * fire repeatedly from both edges.
 */
@Composable
fun MilestoneRaisedBatOverlay(
    eventType: LiveEventType?,
    triggerId: Long?,
    modifier: Modifier = Modifier
) {
    if ((eventType != LiveEventType.FIFTY && eventType != LiveEventType.CENTURY) || triggerId == null) {
        return
    }

    val revealProgress = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }
    var confettiPass by remember(triggerId, eventType) { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()

    val confetti = remember(triggerId, eventType, confettiPass) {
        val isHundred = eventType == LiveEventType.CENTURY
        val rng = Random(triggerId * 41L + eventType.ordinal + confettiPass * 997L)
        val palette = if (isHundred) {
            listOf(
                Color(0xFFFBBF24), Color(0xFFFDE047), Color(0xFFF59E0B),
                Color.White, Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFF34D399)
            )
        } else {
            listOf(
                Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFFDE68A),
                Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFFB7185)
            )
        }
        val perSide = if (isHundred) 18 else 12
        buildList {
            for (side in 0..1) {
                val fromLeft = side == 0
                for (i in 0 until perSide) {
                    add(
                        ConfettiPieceSpec(
                            fromLeft = fromLeft,
                            startYFrac = 0.25f + rng.nextFloat() * 0.55f,
                            vxDp = 36f + rng.nextFloat() * 68f,
                            vyDp = -(16f + rng.nextFloat() * 34f),
                            delayFrac = rng.nextFloat() * 0.18f,
                            rotSpeed = (rng.nextFloat() - 0.5f) * 360f,
                            phaseOffset = i.toFloat(),
                            color = palette[i % palette.size],
                            isStar = i % 4 == 0
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(triggerId, eventType) {
        revealProgress.snapTo(0f)
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(triggerId, eventType) {
        while (isActive) {
            confettiProgress.snapTo(0f)
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (eventType == LiveEventType.CENTURY) 2100 else 1900,
                    easing = FastOutSlowInEasing
                )
            )
            delay(450L)
            confettiPass++
        }
    }

    val gold = liveEventAccentColor(eventType)
    val labelText = if (eventType == LiveEventType.CENTURY) "HUNDRED" else "FIFTY"
    val measuredLabel = remember(labelText) {
        textMeasurer.measure(
            text = labelText,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
        )
    }

    Canvas(modifier = modifier) {
        val rev = revealProgress.value
        val ct = confettiProgress.value

        // Persistent metallic gold shimmer wipe across the row background
        val wipeW = size.width * rev.coerceIn(0f, 1f)
        if (wipeW > 1f) {
            val peakAlpha = if (eventType == LiveEventType.CENTURY) 0.18f else 0.14f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(gold.copy(alpha = 0.05f), gold.copy(alpha = peakAlpha), Color.Transparent),
                    startX = 0f,
                    endX = wipeW
                ),
                size = Size(wipeW, size.height)
            )
        }

        // Larger FIFTY / HUNDRED watermark text + raised cricket bat silhouette (holds cleanly)
        if (rev > 0.20f) {
            val e = ((rev - 0.20f) / 0.80f).coerceIn(0f, 1f)
            val ease = 1f - (1f - e) * (1f - e)

            drawText(
                textLayoutResult = measuredLabel,
                color = gold.copy(alpha = 0.25f * ease),
                topLeft = Offset(
                    size.width * 0.28f,
                    (size.height - measuredLabel.size.height) * 0.54f
                )
            )

            val anchorX = size.width * 0.67f
            val anchorY = size.height * 0.88f
            val batLen = (size.height * 0.68f).coerceIn(36.dp.toPx(), 48.dp.toPx())
            val batWidth = 10.dp.toPx()
            val angleDeg = 20f - 36f * ease

            rotate(degrees = angleDeg, pivot = Offset(anchorX, anchorY)) {
                val handleH = batLen * 0.30f
                val handleW = 3.4.dp.toPx()
                val bladeH = batLen * 0.70f
                drawRoundRect(
                    color = gold.copy(alpha = 0.34f * ease),
                    topLeft = Offset(anchorX - handleW / 2f, anchorY - handleH),
                    size = Size(handleW, handleH + 2.dp.toPx()),
                    cornerRadius = CornerRadius(handleW / 2f)
                )
                drawRoundRect(
                    color = gold.copy(alpha = 0.30f * ease),
                    topLeft = Offset(anchorX - batWidth / 2f, anchorY - batLen),
                    size = Size(batWidth, bladeH),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }

        // Repeating dual-side confetti cannons from both left and right edges
        if (ct > 0f && ct < 1f) {
            val gravityPx = 46.dp.toPx()
            for (c in confetti) {
                val lt = ((ct - c.delayFrac) / (1f - c.delayFrac)).coerceIn(0f, 1f)
                if (lt <= 0f || lt >= 1f) continue

                val startX = if (c.fromLeft) 4.dp.toPx() else size.width - 4.dp.toPx()
                val startY = size.height * c.startYFrac
                val drag = 1f - (1f - lt).pow(2.2f)
                val vxPx = (if (c.fromLeft) 1f else -1f) * c.vxDp.dp.toPx()
                val vyPx = c.vyDp.dp.toPx()

                val px = startX + vxPx * drag
                val py = startY + vyPx * lt + gravityPx * lt * lt
                val cAlpha = (if (lt < 0.15f) lt / 0.15f else 1f - (lt - 0.15f) / 0.85f).coerceIn(0f, 1f) * 0.92f
                val color = c.color.copy(alpha = cAlpha)

                if (c.isStar) {
                    drawStar4(center = Offset(px, py), radius = 3.8.dp.toPx(), rotationRad = c.rotSpeed * lt * 0.02f, color = color)
                } else {
                    val flutterX = cos(lt * 8f + c.phaseOffset).coerceAtLeast(0.18f)
                    val center = Offset(px, py)
                    withTransform({
                        rotate(degrees = c.rotSpeed * lt, pivot = center)
                        scale(scaleX = flutterX, scaleY = 1f, pivot = center)
                    }) {
                        drawRect(
                            color = color,
                            topLeft = Offset(px - 3.2.dp.toPx(), py - 1.6.dp.toPx()),
                            size = Size(6.4.dp.toPx(), 3.2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStar4(
    center: Offset,
    radius: Float,
    rotationRad: Float,
    color: Color
) {
    rotate(degrees = Math.toDegrees(rotationRad.toDouble()).toFloat(), pivot = center) {
        val inner = radius * 0.36f
        val path = Path().apply {
            for (i in 0 until 8) {
                val r = if (i % 2 == 0) radius else inner
                val angle = Math.toRadians(i * 45.0 - 90.0)
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path = path, color = color)
    }
}

/**
 * Horizontal shake modifier triggered when a wicket falls on the batting team's row or out batsman's row.
 */
@Composable
fun Modifier.wicketShake(triggerId: Long?): Modifier {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(triggerId) {
        if (triggerId != null) {
            val keyframes = listOf(-16f, 16f, -11f, 11f, -6f, 6f, 0f)
            for (target in keyframes) {
                offsetX.animateTo(target, animationSpec = tween(durationMillis = 55))
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    return this.graphicsLayer { translationX = offsetX.value }
}

/**
 * Slower vertical score pop-roll animation matching Media1.mp4 so run changes are easy to spot.
 */
@Composable
fun RollingValueTransition(
    targetValue: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    AnimatedContent(
        targetState = targetValue,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(680, easing = FastOutSlowInEasing)) { (it * 0.85f).toInt() } +
                scaleIn(initialScale = 0.84f, animationSpec = tween(680, easing = FastOutSlowInEasing)) +
                fadeIn(tween(520, delayMillis = 100)))
                .togetherWith(
                    slideOutVertically(animationSpec = tween(540, easing = FastOutSlowInEasing)) { -(it * 0.85f).toInt() } +
                        scaleOut(targetScale = 0.88f, animationSpec = tween(480)) +
                        fadeOut(tween(420))
                )
        },
        modifier = modifier,
        label = "rollingScore"
    ) { value ->
        content(value)
    }
}

/**
 * Animated stadium siren dome + alternating red/amber strobe beams on MatchDetailsSummaryCard
 * when a No Ball (`NB`) is bowled.
 */
@Composable
fun NoBallSirenOverlay(
    triggerId: Long?,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(triggerId) {
        if (triggerId == null) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2400, easing = FastOutSlowInEasing)
            )
        }
    }

    if (triggerId == null) return

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas

        val fade = when {
            t < 0.10f -> t / 0.10f
            t > 0.80f -> (1f - t) / 0.20f
            else -> 1f
        }.coerceIn(0f, 1f)

        val sirenRed = Color(0xFFEF4444)
        val sirenAmber = Color(0xFFF59E0B)
        // 4 alternating siren flashes
        val flashWave = sin(t * PI * 8.0).toFloat()
        val redActive = flashWave >= 0f
        val pulseStrength = kotlin.math.abs(flashWave).coerceIn(0.25f, 1f)
        val activeColor = if (redActive) sirenRed else sirenAmber

        val cx = size.width * 0.56f
        val baseY = size.height * 0.66f

        // Left & right sweeping strobe glow cones
        val beamReach = size.width * 0.44f
        val leftBeamAlpha = (if (redActive) 0.16f else 0.04f) * fade * pulseStrength
        val rightBeamAlpha = (if (!redActive) 0.16f else 0.04f) * fade * pulseStrength
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sirenRed.copy(alpha = leftBeamAlpha), Color.Transparent),
                center = Offset(cx - 26.dp.toPx(), baseY - 12.dp.toPx()),
                radius = beamReach
            ),
            radius = beamReach,
            center = Offset(cx - 26.dp.toPx(), baseY - 12.dp.toPx())
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sirenAmber.copy(alpha = rightBeamAlpha), Color.Transparent),
                center = Offset(cx + 26.dp.toPx(), baseY - 12.dp.toPx()),
                radius = beamReach
            ),
            radius = beamReach,
            center = Offset(cx + 26.dp.toPx(), baseY - 12.dp.toPx())
        )

        // Flashing beacon dome
        val domeWidth = 22.dp.toPx()
        val domeHeight = 18.dp.toPx()
        val baseW = 28.dp.toPx()
        val baseH = 5.dp.toPx()

        // Dome outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(activeColor.copy(alpha = 0.34f * fade * pulseStrength), Color.Transparent),
                center = Offset(cx, baseY - domeHeight * 0.55f),
                radius = 28.dp.toPx()
            ),
            radius = 28.dp.toPx(),
            center = Offset(cx, baseY - domeHeight * 0.55f)
        )

        // Siren dome silhouette
        drawArc(
            color = activeColor.copy(alpha = 0.42f * fade * (0.55f + 0.45f * pulseStrength)),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - domeWidth / 2f, baseY - domeHeight),
            size = Size(domeWidth, domeHeight * 2f)
        )
        // Warning base collar
        drawRoundRect(
            color = Color(0xFF94A3B8).copy(alpha = 0.45f * fade),
            topLeft = Offset(cx - baseW / 2f, baseY - baseH * 0.2f),
            size = Size(baseW, baseH),
            cornerRadius = CornerRadius(baseH / 2f)
        )

        // 3 radiating siren strobe arcs above dome
        val arcAngles = listOf(-140.0, -90.0, -40.0)
        for (deg in arcAngles) {
            val rad = Math.toRadians(deg)
            val r0 = 16.dp.toPx()
            val r1 = r0 + (7f + 4f * pulseStrength).dp.toPx()
            val origin = Offset(cx, baseY - domeHeight * 0.4f)
            drawLine(
                color = activeColor.copy(alpha = 0.48f * fade * pulseStrength),
                start = Offset(origin.x + (cos(rad) * r0).toFloat(), origin.y + (sin(rad) * r0).toFloat()),
                end = Offset(origin.x + (cos(rad) * r1).toFloat(), origin.y + (sin(rad) * r1).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Animated crimson border color pulse for LastWicketCard when a new wicket lands.
 */
@Composable
fun rememberLastWicketBorderColor(
    triggerId: Long?,
    baseColor: Color
): Color {
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(triggerId) {
        if (triggerId != null) {
            showFlash = true
            delay(1400L)
            showFlash = false
        } else {
            showFlash = false
        }
    }

    val target = if (showFlash) Color(0xFFEF4444).copy(alpha = 0.85f) else baseColor
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (showFlash) 220 else 550),
        label = "lastWicketBorder"
    )
    return animated
}

/**
 * Horizontal crimson light sweep drawn behind LastWicketCard on new wicket updates.
 */
@Composable
fun LastWicketFlashOverlay(
    triggerId: Long?,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(triggerId) {
        if (triggerId == null) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        }
    }

    if (triggerId == null) return

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val sweepW = size.width * 0.6f
        val leftX = -sweepW + t * (size.width + sweepW * 2f)
        val alpha = sin(t * PI).toFloat() * 0.20f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color(0xFFEF4444).copy(alpha = alpha), Color.Transparent),
                startX = leftX,
                endX = leftX + sweepW
            )
        )
    }
}

/**
 * Spring-pop + expanding crimson ripple ring around the `W` badge inside LastWicketCard.
 */
@Composable
fun LastWicketBadgeWrapper(
    triggerId: Long?,
    content: @Composable () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }

    LaunchedEffect(triggerId) {
        if (triggerId != null) {
            scale.snapTo(0.65f)
            ring.snapTo(0f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LaunchedEffect(triggerId) {
        if (triggerId != null) {
            ring.snapTo(0f)
            ring.animateTo(1f, animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier) {
        if (triggerId != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val rt = ring.value
                if (rt > 0f && rt < 1f) {
                    val baseRadius = size.minDimension / 2f
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = (1f - rt) * 0.65f),
                        radius = baseRadius * (0.95f + 0.65f * rt),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        ) {
            content()
        }
    }
}

/**
 * Spring pop-in scale wrapper + optional ring pulse for newly delivered balls in Recent Balls.
 */
@Composable
fun PopInBallWrapper(
    isNew: Boolean,
    ballKey: Long,
    pulseColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale = remember(ballKey) { Animatable(if (isNew) 0.2f else 1f) }
    val ring = remember(ballKey) { Animatable(if (isNew && pulseColor != null) 0f else 1f) }

    LaunchedEffect(ballKey) {
        if (isNew) {
            scale.snapTo(0.2f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            scale.snapTo(1f)
        }
    }

    LaunchedEffect(ballKey) {
        if (isNew && pulseColor != null) {
            ring.snapTo(0f)
            ring.animateTo(1f, animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = modifier) {
        if (pulseColor != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val rt = ring.value
                if (rt > 0f && rt < 1f) {
                    val baseR = size.minDimension / 2f
                    drawCircle(
                        color = pulseColor.copy(alpha = (1f - rt) * 0.6f),
                        radius = baseR * (1f + 0.55f * rt),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        ) {
            content()
        }
    }
}
