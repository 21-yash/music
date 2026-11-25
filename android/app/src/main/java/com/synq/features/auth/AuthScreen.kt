package com.synq.features.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synq.ui.SynqBrandedText
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.SynqLogo
import com.synq.ui.glassSurface
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkDeep
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.SemanticLive
import com.synq.ui.theme.Spacing

private enum class AuthErrorField {
    IDENTIFIER,
    EMAIL,
    INVITE_CODE,
    PASSWORD,
    CONFIRM_PASSWORD,
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    showBackButton: Boolean = true,
    onBackClick: () -> Unit = {},
    onAuthSuccess: () -> Unit = {},
) {
    val context = LocalContext.current
    BackHandler(enabled = !showBackButton) {
        (context as? Activity)?.finish()
    }

    val isLoginMode by viewModel.isLoginMode.collectAsState()
    val identifier by viewModel.identifier.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val authSuccess by viewModel.authSuccess.collectAsState()

    val isDark = isAppDark()
    val accentTextColor = if (isDark) PinkPrimary else PinkDeep
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var passwordVisible by remember { mutableStateOf(false) }

    val fieldColors = authGlassTextFieldColors(isDark)
    val inputShape = MaterialTheme.shapes.large

    // Unidirectional top-anchored collapse/expand so the outer glass card only shrinks upward cleanly
    val fieldSizeSpec = tween<IntSize>(durationMillis = 250, easing = FastOutSlowInEasing)
    val fieldEnter = fadeIn(tween(250, easing = FastOutSlowInEasing)) +
        expandVertically(animationSpec = fieldSizeSpec, expandFrom = Alignment.Top)
    val fieldExit = fadeOut(tween(220, easing = FastOutSlowInEasing)) +
        shrinkVertically(animationSpec = fieldSizeSpec, shrinkTowards = Alignment.Top)

    val logoPopScale = remember { Animatable(1f) }
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(isLoginMode) {
        if (isFirstComposition) {
            isFirstComposition = false
        } else {
            if (isLoginMode && scrollState.value > 0) {
                scrollState.animateScrollTo(0, tween(250, easing = FastOutSlowInEasing))
            }
            logoPopScale.animateTo(0.92f, tween(80))
            logoPopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.45f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Error highlight + shake feedback each time an error occurs or repeat-submit hits an error
    var submitAttemptCount by remember { mutableIntStateOf(0) }
    val shakeOffsetPx = remember { Animatable(0f) }
    val shakeDistancePx = remember(density) { with(density) { 10.dp.toPx() } }
    val errorFields = remember(errorMessage, isLoginMode) {
        resolveAuthErrorFields(errorMessage, isLoginMode)
    }

    LaunchedEffect(errorMessage, isLoading, submitAttemptCount) {
        if (errorMessage != null && !isLoading) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeOffsetPx.snapTo(0f)
            shakeOffsetPx.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 360
                    -shakeDistancePx at 50
                    shakeDistancePx at 110
                    -shakeDistancePx * 0.65f at 170
                    shakeDistancePx * 0.65f at 230
                    -shakeDistancePx * 0.25f at 290
                    0f at 360
                }
            )
        }
    }

    LaunchedEffect(authSuccess) {
        if (authSuccess) {
            onAuthSuccess()
        }
    }

    fun triggerSubmit() {
        focusManager.clearFocus()
        submitAttemptCount++
        viewModel.submit()
    }

    Scaffold(
        topBar = {
            if (showBackButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pressable(
                                onClick = onBackClick,
                                pressedScale = MotionTokens.ButtonPressScale
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Spacer(Modifier.statusBarsPadding().height(Spacing.xs))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.l, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(Modifier.height(Spacing.s))

                // Logo Badge & Synq Branding (tight between logo & title, generous space below before card)
                SynqLogo(
                    size = 64.dp,
                    showGlow = false,
                    modifier = Modifier.graphicsLayer {
                        scaleX = logoPopScale.value
                        scaleY = logoPopScale.value
                    }
                )

                Spacer(Modifier.height(Spacing.xs))

                SynqBrandedText(
                    fontSize = 28.sp, // matches headlineLarge (28sp Bold)
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    synColor = MaterialTheme.colorScheme.onBackground,
                    qColor = accentTextColor,
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(Modifier.height(Spacing.xl))

                // Error banner (shakes every time an error occurs)
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(200)) + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut(tween(180)) + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.m)
                                .graphicsLayer { translationX = shakeOffsetPx.value },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = SemanticLive.copy(alpha = if (isDark) 0.16f else 0.10f)
                            ),
                            border = BorderStroke(1.dp, SemanticLive.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.cardInnerPaddingCompact),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = SemanticLive,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(Spacing.s))
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Unified Frosted Glass Form Card (no separate sub-card band or divider)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(MaterialTheme.shapes.extraLarge)
                        .padding(Spacing.cardInnerPadding)
                ) {
                    // Left-aligned SemiBold Mode Heading inside unified glass container
                    AnimatedContent(
                        targetState = isLoginMode,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically { it / 3 })
                                .togetherWith(fadeOut(tween(180)) + slideOutVertically { -it / 3 })
                        },
                        label = "authHeading",
                        modifier = Modifier.fillMaxWidth()
                    ) { login ->
                        Text(
                            text = if (login) "Welcome Back" else "Create Account",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(Spacing.s))
                        AuthTextField(
                            value = identifier,
                            onValueChange = { viewModel.setIdentifier(it) },
                            label = if (isLoginMode) "Username or Email" else "Username",
                            leadingIcon = if (isLoginMode) Icons.Default.Person else Icons.Default.AlternateEmail,
                            isError = AuthErrorField.IDENTIFIER in errorFields,
                            shakeOffsetPx = shakeOffsetPx.value,
                            shape = inputShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (isLoginMode) KeyboardType.Email else KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        AnimatedVisibility(
                            visible = !isLoginMode,
                            enter = fieldEnter,
                            exit = fieldExit
                        ) {
                            Column {
                                Spacer(Modifier.height(Spacing.xs))
                                AuthTextField(
                                    value = email,
                                    onValueChange = { viewModel.setEmail(it) },
                                    label = "Email Address",
                                    leadingIcon = Icons.Default.Email,
                                    isError = AuthErrorField.EMAIL in errorFields,
                                    shakeOffsetPx = shakeOffsetPx.value,
                                    shape = inputShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    )
                                )

                                Spacer(Modifier.height(Spacing.xs))
                                AuthTextField(
                                    value = inviteCode,
                                    onValueChange = { viewModel.setInviteCode(it) },
                                    label = "Invite Code",
                                    placeholder = "XXXX-XXXX-XXXX",
                                    leadingIcon = Icons.Default.VpnKey,
                                    isError = AuthErrorField.INVITE_CODE in errorFields,
                                    shakeOffsetPx = shakeOffsetPx.value,
                                    shape = inputShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(Spacing.xs))

                        AuthTextField(
                            value = password,
                            onValueChange = { viewModel.setPassword(it) },
                            label = "Password",
                            leadingIcon = Icons.Default.Lock,
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            isError = AuthErrorField.PASSWORD in errorFields,
                            shakeOffsetPx = shakeOffsetPx.value,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = inputShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isLoginMode) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isLoginMode) triggerSubmit()
                                },
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        AnimatedVisibility(
                            visible = !isLoginMode,
                            enter = fieldEnter,
                            exit = fieldExit
                        ) {
                            Column {
                                Spacer(Modifier.height(Spacing.xs))
                                AuthTextField(
                                    value = confirmPassword,
                                    onValueChange = { viewModel.setConfirmPassword(it) },
                                    label = "Confirm Password",
                                    leadingIcon = Icons.Default.LockClock,
                                    isError = AuthErrorField.CONFIRM_PASSWORD in errorFields,
                                    shakeOffsetPx = shakeOffsetPx.value,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    shape = inputShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { triggerSubmit() }
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(Spacing.m))

                        // Primary Submit Button inside Glass Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .then(
                                    if (!isLoading) {
                                        Modifier.pressable(
                                            onClick = { triggerSubmit() },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(MaterialTheme.shapes.large)
                                .background(PinkGradient)
                                .alpha(if (isLoading) 0.75f else 1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                SynqLoadingIndicator(
                                    size = 22.dp,
                                    barCount = 3,
                                    colors = listOf(Color.White.copy(alpha = 0.75f), Color.White)
                                )
                            } else {
                                AnimatedContent(
                                    targetState = isLoginMode,
                                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                                    label = "authCtaText"
                                ) { login ->
                                    Text(
                                        text = if (login) "Sign In" else "Create Account",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                Spacer(Modifier.height(Spacing.m))

                // Plain clickable text link (no background pill)
                Box(
                    modifier = Modifier
                        .pressable(
                            onClick = { viewModel.toggleMode() },
                            pressedScale = MotionTokens.ChipPressScale
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.m),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append(if (isLoginMode) "Don't have an account? " else "Already have an account? ")
                            }
                            withStyle(SpanStyle(color = accentTextColor, fontWeight = FontWeight.SemiBold)) {
                                append(if (isLoginMode) "Sign Up" else "Sign In")
                            }
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    isError: Boolean,
    shakeOffsetPx: Float,
    shape: Shape,
    colors: TextFieldColors,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    placeholder: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = shape,
        colors = colors,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier
            .fillMaxWidth()
            .shakeIfError(isError, shakeOffsetPx)
    )
}

private fun Modifier.shakeIfError(hasError: Boolean, translationPx: Float): Modifier =
    if (hasError) graphicsLayer { translationX = translationPx } else this

private fun resolveAuthErrorFields(
    errorMessage: String?,
    isLoginMode: Boolean
): Set<AuthErrorField> {
    if (errorMessage.isNullOrBlank()) return emptySet()
    val msg = errorMessage.lowercase()
    return when {
        "invite" in msg -> setOf(AuthErrorField.INVITE_CODE)
        "match" in msg || "confirm" in msg -> setOf(AuthErrorField.CONFIRM_PASSWORD)
        "credentials" in msg || "unauthorized" in msg -> setOf(AuthErrorField.IDENTIFIER, AuthErrorField.PASSWORD)
        "password" in msg -> setOf(AuthErrorField.PASSWORD)
        "username" in msg -> setOf(AuthErrorField.IDENTIFIER)
        "email" in msg -> setOf(if (isLoginMode) AuthErrorField.IDENTIFIER else AuthErrorField.EMAIL)
        "user" in msg || "account" in msg -> setOf(AuthErrorField.IDENTIFIER)
        else -> emptySet()
    }
}

@Composable
private fun authGlassTextFieldColors(isDark: Boolean): TextFieldColors {
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }
    val errorContainer = SemanticLive.copy(alpha = if (isDark) 0.14f else 0.08f)
    val unfocusedBorder = if (isDark) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        errorContainerColor = errorContainer,
        focusedBorderColor = PinkPrimary,
        unfocusedBorderColor = unfocusedBorder,
        errorBorderColor = SemanticLive,
        focusedLeadingIconColor = PinkPrimary,
        unfocusedLeadingIconColor = PinkPrimary.copy(alpha = 0.85f),
        errorLeadingIconColor = SemanticLive,
        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorTrailingIconColor = SemanticLive,
        focusedLabelColor = PinkPrimary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorLabelColor = SemanticLive,
        cursorColor = PinkPrimary,
        errorCursorColor = SemanticLive,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        errorTextColor = MaterialTheme.colorScheme.onSurface,
    )
}
