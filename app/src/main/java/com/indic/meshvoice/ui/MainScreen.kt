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
import com.indic.meshvoice.AppLogger
import com.indic.meshvoice.mesh.MeshEngine
import com.indic.meshvoice.model.MeshMessage
import com.indic.meshvoice.model.SupportedLanguages
import com.indic.meshvoice.speech.Ai4BharatIndicASR
import com.indic.meshvoice.speech.Ai4BharatIndicTTS
import com.indic.meshvoice.speech.OfflineVoiceAudioEngine
import com.indic.meshvoice.ui.components.AudioWaveform
import com.indic.meshvoice.ui.components.NodeGraphVisualizer
import com.indic.meshvoice.ui.theme.*

enum class TransmissionMode(val label: String, val iconText: String) {
    VOICE_AUDIO("Voice Walkie-Talkie", "🎙️ Walkie-Talkie (VAD)"),
    SPEECH_TO_TEXT("AI4Bharat STT", "📝 AI4Bharat ASR")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    meshEngine: MeshEngine,
    indicAsr: Ai4BharatIndicASR,
    indicTts: Ai4BharatIndicTTS,
    voiceAudioEngine: OfflineVoiceAudioEngine
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val messages by meshEngine.messages.collectAsState()
    val connectedNodes by meshEngine.activeNodes.collectAsState()
    val packetStats by meshEngine.packetStats.collectAsState()
    val logs by AppLogger.logs.collectAsState()

    val scanStatus by meshEngine.scanStatus.collectAsState()

    val isListeningAsr by indicAsr.isListening.collectAsState()
    val recognizedText by indicAsr.recognizedText.collectAsState()
    val liveRtf by indicAsr.liveRtf.collectAsState()
    val pcmAudioLevel by voiceAudioEngine.rmsLevel.collectAsState()

    val vadState by voiceAudioEngine.vad.vadState.collectAsState()
    val isVadSpeaking by voiceAudioEngine.vad.isSpeechActive.collectAsState()

    var isRecordingPcm by remember { mutableStateOf(false) }
    var txMode by remember { mutableStateOf(TransmissionMode.VOICE_AUDIO) }

    val isTransmitting = if (txMode == TransmissionMode.VOICE_AUDIO) isRecordingPcm else isListeningAsr

    var selectedLangCode by remember { mutableStateOf("hi") }
    var selectedTargetId by remember { mutableStateOf(MeshMessage.BROADCAST_TARGET) }
    var textInput by remember { mutableStateOf("") }
    var showGraph by remember { mutableStateOf(true) }
    var showLogs by remember { mutableStateOf(false) }

    // Lazy load model on language selection
    LaunchedEffect(selectedLangCode) {
        indicAsr.ensureLanguageLoaded(selectedLangCode)
    }

