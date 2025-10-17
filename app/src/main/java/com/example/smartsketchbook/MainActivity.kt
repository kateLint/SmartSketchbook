package com.example.smartsketchbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smartsketchbook.ui.navigation.Screen
import com.example.smartsketchbook.ui.screen.SketchbookRoute
import com.example.smartsketchbook.ui.screen.SettingsScreen
import com.example.smartsketchbook.ui.theme.SmartSketchbookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartSketchbookTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Sketchbook.route,
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        composable(Screen.Sketchbook.route) {
                            SketchbookRoute()
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SmartSketchbookTheme {
        Greeting("Android")
    }
}