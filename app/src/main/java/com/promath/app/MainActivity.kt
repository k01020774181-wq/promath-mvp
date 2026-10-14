package com.promath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.promath.app.ui.MockupApp
import com.promath.app.ui.PromathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(0xFF080808.toInt()), SystemBarStyle.dark(0xFF080808.toInt()))
        setContent { PromathTheme { MockupApp() } }
    }
}
