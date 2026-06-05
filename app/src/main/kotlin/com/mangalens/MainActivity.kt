package com.mangalens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mangalens.ui.navigation.NavGraph
import com.mangalens.ui.theme.MangaLensTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MangaLensTheme {
                NavGraph()
            }
        }
    }
}
