package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.SortField
import com.example.data.model.SortPreference
import com.example.data.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AppThemeSetting {
    SYSTEM,
    LIGHT,
    DARK,
    ASGL
}

data class SalimSettings(
    val theme: AppThemeSetting = AppThemeSetting.SYSTEM,
    val glassTransparency: Float = 0.75f, // 0.40 (near-clear), 0.75 (balanced), 0.92 (near-opaque)
    val reduceTransparency: Boolean = false, // If true, fall back to solid surfaces
    val defaultSortField: SortField = SortField.NAME,
    val defaultSortAscending: Boolean = true,
    val foldersAlwaysFirst: Boolean = true,
    val defaultViewMode: ViewMode = ViewMode.LIST,
    val gridColumns: Int = 3,
    val confirmBeforeDelete: Boolean = true,
    val trashRetentionDays: Int = 30, // 7, 30, 90, -1 (never)
    val showHiddenFiles: Boolean = false,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val textSizeMultiplier: Float = 1.0f,
    val appLockEnabled: Boolean = false,
    val appLockPin: String = "",
    val vaultPin: String = ""
)

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("salim_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SalimSettings> = _settings.asStateFlow()

    private fun loadSettings(): SalimSettings {
        return SalimSettings(
            theme = try { AppThemeSetting.valueOf(prefs.getString("theme", AppThemeSetting.SYSTEM.name) ?: AppThemeSetting.SYSTEM.name) } catch (e: Exception) { AppThemeSetting.SYSTEM },
            glassTransparency = prefs.getFloat("glass_transparency", 0.75f).coerceIn(0.2f, 0.98f),
            reduceTransparency = prefs.getBoolean("reduce_transparency", false),
            defaultSortField = try { SortField.valueOf(prefs.getString("sort_field", SortField.NAME.name) ?: SortField.NAME.name) } catch (e: Exception) { SortField.NAME },
            defaultSortAscending = prefs.getBoolean("sort_ascending", true),
            foldersAlwaysFirst = prefs.getBoolean("folders_first", true),
            defaultViewMode = try { ViewMode.valueOf(prefs.getString("view_mode", ViewMode.LIST.name) ?: ViewMode.LIST.name) } catch (e: Exception) { ViewMode.LIST },
            gridColumns = prefs.getInt("grid_columns", 3).coerceIn(2, 6),
            confirmBeforeDelete = prefs.getBoolean("confirm_delete", true),
            trashRetentionDays = prefs.getInt("trash_retention", 30),
            showHiddenFiles = prefs.getBoolean("show_hidden", false),
            reduceMotion = prefs.getBoolean("reduce_motion", false),
            highContrast = prefs.getBoolean("high_contrast", false),
            textSizeMultiplier = prefs.getFloat("text_size_multiplier", 1.0f),
            appLockEnabled = prefs.getBoolean("app_lock_enabled", false),
            appLockPin = prefs.getString("app_lock_pin", "") ?: "",
            vaultPin = prefs.getString("vault_pin", "") ?: ""
        )
    }

    fun updateTheme(theme: AppThemeSetting) {
        prefs.edit().putString("theme", theme.name).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun updateGlassTransparency(transparency: Float) {
        val clamped = transparency.coerceIn(0.2f, 0.98f)
        prefs.edit().putFloat("glass_transparency", clamped).apply()
        _settings.value = _settings.value.copy(glassTransparency = clamped)
    }

    fun updateReduceTransparency(reduce: Boolean) {
        prefs.edit().putBoolean("reduce_transparency", reduce).apply()
        _settings.value = _settings.value.copy(reduceTransparency = reduce)
    }

    fun updateDefaultSort(field: SortField, ascending: Boolean) {
        prefs.edit()
            .putString("sort_field", field.name)
            .putBoolean("sort_ascending", ascending)
            .apply()
        _settings.value = _settings.value.copy(defaultSortField = field, defaultSortAscending = ascending)
    }

    fun updateFoldersAlwaysFirst(foldersFirst: Boolean) {
        prefs.edit().putBoolean("folders_first", foldersFirst).apply()
        _settings.value = _settings.value.copy(foldersAlwaysFirst = foldersFirst)
    }

    fun updateDefaultViewMode(viewMode: ViewMode) {
        prefs.edit().putString("view_mode", viewMode.name).apply()
        _settings.value = _settings.value.copy(defaultViewMode = viewMode)
    }

    fun updateGridColumns(columns: Int) {
        val clamped = columns.coerceIn(2, 6)
        prefs.edit().putInt("grid_columns", clamped).apply()
        _settings.value = _settings.value.copy(gridColumns = clamped)
    }

    fun updateConfirmBeforeDelete(confirm: Boolean) {
        prefs.edit().putBoolean("confirm_delete", confirm).apply()
        _settings.value = _settings.value.copy(confirmBeforeDelete = confirm)
    }

    fun updateTrashRetentionDays(days: Int) {
        prefs.edit().putInt("trash_retention", days).apply()
        _settings.value = _settings.value.copy(trashRetentionDays = days)
    }

    fun updateShowHiddenFiles(show: Boolean) {
        prefs.edit().putBoolean("show_hidden", show).apply()
        _settings.value = _settings.value.copy(showHiddenFiles = show)
    }

    fun updateReduceMotion(reduce: Boolean) {
        prefs.edit().putBoolean("reduce_motion", reduce).apply()
        _settings.value = _settings.value.copy(reduceMotion = reduce)
    }

    fun updateHighContrast(highContrast: Boolean) {
        prefs.edit().putBoolean("high_contrast", highContrast).apply()
        _settings.value = _settings.value.copy(highContrast = highContrast)
    }

    fun updateTextSizeMultiplier(multiplier: Float) {
        prefs.edit().putFloat("text_size_multiplier", multiplier).apply()
        _settings.value = _settings.value.copy(textSizeMultiplier = multiplier)
    }

    fun setAppLock(enabled: Boolean, pin: String) {
        prefs.edit()
            .putBoolean("app_lock_enabled", enabled)
            .putString("app_lock_pin", pin)
            .apply()
        _settings.value = _settings.value.copy(appLockEnabled = enabled, appLockPin = pin)
    }

    fun setVaultPin(pin: String) {
        prefs.edit().putString("vault_pin", pin).apply()
        _settings.value = _settings.value.copy(vaultPin = pin)
    }

    fun exportSettingsJson(): String {
        val s = _settings.value
        val json = JSONObject().apply {
            put("theme", s.theme.name)
            put("defaultSortField", s.defaultSortField.name)
            put("defaultSortAscending", s.defaultSortAscending)
            put("foldersAlwaysFirst", s.foldersAlwaysFirst)
            put("defaultViewMode", s.defaultViewMode.name)
            put("gridColumns", s.gridColumns)
            put("confirmBeforeDelete", s.confirmBeforeDelete)
            put("trashRetentionDays", s.trashRetentionDays)
            put("showHiddenFiles", s.showHiddenFiles)
            put("reduceMotion", s.reduceMotion)
            put("highContrast", s.highContrast)
            put("glassTransparency", s.glassTransparency.toDouble())
            put("reduceTransparency", s.reduceTransparency)
            put("textSizeMultiplier", s.textSizeMultiplier.toDouble())
        }
        return json.toString(2)
    }

    fun importSettingsJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("theme")) updateTheme(AppThemeSetting.valueOf(json.getString("theme")))
            if (json.has("glassTransparency")) updateGlassTransparency(json.getDouble("glassTransparency").toFloat())
            if (json.has("reduceTransparency")) updateReduceTransparency(json.getBoolean("reduceTransparency"))
            if (json.has("defaultSortField")) {
                val field = SortField.valueOf(json.getString("defaultSortField"))
                val asc = json.optBoolean("defaultSortAscending", true)
                updateDefaultSort(field, asc)
            }
            if (json.has("foldersAlwaysFirst")) updateFoldersAlwaysFirst(json.getBoolean("foldersAlwaysFirst"))
            if (json.has("defaultViewMode")) updateDefaultViewMode(ViewMode.valueOf(json.getString("defaultViewMode")))
            if (json.has("gridColumns")) updateGridColumns(json.getInt("gridColumns"))
            if (json.has("confirmBeforeDelete")) updateConfirmBeforeDelete(json.getBoolean("confirmBeforeDelete"))
            if (json.has("trashRetentionDays")) updateTrashRetentionDays(json.getInt("trashRetentionDays"))
            if (json.has("showHiddenFiles")) updateShowHiddenFiles(json.getBoolean("showHiddenFiles"))
            if (json.has("reduceMotion")) updateReduceMotion(json.getBoolean("reduceMotion"))
            if (json.has("highContrast")) updateHighContrast(json.getBoolean("highContrast"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
