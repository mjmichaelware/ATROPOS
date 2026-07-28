package com.atropos.android

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "ATROPOS\nNative Android shell\nCore wiring comes next"
        tv.textSize = 20f
        tv.setPadding(48, 48, 48, 48)
        setContentView(tv)
    }
}
