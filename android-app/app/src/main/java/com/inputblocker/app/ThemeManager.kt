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
                background = ContextCompat.getColor(context, R.color.amoled_background),
                surface = ContextCompat.getColor(context, R.color.amoled_surface),
                surfaceElevated = ContextCompat.getColor(context, R.color.amoled_surface_elevated),
                textPrimary = ContextCompat.getColor(context, R.color.amoled_text_primary),
                textSecondary = ContextCompat.getColor(context, R.color.amoled_text_secondary),
                border = ContextCompat.getColor(context, R.color.amoled_border),
                accent = ContextCompat.getColor(context, R.color.accent_blue)
            )
            else -> ThemeColors( // Default to Dark
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
        applyColorsToWindow(activity, colors)
    }

    private fun applyColorsToWindow(activity: Activity, colors: ThemeColors) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = colors.background
            activity.window.navigationBarColor = colors.background
        }
        activity.findViewById<View>(android.R.id.content)?.setBackgroundColor(colors.background)
    }

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
                view.strokeColor = colors.border
            }
            is SwitchMaterial -> {
                val thumbColors = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(colors.accent, colors.border)
                )
                val trackColors = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.argb(77, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent)), colors.border)
                )
                view.thumbTintList = thumbColors
                view.trackTintList = trackColors
            }
            is MaterialButton -> {
                if (view.id == R.id.btn_theme || view.id == R.id.btn_action_safe || 
                    view.id == R.id.btn_action_sync || view.id == R.id.btn_action_export ||
                    view.id == R.id.btn_action_test || view.id == R.id.btn_view_log ||
                    view.id.toString().contains("btn_action")) {
                    view.setTextColor(colors.accent)
                } else if (view.id == R.id.btn_add_region || view.id == R.id.btn_undo || view.id == R.id.btn_cancel) {
                    view.backgroundTintList = ColorStateList.valueOf(colors.surfaceElevated)
                    view.setTextColor(colors.textPrimary)
                    if (view is MaterialButton) view.strokeColor = ColorStateList.valueOf(colors.border)
                } else if (view.id != R.id.btn_launch_setup && view.id != R.id.btn_auto_detect && 
                           view.id != R.id.btn_save && view.id != R.id.btn_clear && view.id != R.id.btn_clear_all) {
                     // Default button styling
                }
            }
            is TextView -> {
                if (view.id == R.id.tv_status) {
                    // Handled specially in MainActivity
                } else if (view.id == R.id.tv_status_label || view.id == R.id.tv_instructions || view.id == R.id.tv_region_coords) {
                    view.setTextColor(colors.textSecondary)
                } else {
                    view.setTextColor(colors.textPrimary)
                }
            }
            is ViewGroup -> {
                if (view.id == R.id.regions_list) {
                    // Container, keep transparent
                } else if (view is MaterialCardView) {
                    // Handled above
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeToViewHierarchy(view.getChildAt(i), colors)
            }
        }
    }
}
