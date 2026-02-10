package no.shoppinglist.android.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

val LocalI18n = staticCompositionLocalOf<I18n> { error("No I18n provided") }

@Composable
fun t(key: String, vararg args: Pair<String, Any>): String {
    return LocalI18n.current.t(key, *args)
}

class I18n(context: Context) {
    private val allTranslations: Map<String, Map<String, String>>
    private val prefs = context.getSharedPreferences("i18n", Context.MODE_PRIVATE)

    var locale by mutableStateOf(prefs.getString("locale", "nb") ?: "nb")
        private set

    init {
        val locales = listOf("en", "nb", "nn", "se", "tl")
        allTranslations = locales.associateWith { loadLocale(context, it) }
    }

    fun changeLocale(newLocale: String) {
        locale = newLocale
        prefs.edit().putString("locale", newLocale).apply()
    }

    fun t(key: String, vararg args: Pair<String, Any>): String {
        val current = allTranslations[locale] ?: emptyMap()
        val fallback = if (locale != "en") allTranslations["en"] ?: emptyMap() else emptyMap()
        val template = current[key] ?: fallback[key] ?: key
        return args.fold(template) { acc, (name, value) ->
            acc.replace("{{$name}}", value.toString())
        }
    }

    val availableLocales: List<Pair<String, String>>
        get() = listOf(
            "en" to "English",
            "nb" to "Norsk Bokm\u00e5l",
            "nn" to "Norsk Nynorsk",
            "se" to "Davvis\u00e1megiella",
            "tl" to "Filipino",
        )

    private fun loadLocale(context: Context, locale: String): Map<String, String> {
        return try {
            val json = context.assets.open("i18n/$locale.json").bufferedReader().readText()
            flattenJson(JSONObject(json))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun flattenJson(obj: JSONObject, prefix: String = ""): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            when (val value = obj.get(key)) {
                is JSONObject -> result.putAll(flattenJson(value, fullKey))
                else -> result[fullKey] = value.toString()
            }
        }
        return result
    }
}
