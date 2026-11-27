package me.capcom.smsgateway

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import me.capcom.smsgateway.helpers.LocaleHelper
import me.capcom.smsgateway.ui.HomeFragment

/** MD SMS: a single screen (no tabs). */
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.container, HomeFragment.newInstance())
            }
        }
    }

    companion object {
        const val TAB_INDEX_HOME = 0

        @Suppress("UNUSED_PARAMETER")
        fun starter(context: Context, tabIndex: Int = TAB_INDEX_HOME): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }
}
