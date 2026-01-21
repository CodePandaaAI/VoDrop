import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import * as admin from "firebase-admin";
import fetch from "node-fetch";
import FormData from "form-data";

admin.initializeApp();

const groqApiKey = defineSecret("GROQ_API_KEY");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

// ═══════════════════════════════════════════════════════════════
// GROQ WHISPER - Speech to Text
// ═══════════════════════════════════════════════════════════════

export const transcribe = onCall(
  {
    secrets: [groqApiKey],
    timeoutSeconds: 540, // Increased to 9 mins to avoid DEADLINE_EXCEEDED
    memory: "1GiB",      // Increased to prevent OOM
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be logged in");
    }

    const audioBase64 = request.data.audio as string;
    if (!audioBase64) {
      throw new HttpsError("invalid-argument", "Missing audio");
    }

    // Convert base64 to buffer
    const audioBuffer = Buffer.from(audioBase64, "base64");

    // Prepare FormData for Groq API
    const formData = new FormData();
    formData.append("file", audioBuffer, {
      filename: "audio.wav",
      contentType: "audio/wav",
    });
    formData.append("model", "whisper-large-v3");
    formData.append("language", "en");
    formData.append("response_format", "json");

    // ✅ ACCURACY TUNING
    // Temperature 0 makes the model deterministic and focuses on high-probability words.
    // This significantly reduces "hallucinations" (making things up).
    formData.append("temperature", "0");

    // Context prompt to guide the style
    formData.append("prompt", "Voice note transcription. Clear, accurate, verbatim speech.");

    const response = await fetch("https://api.groq.com/openai/v1/audio/transcriptions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${groqApiKey.value()}`,
        // form-data headers are handled automatically by the library
      },
      body: formData,
    });

    if (!response.ok) {
        const errorText = await response.text();
        console.error("Groq API Error:", errorText);
        throw new HttpsError("internal", "Transcription failed: " + response.statusText);
    }

    const result = (await response.json()) as {text: string};
    return {text: result.text};
  }
);

// ═══════════════════════════════════════════════════════════════
// GEMINI - Text Cleanup with Full Prompts
// ═══════════════════════════════════════════════════════════════

const BASE_CLEANUP_RULES = `
You are an expert transcription editor. Your goal is to transform raw speech-to-text output into a clean, natural, and readable format while maintaining the speaker's original voice, intent, and meaning.

CORE OBJECTIVES
1. Natural Clarity: The output should sound like a person speaking clearly.
2. Fix Complexity: Break down long, rambling sentences.
3. Grammar & Flow: Fix grammatical errors without being "robotic".
4. Remove Noise: Eliminate stutters, false starts, and filler words.

EDITING RULES
- Preserve the "Sound": Keep the speaker's unique way of talking.
- Fix Misheard Words: Use context to correct obvious STT errors.

FORMATTING RULES (STRICT):
- Use paragraphs to separate different ideas.
- Use bullet points (• or -) or numbering for lists/steps.
- Use proper capitalization.
- DO NOT INTRODUCE PHYSICAL FORMATTING like **bold** or *italics*.
- The output should be plain text with structure, not Markdown styling.

EXAMPLES:

Input: "So, basically, I was thinking that, uh, maybe we should, like, go to the store, but the thing is, the car is, it's not working right, you know? It's making this weird sound, like a clicking, and I don't know if it's safe, so yeah."
Output: I was thinking that maybe we should go to the store. But the thing is, the car isn't working right. It's making a weird clicking sound, and I don't know if it's safe.

Input: "First we need to check the, uh, the NEBOSH requirements and then second we should probably, like, email the team about the safety audit which is happening on Friday I think."
Output:
1. Check the NEBOSH requirements.
2. Email the team about the safety audit happening this Friday.
`;

const FORMAL_STYLE = `
STYLE: FORMAL & PROFESSIONAL:
- Use complete sentences (avoid fragments)
- Replace casual phrases with professional alternatives: "gonna" -> "going to", "wanna" -> "want to".
- Use formal contractions sparingly.
- This is for business emails, presentations, and professional documents.
`;

const INFORMAL_STYLE = `
STYLE: INFORMAL & NATURAL
- Contractions are fine.
- Keep casual phrases if they sound natural.
- Match the speaker's original energy.
- GOAL: Clean and readable, but sounds like YOU wrote it, not a robot.
`;

const CASUAL_STYLE = `
STYLE: CASUAL & FRIENDLY
- Use contractions freely.
- Keep friendly expressions and light slang.
- Exclamation marks are welcome!
- GOAL: Like texting a friend, but cleaned up and readable.
`;

function getStyleAddition(style: string): string {
  switch (style.toLowerCase()) {
  case "formal":
    return FORMAL_STYLE;
  case "casual":
    return CASUAL_STYLE;
  case "informal":
  default:
    return INFORMAL_STYLE;
  }
}

export const cleanupText = onCall(
  {
    secrets: [geminiApiKey],
    timeoutSeconds: 300,
    memory: "512MiB",  // Increased logic memory
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Must be logged in");
    const text = request.data.text as string;
    const style = (request.data.style as string) || "informal";
    if (!text) throw new HttpsError("invalid-argument", "Missing text");

    const styleAddition = getStyleAddition(style);
    const fullPrompt = BASE_CLEANUP_RULES + styleAddition + "\n\nTranscription:\n\"" + text + "\"";

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${geminiApiKey.value()}`,
      {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          contents: [{parts: [{text: fullPrompt}]}],
          generationConfig: {
              temperature: 0.3, // Slightly higher for naturalness, but low enough for control
              maxOutputTokens: 8192,
              topP: 0.8,
              topK: 10
          },
        }),
      }
    );
    if (!response.ok) throw new HttpsError("internal", "Cleanup failed");

    interface GeminiResponse {
      candidates?: Array<{content?: {parts?: Array<{text?: string}>}}>;
    }
    const result = (await response.json()) as GeminiResponse;
    return {text: result.candidates?.[0]?.content?.parts?.[0]?.text || text};
  }
);