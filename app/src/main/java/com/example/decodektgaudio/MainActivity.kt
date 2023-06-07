package com.example.decodektgaudio

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.decodektgaudio.ui.theme.DecodeKtgAudioTheme
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val TAG = "check___"

class MainActivity : ComponentActivity() {

    private val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val pcmEncoder by lazy { Encoder(16000, 11025, 1) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val encodedFile by lazy {
        File.createTempFile("single_${System.currentTimeMillis()}", ".m4a", cacheDir)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DecodeKtgAudioTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(onClick = ::onClickEncode) {
                            Text(text = "Готовим Файл")
                        }
                    }
                }
            }
        }

    }

    private fun onClickEncode() {
        executor.submit(encodeTask(encodedFile.absolutePath))
    }

    private fun encodeTask(outputPath: String) = Runnable {
        try {
            pcmEncoder.setOutputPath(outputPath)
            pcmEncoder.prepare()
            val inputStream = assets.open("android-ktg.wav")
            inputStream.skip(44)
            pcmEncoder.encode(inputStream, 11025)
            pcmEncoder.stop()
            handler.post {
                Toast.makeText(this, "Encoded file to $outputPath", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.d(TAG, "encodeTask: error - ${e.message} ")
        }
    }

}
