package org.torproject.android.ui.more

import android.os.Bundle
import android.view.MenuItem
import org.torproject.android.R
import org.torproject.android.ui.core.BaseActivity

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.menu_settings)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .addToBackStack(FRAGMENT_TAG)
            .replace(R.id.settings_container, SettingsPreferenceFragment())
            .commit()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 1)
                onBackPressedDispatcher.onBackPressed()
            else
                finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val FRAGMENT_TAG = "settings"
    }
}