package ru.lectograph.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lectograph.app.recording.RecordingService
import ru.lectograph.app.ui.LectographApp
import ru.lectograph.app.viewmodels.LecturesViewModel

class MainActivity : ComponentActivity() {
    private val vm: LecturesViewModel by viewModels()
    private var pendingTitle = ""
    private var pendingSubject = ""
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchRecorder()
        else Toast.makeText(this, "Для записи нужен доступ к микрофону", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTitle = savedInstanceState?.getString("pendingTitle").orEmpty()
        pendingSubject = savedInstanceState?.getString("pendingSubject").orEmpty()
        setContent {
            val choice by vm.theme.collectAsStateWithLifecycle()
            val dark = choice == "dark" || choice == "system" && isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                }
            }
            LectographApp(vm, ::startRecording)
        }
        if (intent.getBooleanExtra("quickRecord", false) && savedInstanceState == null) {
            intent.removeExtra("quickRecord"); window.decorView.post { startRecording("", "") }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingTitle", pendingTitle); outState.putString("pendingSubject", pendingSubject)
        super.onSaveInstanceState(outState)
    }
    private fun startRecording(title: String, subject: String) {
        if (RecordingService.activeId != -1L) return
        pendingTitle = title; pendingSubject = subject
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val requests = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) requests.add(Manifest.permission.POST_NOTIFICATIONS)
            permission.launch(requests.toTypedArray())
        } else launchRecorder()
    }
    private fun launchRecorder() {
        try { startForegroundService(Intent(this, RecordingService::class.java).putExtra("title", pendingTitle).putExtra("subject", pendingSubject)) }
        catch (_: RuntimeException) { Toast.makeText(this, "Не удалось запустить диктофон", Toast.LENGTH_LONG).show() }
    }
}
