package org.torproject.android.localization

import android.app.Activity
import android.content.ContextWrapper
import android.content.res.Resources
import android.text.TextUtils
import android.util.DisplayMetrics
import java.util.Collections
import java.util.Locale
import java.util.TreeMap

class Languages private constructor(activity: Activity) {
    /**
     * Return an array of the names of all the supported languages, sorted to
     * match what is returned by [Languages.supportedLocales].
     */
    val allNames: Array<String>
        get() = nameMap.values.toTypedArray()

    val supportedLocales: Array<String>
        get() {
            val keys = nameMap.keys
            return keys.toTypedArray()
        }

    companion object {

        fun buildLocaleForLanguage(language: String, region: String? = null): Locale {
            return Locale.Builder()
                .setLanguage(language)
                .setRegion(region)
                .build()
        }

        private var defaultLocale: Locale? = null
        val TIBETAN = buildLocaleForLanguage("bo")
        val localesToTest = arrayOf(
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale.GERMAN,
            Locale.ITALIAN,
            Locale.JAPANESE,
            Locale.KOREAN,
            Locale.TRADITIONAL_CHINESE,
            Locale.SIMPLIFIED_CHINESE,
            TIBETAN,
            buildLocaleForLanguage("af"),
            buildLocaleForLanguage("am"),
            buildLocaleForLanguage("ar"),
            buildLocaleForLanguage("ay"),
            buildLocaleForLanguage("az"),
            buildLocaleForLanguage("bg"),
            buildLocaleForLanguage("be"),
            buildLocaleForLanguage("bn", "BD"),
            buildLocaleForLanguage("bn", "IN"),
            buildLocaleForLanguage("bn"),
            buildLocaleForLanguage("ca"),
            buildLocaleForLanguage("cs"),
            buildLocaleForLanguage("da"),
            buildLocaleForLanguage("el"),
            buildLocaleForLanguage("es"),
            buildLocaleForLanguage("es", "MX"),
            buildLocaleForLanguage("es", "CU"),

            buildLocaleForLanguage("es", "AR"),
            buildLocaleForLanguage("en", "GB"),
            buildLocaleForLanguage("eo"),
            buildLocaleForLanguage("et"),
            buildLocaleForLanguage("eu"),
            buildLocaleForLanguage("fa"),
            buildLocaleForLanguage("fr"),
            buildLocaleForLanguage("fi"),
            buildLocaleForLanguage("gl"),
            buildLocaleForLanguage("gu"),
            buildLocaleForLanguage("guc"),
            buildLocaleForLanguage("gum"),
            buildLocaleForLanguage("nah"),
            buildLocaleForLanguage("hi"),
            buildLocaleForLanguage("hr"),
            buildLocaleForLanguage("hu"),
            buildLocaleForLanguage("hy", "AM"),
            buildLocaleForLanguage("ia"),
            buildLocaleForLanguage("in"),
            buildLocaleForLanguage("hy"),
            buildLocaleForLanguage("in"),
            buildLocaleForLanguage("is"),
            buildLocaleForLanguage("it"),
            buildLocaleForLanguage("iw"),
            buildLocaleForLanguage("ka"),
            buildLocaleForLanguage("kk"),
            buildLocaleForLanguage("km"),
            buildLocaleForLanguage("kn"),
            buildLocaleForLanguage("ky"),
            buildLocaleForLanguage("lo"),
            buildLocaleForLanguage("lt"),
            buildLocaleForLanguage("lv"),
            buildLocaleForLanguage("mk"),
            buildLocaleForLanguage("ml"),
            buildLocaleForLanguage("mn"),
            buildLocaleForLanguage("mr"),
            buildLocaleForLanguage("ms"),
            buildLocaleForLanguage("my"),
            buildLocaleForLanguage("nb"),
            buildLocaleForLanguage("ne"),
            buildLocaleForLanguage("nl"),
            buildLocaleForLanguage("pa"),
            buildLocaleForLanguage("pbb"),

            buildLocaleForLanguage("pl"),
            buildLocaleForLanguage("pt", "BR"),
            buildLocaleForLanguage("pt"),
            buildLocaleForLanguage("rm"),
            buildLocaleForLanguage("ro"),
            buildLocaleForLanguage("ru"),
            buildLocaleForLanguage("si", "LK"),
            buildLocaleForLanguage("sk"),
            buildLocaleForLanguage("sl"),
            buildLocaleForLanguage("sn"),
            buildLocaleForLanguage("sq"),
            buildLocaleForLanguage("sr"),
            buildLocaleForLanguage("sv"),
            buildLocaleForLanguage("sw"),
            buildLocaleForLanguage("ta"),
            buildLocaleForLanguage("te"),
            buildLocaleForLanguage("th"),
            buildLocaleForLanguage("tl"),
            buildLocaleForLanguage("tr"),
            buildLocaleForLanguage("uk"),
            buildLocaleForLanguage("ur"),
            buildLocaleForLanguage("uz"),
            buildLocaleForLanguage("vi"),
            buildLocaleForLanguage("zu")
        )
        private const val USE_SYSTEM_DEFAULT = ""
        private const val DEFAULT_STRING = "Use System Default"
        private var locale: Locale? = null
        private var singleton: Languages? = null
        private var clazz: Class<*>? = null
        private var resId = 0
        private val tmpMap: MutableMap<String, String> = TreeMap()
        private lateinit var nameMap: Map<String, String>

        /**
         * Get the instance of [Languages] to work with, providing the
         * [Activity] that is will be working as part of, as well as the
         * `resId` that has the exact string "Use System Default",
         * i.e. `R.string.use_system_default`.
         *
         *
         * That string resource `resId` is also used to find the supported
         * translations: if an included translation has a translated string that
         * matches that `resId`, then that language will be included as a
         * supported language.
         *
         * @param clazz the [Class] of the default `Activity`,
         * usually the main `Activity` from where the
         * Settings is launched from.
         * @param resId the string resource ID to for the string "Use System Default",
         * e.g. `R.string.use_system_default`
         */
        fun setup(clazz: Class<*>?, resId: Int) {
            defaultLocale = Locale.getDefault()
            if (Companion.clazz == null) {
                Companion.clazz = clazz
                Companion.resId = resId
            } else {
                throw RuntimeException("Languages singleton was already initialized, duplicate call to Languages.setup()!")
            }
        }

        /**
         * Get the singleton to work with.
         *
         * @param activity the [Activity] this is working as part of
         */
        operator fun get(activity: Activity): Languages? {
            if (singleton == null) {
                singleton = Languages(activity)
            }
            return singleton
        }

        fun setLanguage(contextWrapper: ContextWrapper, language: String?, refresh: Boolean) {
            locale =
                if (locale != null && TextUtils.equals(locale!!.language, language) && !refresh) {
                    return  // already configured
                } else if (language == null || language === USE_SYSTEM_DEFAULT) {
                    defaultLocale
                } else {
                    /* handle locales with the country in it, i.e. zh_CN, zh_TW, etc */
                    val localeSplit = language.split("_".toRegex()).toTypedArray()
                    if (localeSplit.size > 1) {
                        buildLocaleForLanguage(localeSplit[0], localeSplit[1])
                    } else {
                        buildLocaleForLanguage(language)
                    }
                }
            setLocale(contextWrapper, locale)
        }

        private fun setLocale(contextWrapper: ContextWrapper, locale: Locale?) {
            val resources = contextWrapper.resources
            val configuration = resources.configuration
            configuration.setLocale(locale)
            contextWrapper.applicationContext.createConfigurationContext(configuration)
        }
    }

