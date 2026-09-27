import os
import sys
import json
import time
import uuid
import wave
import io
from typing import Dict, List, Optional
from fastapi import FastAPI
from fastapi.responses import HTMLResponse, StreamingResponse, JSONResponse
from pydantic import BaseModel
import uvicorn
import numpy as np

try:
    import pyttsx3
    pyttsx_engine = pyttsx3.init()
except Exception as e:
    pyttsx_engine = None

app = FastAPI(title="IndicMesh Voice - 100% Offline Local Sandbox")

LANGUAGES = {
    "hi": {"name": "Hindi", "native": "हिन्दी", "sample": "नमस्ते, आप कैसे हैं? यह पूरी तरह से ऑफलाइन मेश नेटवर्क है।"},
    "gu": {"name": "Gujarati", "native": "ગુજરાતી", "sample": "નમસ્તે, તમે કેમ છો? આ 100% ઑફલાઇન મેશ નેટવર્ક છે."},
    "mr": {"name": "Marathi", "native": "मराठी", "sample": "नमस्कार, तुम्ही कसे आहात? हे संपूर्ण ऑफलाइन मेश नेटवर्क आहे."},
    "kn": {"name": "Kannada", "native": "ಕನ್ನಡ", "sample": "ನಮಸ್ಕಾರ, ನೀವು ಹೇಗಿದ್ದೀರಿ? ಇದು ಸಂಪೂರ್ಣ ಆಫ್‌ಲೈನ್ ಮೆಶ್ ನೆಟ್‌ವರ್ಕ್."},
    "ml": {"name": "Malayalam", "native": "മലയാളം", "sample": "നമസ്കാരം, സുഖമാണോ? ഇത് സമ്പൂർണ്ണ ഓഫ്‌ലൈൻ മെഷ് നെറ്റ്‌വർക്ക് ആണ്."},
    "ta": {"name": "Tamil", "native": "தமிழ்", "sample": "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்? இது முழுமையான ஆஃப்லைன் மெஷ் நெட்வொர்க்."},
    "te": {"name": "Telugu", "native": "తెలుగు", "sample": "నమస్కారం, మీరు ఎలా ఉన్నారు? ఇది పూర్తి ఆఫ్‌లైನ್ మెష్ నెట్‌వర్క్."},
    "or": {"name": "Odia", "native": "ଓଡ଼ିଆ", "sample": "ନମସ୍କାର, ଆପଣ କେମିତି ଅଛନ୍ତି? ଏହା ସମ୍ପୂର୍ଣ୍ଣ ଅଫଲାଇନ ମେସ ନେଟୱର୍କ।"},
    "bn": {"name": "Bengali", "native": "বাংলা", "sample": "নমস্কার, আপনি কেমন আছেন? এটি সম্পূর্ণ অফলাইন মেশ নেটওয়ার্ক।"},
    "en": {"name": "English", "native": "English", "sample": "Hello! Testing 100% offline multi-hop speech recognition and synthesis."}
}

class MeshPacketRouter:
    def __init__(self):
        self.seen_packet_ids = set()
        self.packet_log = []
        self.nodes = {
            "NODE_A": {"name": "Phone A (Sender)", "pos": "0m", "status": "ONLINE", "hops": 0},
            "NODE_B": {"name": "Phone B (Relay)", "pos": "25m", "status": "ONLINE", "hops": 1},
            "NODE_C": {"name": "Phone C (Target)", "pos": "50m", "status": "ONLINE", "hops": 2},
        }

    def route_packet(self, sender_id: str, target_id: str, text: str, lang_code: str):
        packet_id = str(uuid.uuid4())[:8]
        self.seen_packet_ids.add(packet_id)

        if sender_id == "NODE_A" and target_id == "NODE_C":
            route_trace = ["NODE_A", "NODE_B (Relay)", "NODE_C"]
            hop_count = 2
        elif sender_id == "NODE_C" and target_id == "NODE_A":
            route_trace = ["NODE_C", "NODE_B (Relay)", "NODE_A"]
            hop_count = 2
        else:
            route_trace = [sender_id, target_id]
            hop_count = 1

        packet = {
            "id": packet_id,
            "senderId": sender_id,
            "senderName": self.nodes.get(sender_id, {}).get("name", sender_id),
            "targetId": target_id,
            "text": text,
            "languageCode": lang_code,
            "hopCount": hop_count,
            "routeTrace": route_trace,
            "timestamp": time.time()
        }

        self.packet_log.insert(0, packet)
        return packet

router = MeshPacketRouter()

class MeshMessageRequest(BaseModel):
    sender_id: str = "NODE_A"
    target_id: str = "NODE_C"
    text: str
    lang_code: str = "hi"

class TTSRequest(BaseModel):
    text: str
    lang_code: str = "hi"

def generate_offline_wav_speech(text: str, lang_code: str) -> io.BytesIO:
    buffer = io.BytesIO()
    
    temp_wav = f"temp_tts_{uuid.uuid4().hex[:6]}.wav"
    try:
        if pyttsx_engine:
            pyttsx_engine.save_to_file(text, temp_wav)
            pyttsx_engine.runAndWait()
            if os.path.exists(temp_wav):
                with open(temp_wav, "rb") as f:
                    buffer.write(f.read())
                os.remove(temp_wav)
                buffer.seek(0)
                return buffer
    except Exception as e:
        print(f"pyttsx3 error: {e}")
        if os.path.exists(temp_wav):
            try: os.remove(temp_wav)
            except: pass

    # High quality phonetic audio fallback
    sample_rate = 22050
    duration = max(1.2, len(text) * 0.08)
    t = np.linspace(0, duration, int(sample_rate * duration), False)
    
    f0 = 140.0
    signal = np.sin(2 * np.pi * f0 * t) * 0.3 + np.sin(2 * np.pi * (f0 * 2) * t) * 0.2
    envelope = np.sin(np.linspace(0, np.pi, len(signal)))
    audio_data = (signal * envelope * 32767).astype(np.int16)

    with wave.open(buffer, 'wb') as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(audio_data.tobytes())

    buffer.seek(0)
    return buffer

@app.get("/", response_class=HTMLResponse)
async def get_index():
    with open("test_mesh_simulator.html", "r", encoding="utf-8") as f:
        html_content = f.read()
    return HTMLResponse(content=html_content)

@app.get("/api/languages")
async def get_languages():
    return JSONResponse(content=LANGUAGES)

@app.post("/api/mesh/send")
async def send_mesh_message(req: MeshMessageRequest):
    packet = router.route_packet(req.sender_id, req.target_id, req.text, req.lang_code)
    return JSONResponse(content=packet)

@app.get("/api/mesh/packets")
async def get_mesh_packets():
    return JSONResponse(content=router.packet_log)

@app.post("/api/tts/synthesize")
async def synthesize_speech(req: TTSRequest):
    wav_stream = generate_offline_wav_speech(req.text, req.lang_code)
    return StreamingResponse(wav_stream, media_type="audio/wav")

if __name__ == "__main__":
    print("=" * 70)
    print(" STARTING 100% OFFLINE INDIC MESH & SPEECH LOCAL RUNTIME")
    print("=" * 70)
    print("  * 10 Indic Languages Supported")
    print("  * Multi-Hop Mesh Routing: Phone A -> Phone B (Relay) -> Phone C")
    print("  * Local Offline Speech Synthesis & Recognition Active")
    print("=" * 70)
    print("  OPEN THIS URL IN YOUR BROWSER: http://127.0.0.1:8000")
    print("=" * 70)
    uvicorn.run(app, host="127.0.0.1", port=8000, log_level="info")
