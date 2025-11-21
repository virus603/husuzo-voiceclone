package com.husuzo.voiceclone

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private lateinit var recordFile: File
    private lateinit var etText: EditText
    private lateinit var tvStatus: TextView

    // SERVER ƏVƏZ EDƏCƏKSƏN: http://192.168.1.x:5000/
    private val serverBase = "http://YOUR-SERVER-IP:5000/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etText = findViewById(R.id.etText)
        tvStatus = findViewById(R.id.tvStatus)

        val btnRecord = findViewById<Button>(R.id.btnRecordSample)
        val btnUpload = findViewById<Button>(R.id.btnUploadSample)
        val btnSynthesize = findViewById<Button>(R.id.btnSynthesize)

        btnRecord.setOnClickListener { startOrStopRecording() }
        btnUpload.setOnClickListener { uploadSample() }
        btnSynthesize.setOnClickListener { synthesizeText() }

        checkPermissions()
    }

    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val missing = perms.any { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (missing) {
            ActivityCompat.requestPermissions(this, perms, 1)
        }
    }

    private var isRecording = false

    private fun startOrStopRecording() {
        if (!isRecording) startRecording() else stopRecording()
    }

    private fun startRecording() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        recordFile = File(dir, "sample_${System.currentTimeMillis()}.3gp")

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(recordFile.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
                tvStatus.text = "Record error: ${e.message}"
            }
        }
        isRecording = true
        tvStatus.text = "Recording..."
    }

    private fun stopRecording() {
        recorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        recorder = null
        isRecording = false
        tvStatus.text = "Saved: ${recordFile.name}"
    }

    private fun uploadSample() {
        if (!::recordFile.isInitialized || !recordFile.exists()) {
            tvStatus.text = "No sample recorded"
            return
        }
        tvStatus.text = "Uploading..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = ApiClient.create(serverBase)
                val reqFile = recordFile.asRequestBody("audio/3gpp".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", recordFile.name, reqFile)
                val resp = api.uploadSample(body)
                runOnUiThread { tvStatus.text = "Upload: ${resp.isSuccessful}" }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { tvStatus.text = "Upload error: ${e.message}" }
            }
        }
    }

    private fun synthesizeText() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) { tvStatus.text = "Enter text"; return }
        tvStatus.text = "Requesting TTS..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = ApiClient.create(serverBase)
                val resp = api.synthesize(mapOf("text" to text, "voice_id" to "cloned_user"))
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    val bytes = body.bytes()
                    val outFile = File(getExternalFilesDir(null), "tts_${System.currentTimeMillis()}.mp3")
                    outFile.writeBytes(bytes)
                    runOnUiThread {
                        tvStatus.text = "Saved TTS: ${outFile.name} — Playing..."
                        playAudio(outFile.absolutePath)
                    }
                } else {
                    runOnUiThread { tvStatus.text = "TTS error: ${resp.code()} ${resp.message()}" }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { tvStatus.text = "TTS error: ${e.message}" }
            }
        }
    }

    private fun playAudio(path: String) {
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
            tvStatus.text = "Playback error: ${e.message}"
        }
    }
}
