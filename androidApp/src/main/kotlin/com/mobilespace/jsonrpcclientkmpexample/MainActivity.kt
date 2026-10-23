package com.mobilespace.jsonrpcclientkmpexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainScreen
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainUiState
import com.vsvictor.jsonrpckmp.JsonRpcClientKmp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        JsonRpcClientKmp.installAndroidContext(applicationContext)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    MainScreen(state = MainUiState(), onAction = {})
}