    // Setup VAD early-trigger and message listeners
    LaunchedEffect(Unit) {
        // VAD Trigger for Walkie-Talkie Voice Audio
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
                Toast.makeText(context, "⚡ VAD Auto-Transmitted (${durationMs}ms) to Mesh!", Toast.LENGTH_SHORT).show()
            }
        }

        // VAD Trigger for AI4Bharat ASR Mode
        voiceAudioEngine.onVadAsrTrigger = { durationMs ->
            indicAsr.finalizeFromVad(durationMs)
        }

        meshEngine.onSpeechMessageReceived = { message ->
            AppLogger.log("MainScreen", "Received message from ${message.senderName}: \"${message.text}\" (hasAudio: ${message.audioBase64 != null})")
            if (message.audioBase64 != null) {
                voiceAudioEngine.playPcmAudio(message.audioBase64)
            } else if (message.text.isNotBlank()) {
                indicTts.speak(message.text, message.languageCode)
            }
        }

        indicAsr.onFinalResult = { transcribed, lang ->
            if (transcribed.isNotBlank()) {
                AppLogger.log("MainScreen", "AI4Bharat ASR transcribed: \"$transcribed\"")
                meshEngine.sendVoiceTranscript(
                    text = transcribed,
                    targetId = selectedTargetId,
                    langCode = lang
                )
                mainHandler.post {
                    Toast.makeText(context, "📡 Transcribed & Transmitted to Mesh!", Toast.LENGTH_SHORT).show()
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
                                    text = "AI4BHARAT QUANTIZED",
                                    color = NeonEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Node: ${meshEngine.myNodeId} • Peers: ${connectedNodes.size} • 100% Offline",
                            color = if (connectedNodes.isNotEmpty()) NeonEmerald else CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
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
            // Live Stats Banner
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
                StatItem("VAD", if (isVadSpeaking) "SPEAKING" else "180ms", if (isVadSpeaking) NeonEmerald else TextSecondary)
                StatItem("PEERS", "${connectedNodes.size}", if (connectedNodes.isNotEmpty()) NeonEmerald else ElectricIndigo)
            }

            // Language Selector Chips (10 Indic Languages - Lazy Loaded)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT LANGUAGE (10 INDIC LANGUAGES):",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = "LAZY LOADED",
                    color = CyberCyan,
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
                                indicAsr.ensureLanguageLoaded(lang.code)
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

            // Dynamic Mesh Topology Visualizer
            if (showGraph) {
                NodeGraphVisualizer(
                    myNodeId = meshEngine.myNodeId,
                    connectedNodes = connectedNodes,
                    scanStatus = scanStatus,
                    onRetryScan = {
                        meshEngine.restartMesh()
                        mainHandler.post {
                            Toast.makeText(context, "🔄 Scanning for nearby mesh peers...", Toast.LENGTH_SHORT).show()
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
                    AudioWaveform(isRecording = isTransmitting, audioLevel = pcmAudioLevel)

                    Spacer(Modifier.height(3.dp))

                    val currentLang = SupportedLanguages.getByCode(selectedLangCode)
                    if (isTransmitting) {
                        Text(
                            text = if (txMode == TransmissionMode.VOICE_AUDIO)
                                (if (isVadSpeaking) "🎙️ VAD: Voice Detected! (${currentLang.displayName})" else "⚡ VAD: Auto-Cutoff on 180ms pause")
                            else
                                "📝 AI4Bharat ASR: Transcribing in ${currentLang.displayName}...",
                            color = if (isVadSpeaking) NeonEmerald else CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (recognizedText.isNotBlank()) {
                        Text(
                            text = "✅ AI4Bharat ASR: \"$recognizedText\"",
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

                        // Walkie-Talkie Push-to-Talk Mic Button
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
                                            val started = voiceAudioEngine.startRecording(asrMode = false)
                                            if (started) isRecordingPcm = true
                                        }
                                    } else {
                                        // AI4Bharat ASR Mode with VAD
                                        if (isListeningAsr) {
                                            voiceAudioEngine.stopRecording()
                                            indicAsr.stopListening()
                                        } else {
                                            indicAsr.startListening(selectedLangCode)
                                            voiceAudioEngine.startRecording(asrMode = true)
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

                        // 🔊 Test AI4Bharat IndicTTS Button
                        IconButton(
                            onClick = {
                                indicTts.speak(currentLang.sampleText, currentLang.code)
                                mainHandler.post {
                                    Toast.makeText(context, "🔊 Synthesizing ${currentLang.displayName} (AI4Bharat TTS)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkBorder)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Test AI4Bharat TTS",
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
                                text = "🐞 LIVE AI4BHARAT ENGINE LOGS",
                                color = SaffronOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                TextButton(
                                    onClick = { indicTts.speak("Testing AI4Bharat offline speech synthesis", "en") },
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
                                    color = if (log.contains("Error", true)) CrimsonAlert else if (log.contains("ASR", true) || log.contains("VAD", true)) CyberCyan else if (log.contains("TTS", true) || log.contains("Model", true)) SaffronOrange else TextSecondary,
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
                                indicTts.speak(msg.text, msg.languageCode)
                            },
                            onPlayVoice = {
                                if (msg.audioBase64 != null) {
                                    voiceAudioEngine.playPcmAudio(msg.audioBase64)
                                } else {
                                    indicTts.speak(msg.text, msg.languageCode)
                                }
                            }
                        )
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
                            contentDescription = "Speak using AI4Bharat TTS",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
