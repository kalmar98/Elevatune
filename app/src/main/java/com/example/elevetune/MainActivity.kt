package com.example.elevetune

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
import com.example.elevetune.ui.theme.ElevetuneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElevetuneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
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
    ElevetuneTheme {
        Greeting("Android")
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    Column {
        Button(onClick = { viewModel.pickReference() }) { Text("Load Reference Vocal (WAV)") }
        Button(onClick = { viewModel.pickInstrumental() }) { Text("Load Instrumental") }
        Button(onClick = { viewModel.analyzeReference() }) { Text("Analyze Reference") }
        Button(onClick = { viewModel.startSession() }) { Text("Start") }
        Text("User pitch: ${viewModel.userPitchHz}")
        Text("Ref pitch: ${viewModel.refPitchHz}")
        Text("Deviation (cents): ${viewModel.centsDeviation}")
    }
}