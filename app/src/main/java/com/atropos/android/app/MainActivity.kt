package com.atropos.android.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            text = "ATROPOS SYSTEM ONLINE"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }
}
