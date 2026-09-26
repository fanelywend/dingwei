package com.dingwei.gpsmock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dingwei.gpsmock.ui.MainScreen
import com.dingwei.gpsmock.ui.MockViewModel
import com.dingwei.gpsmock.ui.theme.DingweiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DingweiTheme {
                MainScreen(viewModel)
            }
        }
    }
}
