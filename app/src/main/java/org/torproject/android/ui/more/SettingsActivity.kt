package org.torproject.android.ui.more

import android.os.Bundle
import android.view.MenuItem
import org.torproject.android.R
import org.torproject.android.ui.components.BaseActivity

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                onBackPressed()
            else
                finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 1)
            super.onBackPressed()
        else
            finish()
    }

    companion object {
        const val FRAGMENT_TAG = "settings"
    }
}