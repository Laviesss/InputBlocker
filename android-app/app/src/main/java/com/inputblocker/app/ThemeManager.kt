package com.inputblocker.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout

/**
 * Senior Engineering: Unified Theme Management Engine.
 * Handles dynamic propagation of System, Light, Dark, and AMOLED palettes.
 * AMOLED mode enforces pure black (#000000) for OLED battery efficiency.
 */
object ThemeManager {

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2
    const val THEME_AMOLED = 3

    data class ThemeColors(
        val background: Int,
        val surface: Int,
        val surfaceElevated: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val border: Int,
        val accent: Int
    )

    fun getThemeColors(context: Context, theme: Int): ThemeColors {
        return when (theme) {
            THEME_LIGHT -> ThemeColors(
                background = ContextCompat.getColor(context, R.color.light_background),
                surface = ContextCompat.getColor(context, R.color.light_surface),
                surfaceElevated = ContextCompat.getColor(context, R.color.light_surface_elevated),
                textPrimary = ContextCompat.getColor(context, R.color.light_text_primary),
                textSecondary = ContextCompat.getColor(context, R.color.light_text_secondary),
                border = ContextCompat.getColor(context, R.color.light_border),
                accent = ContextCompat.getColor(context, R.color.accent_blue)
            )
            THEME_AMOLED -> ThemeColors(
                background = Color.BLACK,
                surface = Color.parseColor("#121212"),
                surfaceElevated = Color.parseColor("#1E1E1E"),
                textPrimary = Color.WHITE,
                textSecondary = Color.parseColor("#A0A0A0"),
                border = Color.parseColor("#2C2C2C"),
                accent = ContextCompat.getColor(context, R.color.accent_blue)
            )
            else -> ThemeColors( // Default / Dark
                background = ContextCompat.getColor(context, R.color.dark_background),
                surface = ContextCompat.getColor(context, R.color.dark_surface),
                surfaceElevated = ContextCompat.getColor(context, R.color.dark_surface_elevated),
                textPrimary = ContextCompat.getColor(context, R.color.dark_text_primary),
                textSecondary = ContextCompat.getColor(context, R.color.dark_text_secondary),
                border = ContextCompat.getColor(context, R.color.dark_border),
                accent = ContextCompat.getColor(context, R.color.accent_blue)
            )
        }
    }

    fun applyTheme(activity: Activity, theme: Int) {
        val mode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK, THEME_AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        
        val colors = getThemeColors(activity, theme)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = colors.background
            activity.window.navigationBarColor = colors.background
        }
        activity.findViewById<View>(android.R.id.content)?.setBackgroundColor(colors.background)
    }

    /**
     * Recursively applies theme colors to the entire view hierarchy.
     */
    fun applyThemeToViewHierarchy(view: View, colors: ThemeColors) {
        when (view) {
            is TabLayout -> {
                view.setBackgroundColor(colors.surface)
                view.setSelectedTabIndicatorColor(colors.accent)
                view.tabTextColors = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                    intArrayOf(colors.accent, colors.textSecondary)
                )
            }
            is MaterialCardView -> {
                view.setCardBackgroundColor(colors.surface)
                view.strokeColor = ColorStateList.valueOf(colors.border)
            }
            is SwitchMaterial -> {
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                val thumbColors = intArrayOf(colors.accent, colors.border)
                val trackColors = intArrayOf(
                    adjustAlpha(colors.accent, 0.3f),
                    adjustAlpha(colors.border, 0.3f)
                )
                view.thumbTintList = ColorStateList(states, thumbColors)
                view.trackTintList = ColorStateList(states, trackColors)
            }
            is MaterialButton -> {
                val id = view.id
                if (id == R.id.btn_theme || id == R.id.btn_action_safe || id == R.id.btn_view_log) {
                    view.setTextColor(colors.accent)
                } else if (id == R.id.btn_add_region || id == R.id.btn_undo || id == R.id.btn_cancel) {
                    view.backgroundTintList = ColorStateList.valueOf(colors.surfaceElevated)
                    view.setTextColor(colors.textPrimary)
                    view.setStrokeColor(ColorStateList.valueOf(colors.border))
                }
            }
            is TextView -> {
                val id = view.id
                if (id == R.id.tv_status_label || id == R.id.tv_instructions || id == R.id.tv_region_coords) {
                    view.setTextColor(colors.textSecondary)
                } else if (id != R.id.tv_status) {
                    view.setTextColor(colors.textPrimary)
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeToViewHierarchy(view.getChildAt(i), colors)
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
