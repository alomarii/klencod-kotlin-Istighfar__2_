package com.mohammed.istighfar

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var view: IstighfarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = IstighfarView(this)
        setContentView(view)
        hideBars()
    }

    override fun onResume() {
        super.onResume()
        view.start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onPause() {
        super.onPause()
        view.stop()
        view.save()
    }

    override fun onDestroy() {
        super.onDestroy()
        view.release()
    }
}