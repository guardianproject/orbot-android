package org.torproject.android.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContextWrapper
import android.content.res.Resources
import android.text.TextUtils
import android.util.DisplayMetrics
import java.util.*

class Languages private constructor(activity: Activity) {
    /**
     * Return an array of the names of all the supported languages, sorted to
     * match what is returned by [Languages.supportedLocales].
     *
     * @return
     */
    val allNames: Array<String>
        get() = nameMap.values.toTypedArray()

    val supportedLocales: Array<String>
        get() {
            val keys = nameMap.keys
            return keys.toTypedArray()
        }

    companion object {
        private var defaultLocale: Locale? = null
        val TIBETAN = Locale("bo")
        val localesToTest = arrayOf(
            Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN,
            Locale.ITALIAN, Locale.JAPANESE, Locale.KOREAN,
            Locale.TRADITIONAL_CHINESE, Locale.SIMPLIFIED_CHINESE,
            TIBETAN, Locale("af"), Locale("am"),
            Locale("ar"), Locale("ay"), Locale("az"),
            Locale("bg"), Locale("be"), Locale("bn", "BD"),
            Locale("bn", "IN"),
            Locale("bn"), Locale("ca"), Locale("cs"),
            Locale("da"), Locale("el"), Locale("es"),
            Locale("es", "MX"),
            Locale("es", "CU"),

            Locale("es", "AR"),
            Locale("en", "GB"),
            Locale("eo"),
            Locale("et"), Locale("eu"), Locale("fa"),
            Locale("fr"),
            Locale("fi"), Locale("gl"),
            Locale("gu"),
            Locale("guc"),
            Locale("gum"),
            Locale("nah"),
            Locale("hi"),
            Locale("hr"), Locale("hu"), Locale("hy", "AM"),
            Locale("ia"),
            Locale("in"), Locale("hy"), Locale("in"),
            Locale("is"), Locale("it"), Locale("iw"),
            Locale("ka"), Locale("kk"), Locale("km"),
            Locale("kn"), Locale("ky"), Locale("lo"),
            Locale("lt"), Locale("lv"), Locale("mk"),
            Locale("ml"), Locale("mn"), Locale("mr"),
            Locale("ms"), Locale("my"), Locale("nb"),
            Locale("ne"), Locale("nl"),
            Locale("pa"),
            Locale("pbb"),

            Locale("pl"),
            Locale("pt", "BR"),
            Locale("pt"), Locale("rm"), Locale("ro"),
            Locale("ru"), Locale("si", "LK"), Locale("sk"),
            Locale("sl"), Locale("sn"), Locale("sq"), Locale("sr"),
            Locale("sv"), Locale("sw"), Locale("ta"),
            Locale("te"), Locale("th"), Locale("tl"),
            Locale("tr"), Locale("uk"), Locale("ur"),
            Locale("uz"), Locale("vi"), Locale("zu")
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
         * @return
         */
        @JvmStatic
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
         * @return
         */
        @JvmStatic
        operator fun get(activity: Activity): Languages? {
            if (singleton == null) {
                singleton = Languages(activity)
            }
            return singleton
        }

        @JvmStatic
        @SuppressLint("NewApi")
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
                        Locale(localeSplit[0], localeSplit[1])
                    } else {
                        Locale(language)
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
        activity.windowManager.defaultDisplay.getMetrics(ignored)
        var resources: Resources
        val localeSet: MutableSet<Locale> = LinkedHashSet()
        for (locale in localesToTest) {
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
                tmpMap[locale.toString()] =
                    locale.getDisplayLanguage(locale).capitalize() + " " + locale.getDisplayCountry(
                        locale
                    )
            }
        }

        /* USE_SYSTEM_DEFAULT is a fake one for displaying in a chooser menu. */
        // localeSet.add(null);
        // tmpMap.put(USE_SYSTEM_DEFAULT, activity.getString(resId));
        nameMap = Collections.unmodifiableMap(tmpMap)
    }
}