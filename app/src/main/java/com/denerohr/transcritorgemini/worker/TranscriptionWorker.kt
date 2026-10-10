package com.denerohr.transcritorgemini.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.denerohr.transcritorgemini.data.AppDatabase
import com.denerohr.transcritorgemini.data.UserPreferences
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val transcriptionId = inputData.getLong("transcription_id", -1)
        val mediaUriString = inputData.getString("media_uri")

        if (transcriptionId == -1L || mediaUriString == null) {
            return Result.failure()
        }

        val db = AppDatabase.getDatabase(applicationContext).transcriptionDao()
        val prefs = UserPreferences(applicationContext)
        val apiKey = prefs.apiKey.firstOrNull()
        val transMode = prefs.transcriptionMode.firstOrNull() ?: "SMART"
        
        val item = db.getById(transcriptionId) ?: return Result.failure()

        if (apiKey.isNullOrBlank()) {
            db.update(item.copy(status = "ERRO", errorMessage = "Chave da API não configurada."))
            return Result.failure()
        }

        db.update(item.copy(status = "PROCESSANDO"))

        try {
            val uri = Uri.parse(mediaUriString)
            val safUrl = FFmpegKitConfig.getSafParameterForRead(applicationContext, uri)
            val outputFile = File(applicationContext.cacheDir, "audio_${transcriptionId}.mp3")
            
            val ffmpegCommand = "-y -i \"$safUrl\" -vn -acodec libmp3lame -ab 64k -ac 1 \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(ffmpegCommand)
            
            if (!session.returnCode.isValueSuccess) {
                db.update(item.copy(status = "ERRO", errorMessage = "Falha na extração de áudio via FFmpeg."))
                return Result.failure()
            }

            val audioBytes = outputFile.readBytes()
            outputFile.delete()

            // Removido o modelo live problemático. Mantido apenas o transcribe base.
            val models = listOf("gemini-3.5-transcribe")
            var transcribedText: String? = null
            var lastError: String? = null
            
            val instructionPrompt = when (transMode) {
                "VERBATIM" -> "Transcreva o áudio em português ipsis litteris, preservando todas as hesitações, gagueiras e falsos começos, seguindo estritamente as palavras faladas."
                "diarization_mode" -> "Transcreva o áudio em português identificando ativamente os oradores diferentes com marcadores (ex: Orador 1, Orador 2)."
                else -> "Transcreva o áudio em português no modo SMART: limpe os vícios de linguagem, faça autocorreções lógicas e formate elementos complexos como listas e numerais."
            }

            for (modelName in models) {
                val generativeModel = GenerativeModel(modelName = modelName, apiKey = apiKey)
                var success = false
                
                for (attempt in 1..3) {
                    try {
                        val response = generativeModel.generateContent(
                            content {
                                blob("audio/mp3", audioBytes)
                                text(instructionPrompt)
                            }
                        )
                        transcribedText = response.text
                        success = true
                        break
                    } catch (e: Exception) {
                        lastError = e.message ?: "Erro de rede ou timeout."
                        Log.e("TranscriptionWorker", "Tentativa $attempt falhou no modelo $modelName: $lastError")
                    }
                }
                if (success) break
            }

            if (transcribedText == null) {
                db.update(item.copy(status = "ERRO", errorMessage = "Falha total após retentativas: $lastError"))
                return Result.failure()
            }

            db.update(item.copy(status = "CONCLUIDO", transcriptionText = transcribedText))
            return Result.success()

        } catch (e: Exception) {
            db.update(item.copy(status = "ERRO", errorMessage = e.message ?: "Erro crítico inesperado."))
            return Result.failure()
        }
    }
}