    init {
        val assets = activity.assets
        val config = activity.resources.configuration
        // Resources() requires DisplayMetrics, but they are only needed for drawables
        val ignored = DisplayMetrics()
        var resources: Resources
        val localeSet: MutableSet<Locale> = LinkedHashSet()
        for (locale in localesToTest) {

            // https://stackoverflow.com/questions/56892805/how-is-the-resource-class-correctly-overwritten-to-implement-a-dynamic-translati
            // currently Android offers no non-deprecated way to do this hot swapping locale switching
            @Suppress("DEPRECATION")
            resources = Resources(assets, ignored, config)
            if (!TextUtils.equals(DEFAULT_STRING, resources.getString(resId))
                || locale == Locale.ENGLISH
            ) localeSet.add(locale)
        }
        for (locale in localeSet) {
            if (locale == TIBETAN) {
                // include English name for devices without Tibetan font support
                tmpMap[TIBETAN.toString()] = "Tibetan བོད་སྐད།" // Tibetan
            } else if (locale == Locale.SIMPLIFIED_CHINESE) {
                tmpMap[Locale.SIMPLIFIED_CHINESE.toString()] = "中文 (中国)" // Chinese (China)
            } else if (locale == Locale.TRADITIONAL_CHINESE) {
                tmpMap[Locale.TRADITIONAL_CHINESE.toString()] = "中文 (台灣)" // Chinese (Taiwan)
            } else if (locale.language.equals("pbb")) {
                tmpMap["pbb"] = "Páez"
            } else if (locale.language.equals("gum")) {
                tmpMap["gum"] = "Guambiano"
            } else if (locale.language.equals("guc")) {
                tmpMap["guc"] = "Wayuu"
            } else if (locale.language.equals("nah")) {
                tmpMap["nah"] = "Nahuatl"
            } else if (locale.country.equals("cu", true)) {
                tmpMap[locale.toString()] = "Español Cubano"
            } else {
                val displayLang = locale.getDisplayLanguage(locale).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase()
                    else it.toString()
                }
                tmpMap[locale.toString()] = "$displayLang ${locale.getDisplayCountry(locale)}"
            }
        }

        /* USE_SYSTEM_DEFAULT is a fake one for displaying in a chooser menu. */
        // localeSet.add(null);
        // tmpMap.put(USE_SYSTEM_DEFAULT, activity.getString(resId));
        nameMap = Collections.unmodifiableMap(tmpMap)
    }
}