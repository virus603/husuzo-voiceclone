package com.husuzo.voiceclone

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private lateinit var recordFile: File
    private lateinit var tvStatus: TextView
    private lateinit var etText: EditText

    // SERVER ƏVƏZ EDƏCƏKSƏN: http://192.168.1.x:5000/
    private val serverBase = "http://YOUR-SERVER-IP:5000/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etText = findViewById(R.id.etText)

        findViewById<Button>(R.id.btnRecordSample).setOnClickListener { startOrStopRecording() }
        findViewById<Button>(R.id.btnUploadSample).setOnClickListener { uploadSample() }
        findViewById<Button>(R.id.btnSynthesize).setOnClickListener { synthesizeText() }

        checkPermissions()
    }

    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (perms.any {
                ContextCompat.checkSelfPermission(this, it)
                != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, perms, 1)
        }
    }

    private var isRecording = false

    private fun startOrStopRecording() {
        if (!isRecording) startRecording()
        else stopRecording()
    }

    private fun startRecording() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        recordFile = File(dir, "sample_${System.currentTimeMillis()}.3gp")

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(recordFile.absolutePath)
            prepare()
            start()
        }

        isRecording = true
        tvStatus.text = "Səs yazılır..."
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        isRecording = false

        tvStatus.text = "Yadda saxlanıldı: ${recordFile.name}"
    }

    private fun uploadSample() {
        if (!::recordFile.isInitialized || !recordFile.exists()) {
            tvStatus.text = "Öncə səs yaz"
            return
        }

        tvStatus.text = "Yüklənir..."

        CoroutineScope(Dispatchers.IO).launch {
            val api = ApiClient.create(serverBase)

            val reqFile = recordFile
                .asRequestBody("audio/3gpp".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", recordFile.name, reqFile)

            try {
                val resp = api.uploadSample(body)
                runOnUiThread {
                    tvStatus.text = if (resp.isSuccessful) "Yükləndi" else "Xəta"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Xəta: ${e.message}"
                }
            }
        }
    }

    private fun synthesizeText() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) {
            tvStatus.text = "Mətni yaz"
            return
        }

        tvStatus.text = "Səs yaradılır..."

        CoroutineScope(Dispatchers.IO).launch {
            val api = ApiClient.create(serverBase)

            try {
                val resp = api.synthesize(
                    mapOf("text" to text, "voice_id" to "cloned_user")
                )

                if (!resp.isSuccessful) {
                    runOnUiThread { tvStatus.text = "TTS xəta: ${resp.code()}" }
                    return@launch
                }

                val bytes = resp.body()?.bytes() ?: return@launch
                val out = File(getExternalFilesDir(null),
                    "tts_${System.currentTimeMillis()}.mp3")

                out.writeBytes(bytes)

                runOnUiThread {
                    tvStatus.text = "Oynadılır..."
                    playAudio(out.absolutePath)
                }

            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "Xəta: ${e.message}" }
            }
        }
    }

    private fun playAudio(path: String) {
        val player = MediaPlayer()
        player.setDataSource(path)
        player.prepare()
        player.start()
        player.setOnCompletionListener { it.release() }
    }
}
