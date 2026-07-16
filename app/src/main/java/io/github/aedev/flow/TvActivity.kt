package io.github.aedev.flow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Leanback launcher bridge that keeps [MainActivity] as Flow's single runtime host. */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forwardedIntent = Intent(intent).apply {
            setClass(this@TvActivity, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(forwardedIntent)
        finish()
    }
}
