package com.indic.meshvoice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.indic.meshvoice.AppLogger
import com.indic.meshvoice.mesh.MeshEngine
import com.indic.meshvoice.model.MeshMessage
import com.indic.meshvoice.model.SupportedLanguages
import com.indic.meshvoice.speech.*
import com.indic.meshvoice.ui.components.AudioWaveform
import com.indic.meshvoice.ui.components.NodeGraphVisualizer
import com.indic.meshvoice.ui.theme.*

enum class TransmissionMode(val label: String, val iconText: String) {
    VOICE_AUDIO("Voice Walkie-Talkie", "🎙️ Audio (VAD)"),
    SPEECH_TO_TEXT("Speech-To-Text", "📝 ASR (Our Model)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    meshEngine: MeshEngine,
    speechRecognizer: OfflineSpeechRecognizer,
    textToSpeech: OfflineTextToSpeech,
    voiceAudioEngine: OfflineVoiceAudioEngine
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val messages by meshEngine.messages.collectAsState()
    val connectedNodes by meshEngine.activeNodes.collectAsState()
    val packetStats by meshEngine.packetStats.collectAsState()
    val logs by AppLogger.logs.collectAsState()

    val scanStatus by meshEngine.scanStatus.collectAsState()

    val isListeningStt by speechRecognizer.isListening.collectAsState()
    val recognizedText by speechRecognizer.recognizedText.collectAsState()
    val sttAudioLevel by speechRecognizer.rmsAudioLevel.collectAsState()
    val pcmAudioLevel by voiceAudioEngine.rmsLevel.collectAsState()
    val liveRtf by speechRecognizer.liveRtf.collectAsState()
    val lastProcTime by speechRecognizer.lastProcessingMs.collectAsState()

    val vadState by voiceAudioEngine.vad.vadState.collectAsState()
    val isVadSpeaking by voiceAudioEngine.vad.isSpeechActive.collectAsState()

    var isRecordingPcm by remember { mutableStateOf(false) }
    var txMode by remember { mutableStateOf(TransmissionMode.VOICE_AUDIO) }

    val isTransmitting = if (txMode == TransmissionMode.VOICE_AUDIO) isRecordingPcm else isListeningStt
    val liveAudioLevel = if (txMode == TransmissionMode.VOICE_AUDIO) pcmAudioLevel else sttAudioLevel

    var selectedLangCode by remember { mutableStateOf("hi") }
    var selectedTargetId by remember { mutableStateOf(MeshMessage.BROADCAST_TARGET) }
    var textInput by remember { mutableStateOf("") }
    var showGraph by remember { mutableStateOf(true) }
    var showLogs by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val languageStatus by textToSpeech.readinessManager.languageStatus.collectAsState()

    // Setup VAD early-trigger and message listeners
    LaunchedEffect(Unit) {
        voiceAudioEngine.onVadEarlyTrigger = { audioBase64, durationMs ->
            isRecordingPcm = false
            val lang = SupportedLanguages.getByCode(selectedLangCode)
            meshEngine.sendVoiceMessage(
                text = lang.sampleText,
                audioBase64 = audioBase64,
                targetId = selectedTargetId,
                langCode = selectedLangCode
            )
            mainHandler.post {
                Toast.makeText(context, "⚡ VAD Auto-Transmitted (${durationMs}ms) across Mesh!", Toast.LENGTH_SHORT).show()
            }
        }

        meshEngine.onSpeechMessageReceived = { message ->
            AppLogger.log("MainScreen", "Received message from ${message.senderName}: \"${message.text}\" (hasAudio: ${message.audioBase64 != null})")
            if (message.audioBase64 != null) {
                voiceAudioEngine.playPcmAudio(message.audioBase64)
            } else if (message.text.isNotBlank()) {
                textToSpeech.speak(message.text, message.languageCode)
            }
        }

        speechRecognizer.onFinalResult = { transcribed, lang ->
            if (transcribed.isNotBlank()) {
                AppLogger.log("MainScreen", "STT transcribed: \"$transcribed\"")
                meshEngine.sendVoiceTranscript(
                    text = transcribed,
                    targetId = selectedTargetId,
                    langCode = lang
                )
                mainHandler.post {
                    Toast.makeText(context, "📡 Transcribed & Transmitted across Mesh!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "IndicMesh Voice",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "100% OFFLINE",
                                    color = NeonEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Node: ${meshEngine.myNodeId} • Peers: ${connectedNodes.size} • Engine: OUR MODEL",
                            color = if (connectedNodes.isNotEmpty()) NeonEmerald else CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDiagnosticsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Language & Model Studio",
                            tint = CyberCyan
                        )
                    }
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Toggle Live Logs",
                            tint = if (showLogs) SaffronOrange else TextSecondary
                        )
                    }
                    IconButton(onClick = { showGraph = !showGraph }) {
                        Icon(
                            imageVector = if (showGraph) Icons.Default.Hub else Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Toggle Topology View",
                            tint = if (showGraph) CyberCyan else TextSecondary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // Responsive Stats Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(DarkSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(vertical = 5.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("SENT", "${packetStats.totalSent}", CyberCyan)
                StatItem("RECEIVED", "${packetStats.totalReceived}", NeonEmerald)
                StatItem("RTF", String.format("%.2f", liveRtf), SaffronOrange)
                StatItem("VAD", if (isVadSpeaking) "ACTIVE" else "180ms", if (isVadSpeaking) NeonEmerald else TextSecondary)
                StatItem("PEERS", "${connectedNodes.size}", if (connectedNodes.isNotEmpty()) NeonEmerald else ElectricIndigo)
            }

            // Language Selector Chips (10 Indic Languages)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT LANGUAGE:",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                val currentDiag = languageStatus[selectedLangCode]
                Text(
                    text = currentDiag?.selectedTier?.badge ?: "OUR MODEL (ACTIVE)",
                    color = NeonEmerald,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SupportedLanguages.ALL) { lang ->
                    val isSelected = lang.code == selectedLangCode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) SaffronOrange else DarkSurface)
                            .border(
                                1.dp,
                                if (isSelected) SaffronOrange else DarkBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedLangCode = lang.code
                                textInput = lang.sampleText
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = lang.nativeName,
                                color = if (isSelected) DarkBg else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = lang.displayName,
                                color = if (isSelected) DarkBg.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            // Dynamic Visualizer Section (Adaptive for tablets and all phones)
            if (showGraph) {
                NodeGraphVisualizer(
                    myNodeId = meshEngine.myNodeId,
                    connectedNodes = connectedNodes,
                    scanStatus = scanStatus,
                    onRetryScan = {
                        meshEngine.restartMesh()
                        mainHandler.post {
                            Toast.makeText(context, "🔄 Re-scanning for nearby mesh peers...", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(Modifier.height(3.dp))

            // Push-to-Talk / Transmit Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(14.dp))
                    .border(1.dp, if (isTransmitting) CyberCyan else DarkBorder, RoundedCornerShape(14.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AudioWaveform(isRecording = isTransmitting, audioLevel = liveAudioLevel)

                    Spacer(Modifier.height(3.dp))

                    val currentLang = SupportedLanguages.getByCode(selectedLangCode)
                    if (isTransmitting) {
                        Text(
                            text = if (txMode == TransmissionMode.VOICE_AUDIO)
                                (if (isVadSpeaking) "🎙️ VAD: Voice Detected! (${currentLang.displayName})" else "⚡ VAD: Listening (Auto-Cutoff on 180ms pause)")
                            else
                                "📝 Transcribing in ${currentLang.displayName} (Our Offline Model)...",
                            color = if (isVadSpeaking) NeonEmerald else CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (recognizedText.isNotBlank()) {
                        Text(
                            text = "✅ Transcribed: \"$recognizedText\"",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = "Tap Mic to transmit in ${currentLang.displayName} (${txMode.label})",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Switcher Button
                        OutlinedButton(
                            onClick = {
                                txMode = if (txMode == TransmissionMode.VOICE_AUDIO)
                                    TransmissionMode.SPEECH_TO_TEXT
                                else
                                    TransmissionMode.VOICE_AUDIO
                            },
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(txMode.iconText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(14.dp))

                        // Safe Unified Mic Button (Exclusive Audio Capture + VAD)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = if (isTransmitting) listOf(CrimsonAlert, SaffronOrange) else listOf(CyberCyan, ElectricIndigo)
                                    )
                                )
                                .clickable {
                                    if (txMode == TransmissionMode.VOICE_AUDIO) {
                                        if (isRecordingPcm) {
                                            isRecordingPcm = false
                                            val audioBase64 = voiceAudioEngine.stopRecording()
                                            if (audioBase64 != null) {
                                                meshEngine.sendVoiceMessage(
                                                    text = currentLang.sampleText,
                                                    audioBase64 = audioBase64,
                                                    targetId = selectedTargetId,
                                                    langCode = selectedLangCode
                                                )
                                                mainHandler.post {
                                                    Toast.makeText(context, "📡 Voice transmitted across mesh!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            val started = voiceAudioEngine.startRecording()
                                            if (started) isRecordingPcm = true
                                        }
                                    } else {
                                        // STT Mode (Our Model)
                                        if (isListeningStt) {
                                            speechRecognizer.stopListening()
                                        } else {
                                            speechRecognizer.startListening(selectedLangCode)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTransmitting) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Toggle Recording",
                                tint = if (isTransmitting) Color.White else DarkBg,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        // 🔊 Test Local TTS Button
                        IconButton(
                            onClick = {
                                textToSpeech.speak(currentLang.sampleText, currentLang.code)
                                mainHandler.post {
                                    Toast.makeText(context, "🔊 Synthesizing in ${currentLang.displayName}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkBorder)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Test Local TTS",
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Quick Text Input Fallback / Direct Send
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Or type phrase in ${currentLang.displayName}...", color = TextMuted, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = CyberCyan
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    meshEngine.sendVoiceTranscript(
                                        text = textInput,
                                        targetId = selectedTargetId,
                                        langCode = selectedLangCode
                                    )
                                    mainHandler.post {
                                        Toast.makeText(context, "📡 Sent to Mesh!", Toast.LENGTH_SHORT).show()
                                    }
                                    textInput = ""
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan)
                                .size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send text over mesh",
                                tint = DarkBg
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            // Expandable Live Logs Console OR Live Transcripts
            if (showLogs) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070B14)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, SaffronOrange.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🐞 LIVE ENGINE & SPEECH LOGS",
                                color = SaffronOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                TextButton(
                                    onClick = { textToSpeech.speak("Testing our offline speech synthesis", "en") },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("🔊 Test", color = CyberCyan, fontSize = 9.sp)
                                }
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("IndicMesh Logs", AppLogger.getAllLogsText())
                                        clipboard.setPrimaryClip(clip)
                                        mainHandler.post {
                                            Toast.makeText(context, "📋 Copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("📋 Copy", color = NeonEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { AppLogger.clear() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("Clear", color = TextMuted, fontSize = 9.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("Error", true)) CrimsonAlert else if (log.contains("STT", true) || log.contains("VAD", true)) CyberCyan else if (log.contains("TTS", true) || log.contains("Model", true)) SaffronOrange else TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 12.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "LIVE MESH TRANSCRIPTS & PACKET FEED",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 1.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No messages yet. Speak or type to transmit over mesh.",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    items(messages) { msg ->
                        MessageCard(
                            message = msg,
                            isFromMe = msg.senderId == meshEngine.myNodeId,
                            onPlayTts = {
                                textToSpeech.speak(msg.text, msg.languageCode)
                            },
                            onPlayVoice = {
                                if (msg.audioBase64 != null) {
                                    voiceAudioEngine.playPcmAudio(msg.audioBase64)
                                } else {
                                    textToSpeech.speak(msg.text, msg.languageCode)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 🌐 Model & Language Studio Dialog
    if (showDiagnosticsDialog) {
        Dialog(onDismissRequest = { showDiagnosticsDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "STT & TTS Model Studio",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Default: Our Embedded Model • Manual Override",
                                color = NeonEmerald,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(onClick = { showDiagnosticsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(SupportedLanguages.ALL) { lang ->
                            val diag = languageStatus[lang.code]
                            val selectedTier = diag?.selectedTier ?: TtsTier.TIER_2_BUNDLED_NEURAL

                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${lang.displayName} (${lang.nativeName})",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Code: ${lang.code} • Sample: \"${lang.sampleText}\"",
                                                color = TextMuted,
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                textToSpeech.speak(lang.sampleText, lang.code)
                                                mainHandler.post {
                                                    Toast.makeText(context, "Playing ${lang.displayName} via ${selectedTier.badge}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Test speech",
                                                tint = CyberCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    // Tier Selection Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        TtsTier.values().forEach { tier ->
                                            val isChosen = tier == selectedTier
                                            val isAvailable = when (tier) {
                                                TtsTier.TIER_1_SYSTEM_HD -> diag?.isTier1Available ?: false
                                                TtsTier.TIER_2_BUNDLED_NEURAL -> true
                                                TtsTier.TIER_3_FAILSAFE -> true
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isChosen) CyberCyan else DarkSurface)
                                                    .border(
                                                        1.dp,
                                                        if (isChosen) CyberCyan else if (isAvailable) DarkBorder else CrimsonAlert.copy(alpha = 0.5f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        textToSpeech.readinessManager.setPreferredTier(lang.code, tier)
                                                    }
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = tier.badge,
                                                    color = if (isChosen) DarkBg else if (isAvailable) TextSecondary else TextMuted,
                                                    fontSize = 8.sp,
                                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(text = label, color = TextMuted, fontSize = 8.sp)
    }
}

@Composable
private fun MessageCard(
    message: MeshMessage,
    isFromMe: Boolean,
    onPlayTts: () -> Unit,
    onPlayVoice: () -> Unit
) {
    val lang = SupportedLanguages.getByCode(message.languageCode)

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isFromMe) DarkCard else DarkSurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isFromMe) CyberCyan.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isFromMe) CyberCyan else SaffronOrange)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isFromMe) "TRANSMITTED" else "RECEIVED: ${message.senderName}",
                            color = DarkBg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "• ${lang.displayName} (${lang.nativeName})",
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }

                // Hop Count Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkBorder)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (message.hopCount == 0) "Direct" else "${message.hopCount} Hops",
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                }
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = message.text,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Route: " + message.routeTrace.joinToString(" ➔ "),
                    color = TextMuted,
                    fontSize = 8.sp
                )

                Row {
                    if (message.audioBase64 != null) {
                        IconButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play original voice audio",
                                tint = NeonEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                    IconButton(
                        onClick = onPlayTts,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak using offline TTS",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
