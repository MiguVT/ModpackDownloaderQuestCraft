package com.miguvr.miguVRModManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.miguvr.miguVRModManager.ui.theme.MiguVRModManagerTheme
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

class MainActivity : ComponentActivity() {

    private var downloadProgress = mutableStateOf(0f)
    private val filesToDownload = listOf(
        "https://cdn.modrinth.com/data/P7dR8mSH/versions/bnOsLTYu/fabric-api-0.96.0%2B1.20.4.jar",
        "https://example.com/file2.jar",
        "https://example.com/file3.jar"
    )
    private lateinit var storagePermissionRequest: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storagePermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startDownloads()
            } else {
                showToast("El permiso de almacenamiento es necesario para descargar los mods.")
            }
        }

        setContent {
            MiguVRModManagerTheme {
                MainScreen(storagePermissionRequest)
            }
        }
    }

    @Composable
    fun MainScreen(storagePermissionRequest: ActivityResultLauncher<String>) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("MiguVT Server Mod Installer", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = {
                storagePermissionRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }) {
                Text("Descargar Mods")
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(progress = downloadProgress.value)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MiguVRModManagerTheme {
            MainScreen(storagePermissionRequest = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = {}
            ))
        }
    }

    private val jsonUrl = "https://raw.githubusercontent.com/MiguVT/ModpackDownloaderQuestCraft/main/mods.json"
    private fun startDownloads() {
        // Obtén el directorio de almacenamiento como antes
        val externalStorageDir = Environment.getExternalStorageDirectory()
        val modsDirPath = "$externalStorageDir/Android/data/com.qcxr.qcxr/files/.minecraft/mods"
        val modsDir = File(modsDirPath)

        // Verifica si el directorio existe
        if (!modsDir.exists()) {
            showToast("No se ha encontrado QuestCraft, asegúrese de que esté instalado")
            return
        }

        // Comienza por obtener el JSON
        CoroutineScope(Dispatchers.IO).launch {
            val json = getJsonFromUrl(jsonUrl)
            if (json != null) {
                val urlsToDownload = parseJson(json)
                downloadFiles(urlsToDownload, modsDir)
            } else {
                withContext(Dispatchers.Main) {
                    showToast("Error al obtener la configuración de los mods.")
                }
            }
        }
    }

    private suspend fun getJsonFromUrl(urlString: String): String? {
        var json: String? = null
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            json = connection.inputStream.bufferedReader().readText()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return json
    }

    private fun parseJson(jsonString: String): Map<String, String> {
        val jsonObject = JSONObject(jsonString)
        val urlsMap = mutableMapOf<String, String>()
        jsonObject.keys().forEach {
            urlsMap[it] = jsonObject.getString(it)
        }
        return urlsMap
    }

    private fun downloadFiles(modsMap: Map<String, String>, modsDir: File) {
        CoroutineScope(Dispatchers.IO).launch {
            modsMap.forEach { (modName, urlString) ->
                val outputFile = File(modsDir, "$modName.jar")

                if (urlString == "delete") {
                    if (outputFile.exists()) {
                        outputFile.delete()
                        withContext(Dispatchers.Main) {
                            showToast("Archivo eliminado: ${outputFile.name}")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showToast("El archivo a eliminar no existe: ${outputFile.name}")
                        }
                    }
                } else {
                    try {
                        val url = URL(urlString)
                        val connection = url.openConnection()
                        connection.connect()
                        val inputStream = BufferedInputStream(url.openStream())
                        val outputStream = FileOutputStream(outputFile)

                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            showToast("Descargado: ${outputFile.name}")
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            showToast("Error descargando el archivo: ${outputFile.name}")
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                showToast("Proceso de actualización de mods completado")
                downloadProgress.value = 0f
            }
        }
    }



    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
