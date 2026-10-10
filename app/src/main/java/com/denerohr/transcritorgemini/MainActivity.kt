package com.denerohr.transcritorgemini

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.denerohr.transcritorgemini.data.AppDatabase
import com.denerohr.transcritorgemini.data.TranscriptionDao
import com.denerohr.transcritorgemini.data.TranscriptionItem
import com.denerohr.transcritorgemini.data.UserPreferences
import com.denerohr.transcritorgemini.worker.TranscriptionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = AppDatabase.getDatabase(this).transcriptionDao()
        val prefs = UserPreferences(this)
        
        handleIntent(intent, db)

        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf("history") }
                
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == "history",
                                onClick = { currentScreen = "history" },
                                icon = { Text("Histórico") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == "settings",
                                onClick = { currentScreen = "settings" },
                                icon = { Text("Configurações") }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        if (currentScreen == "history") {
                            HistoryScreen(db)
                        } else {
                            SettingsScreen(prefs)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val db = AppDatabase.getDatabase(this).transcriptionDao()
        handleIntent(intent, db)
    }

    private fun handleIntent(intent: Intent, db: TranscriptionDao) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val newItem = TranscriptionItem(
                        fileName = uri.lastPathSegment ?: "Media_${System.currentTimeMillis()}",
                        status = "AGUARDANDO",
                        mediaUri = uri.toString()
                    )
                    val id = db.insert(newItem)
                    
                    val inputData = Data.Builder()
                        .putLong("transcription_id", id)
                        .putString("media_uri", uri.toString())
                        .build()

                    val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                        .setInputData(inputData)
                        .build()

                    WorkManager.getInstance(this@MainActivity).enqueue(request)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(db: TranscriptionDao) {
    val items by db.getAll().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { 
            coroutineScope.launch(Dispatchers.IO) { db.clearAll() } 
        }) {
            Text("Limpar Histórico")
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn {
            items(items) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Ficheiro: ${item.fileName}", style = MaterialTheme.typography.titleMedium)
                        Text(text = "Estado: ${item.status}")
                        if (item.transcriptionText != null) {
                            Text(text = "Texto: ${item.transcriptionText}", modifier = Modifier.padding(top = 8.dp))
                        }
                        if (item.errorMessage != null) {
                            Text(text = "Erro: ${item.errorMessage}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(prefs: UserPreferences) {
    val coroutineScope = rememberCoroutineScope()
    val apiKey by prefs.apiKey.collectAsState(initial = "")
    val promptSummary by prefs.promptSummary.collectAsState(initial = "")
    val promptReview by prefs.promptReview.collectAsState(initial = "")

    var keyInput by remember(apiKey) { mutableStateOf(apiKey ?: "") }
    var summaryInput by remember(promptSummary) { mutableStateOf(promptSummary) }
    var reviewInput by remember(promptReview) { mutableStateOf(promptReview) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Chave da API Gemini") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = summaryInput,
            onValueChange = { summaryInput = it },
            label = { Text("Prompt de Resumo") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = reviewInput,
            onValueChange = { reviewInput = it },
            label = { Text("Prompt de Revisão") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            coroutineScope.launch {
                prefs.saveApiKey(keyInput)
                prefs.savePrompts(summaryInput, reviewInput)
            }
        }) {
            Text("Guardar Configurações")
        }
    }
}
