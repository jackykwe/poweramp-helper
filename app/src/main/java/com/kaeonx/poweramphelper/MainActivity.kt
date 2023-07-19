package com.kaeonx.poweramphelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kaeonx.poweramphelper.ui.theme.PowerampHelperTheme

internal class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PowerampHelperTheme {
                PowerampHelperApp()
            }
        }
    }
}