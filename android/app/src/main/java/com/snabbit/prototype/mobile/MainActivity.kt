package com.snabbit.prototype.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.snabbit.prototype.mobile.navigation.SnabbitApp
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnabbitTheme {
                SnabbitApp()
            }
        }
    }
}
