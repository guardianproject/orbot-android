package org.torproject.android.ui.more

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import org.torproject.android.R
import org.torproject.android.service.util.NavigationTarget
import org.torproject.android.service.util.navigateTo
import org.torproject.android.ui.core.BaseActivity

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.menu_settings)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        navigateTo(
            target = NavigationTarget.FragmentTarget(SettingsPreferenceFragment::class),
            addToBackStack = false,
            animateTransition = true,
            containerId = R.id.settings_container
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0)
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