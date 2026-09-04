package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors

private val DarkColorScheme = darkColorScheme(
    primary = SprucePrimaryLight,
    onPrimary = SpruceOnContainer,
    primaryContainer = SprucePrimary,
    onPrimaryContainer = Color.White,
    secondary = SpruceContainer,
    onSecondary = SpruceOnContainer,
    secondaryContainer = SprucePrimaryDark,
    onSecondaryContainer = Color.White,
    tertiary = PolishAmber,
    background = PolishBackgroundDark,
    surface = PolishSurfaceDark,
    surfaceVariant = PolishSurfaceVariantDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = PolishRose,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SprucePrimary,
    onPrimary = Color.White,
    primaryContainer = SpruceContainer,
    onPrimaryContainer = SpruceOnContainer,
    secondary = SprucePrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = SpruceContainerLight,
    onSecondaryContainer = SpruceOnContainer,
    tertiary = PolishAmber,
    background = PolishBackgroundLight,
    surface = PolishSurfaceLight,
    surfaceVariant = PolishSurfaceVariantLight,
    onBackground = Slate900,
    onSurface = Slate900,
    onSurfaceVariant = Slate500,
    error = PolishRose,
    onError = Color.White
)

@Composable
fun SndmartVendorTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun polishTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Slate900,
    unfocusedTextColor = Slate900,
    disabledTextColor = Slate500,
    errorTextColor = PolishRose,
    focusedContainerColor = PolishSurfaceLight,
    unfocusedContainerColor = PolishSurfaceLight,
    disabledContainerColor = PolishSurfaceSubtle,
    cursorColor = SprucePrimary,
    errorCursorColor = PolishRose,
    focusedBorderColor = SprucePrimary,
    unfocusedBorderColor = PolishBorderLight,
    disabledBorderColor = Slate200,
    errorBorderColor = PolishRose,
    focusedLabelColor = SprucePrimary,
    unfocusedLabelColor = Slate600,
    disabledLabelColor = Slate400,
    errorLabelColor = PolishRose,
    focusedPlaceholderColor = Slate400,
    unfocusedPlaceholderColor = Slate400,
    disabledPlaceholderColor = Slate400,
    focusedLeadingIconColor = Slate600,
    unfocusedLeadingIconColor = Slate400,
    focusedTrailingIconColor = Slate600,
    unfocusedTrailingIconColor = Slate400
)

