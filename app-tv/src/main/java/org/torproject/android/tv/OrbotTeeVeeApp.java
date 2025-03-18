package org.torproject.android.tv;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import org.torproject.android.core.Languages;
import org.torproject.android.core.LocaleHelper;
import org.torproject.android.service.util.Prefs;

import java.util.Locale;

public class OrbotTeeVeeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Languages.setup(TeeveeMainActivity.class, R.string.menu_settings);

        if (!Prefs.getDefaultLocale().equals(Locale.getDefault().getLanguage())) {
            Languages.setLanguage(this, Prefs.getDefaultLocale(), true);
        }

        //no hosting of onion services!
        Prefs.putHostOnionServicesEnabled(false);
    }

    @Override
    protected void attachBaseContext(Context base) {
        Prefs.setContext(base);
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!Prefs.getDefaultLocale().equals(Locale.getDefault().getLanguage()))
            Languages.setLanguage(this, Prefs.getDefaultLocale(), true);
    }
}