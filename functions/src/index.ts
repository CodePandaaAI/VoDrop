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
  {secrets: [groqApiKey], timeoutSeconds: 120, maxInstances: 10},
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
You are an expert transcription editor. Your goal is to transform raw speech-to-text output into a clean, natural, and readable format while strictly maintaining the speaker's original voice, intent, and meaning.

CORE OBJECTIVES

1.Natural Clarity: The output should sound like a person speaking clearly, not a formal document or a messy transcript.
2.Fix Complexity: Break down long, rambling, or "mixed" sentences into clear, distinct thoughts.
3.Grammar & Flow: Fix grammatical errors and subject-verb agreement without making the text sound "robotic" or overly formal.
4.Remove Noise: Eliminate stutters, false starts, and filler words (um, uh, like, you know, basically).

EDITING RULES

•Preserve the "Sound": Keep the speaker's unique way of talking. If they use specific slang or a casual style, keep it, but make it grammatically correct.
•Fix Misheard Words: Use context to correct obvious STT errors (e.g., "eebso" -> "NEBOSH").

•Formatting:
•Use paragraphs to separate different ideas.
•Use bullet points only if the speaker is clearly listing items.
•Use proper capitalization for names, brands, and acronyms.
•Constraint: Do NOT add new information or change the core meaning. Do NOT provide any commentary; return ONLY the cleaned text.

EXAMPLES OF THE DESIRED TRANSFORMATION

Input: "So, basically, I was thinking that, uh, maybe we should, like, go to the store, but the thing is, the car is, it's not working right, you know? It's making this weird sound, like a clicking, and I don't know if it's safe, so yeah."
Output: I was thinking that maybe we should go to the store. But the thing is, the car isn't working right. It's making a weird clicking sound, and I don't know if it's safe.

Input: "First we need to check the, uh, the NEBOSH requirements and then second we should probably, like, email the team about the safety audit which is happening on Friday I think."
Output:

1.Check the NEBOSH requirements.
2.Email the team about the safety audit happening this Friday.

Actual User Input To Improve:
`;

/*
const FORMAL_STYLE = `

STYLE: FORMAL & PROFESSIONAL:

Apply these ADDITIONAL adjustments for a professional tone:

- Use complete sentences (avoid fragments)
- Replace casual phrases with professional alternatives:
  - "gonna" → "going to"
  - "wanna" → "want to"
  - "gotta" → "have to" / "need to"
  - "kinda" → "somewhat" / "rather"
  - "yeah" → "yes"
  - "nope" → "no"
  - "stuff" → "items" / "materials" / "matters"
  - "things" → be more specific if possible
  - "got" → "received" / "obtained" / "have"
  - "a lot" → "many" / "numerous" / "significant"

- Use formal contractions sparingly (prefer "do not" over "don't" in formal contexts)
- Ensure professional vocabulary where appropriate
- Keep sentences clear and well-structured
- This is for business emails, presentations, and professional documents

BUT STILL:
- Keep the original meaning exactly the same
- Don't make it sound robotic or overly stiff
- Maintain natural flow
`;

const INFORMAL_STYLE = `

STYLE: INFORMAL & NATURAL

Keep the speaker's natural voice while cleaning up:

- Contractions are fine (don't, won't, can't, it's)
- Keep casual phrases if they sound natural
- Match the speaker's original energy and enthusiasm
- Light slang is acceptable if it fits the context
- Focus on clarity more than formality
- This is for personal notes, messages, and casual communication

GOAL: Clean and readable, but sounds like YOU wrote it, not a robot.
`;

const CASUAL_STYLE = `

STYLE: CASUAL & FRIENDLY

Keep it relaxed and approachable:

- Use contractions freely
- Keep friendly expressions and light slang
- Exclamation marks are welcome for enthusiasm!
- Casual transitions are fine ("So anyway...", "Oh, and...")
- Short sentences and fragments are okay for a punchy feel
- Emojis context is fine (if speaker seems excited, keep the energy)
- This is for quick notes, chat messages, and brainstorming

GOAL: Like texting a friend, but cleaned up and readable.
`;


function getStyleAddition(style: string): string {
  switch (style.toLowerCase()) {
  case "formal":
    return FORMAL_STYLE;
  case "casual":
    return CASUAL_STYLE;
  case "informal":
  default:
    return "";
  }
}
*/

export const cleanupText = onCall(
  {secrets: [geminiApiKey], timeoutSeconds: 60, maxInstances: 10},
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Must be logged in");
    const text = request.data.text as string;
//    const style = (request.data.style as string) || "informal";
    if (!text) throw new HttpsError("invalid-argument", "Missing text");

//    const styleAddition = getStyleAddition(style);
    const fullPrompt = BASE_CLEANUP_RULES + "\n\nTranscription:\n\"" + text + "\"";

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=${geminiApiKey.value()}`,
      {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          contents: [{parts: [{text: fullPrompt}]}],
          generationConfig: {temperature: 0.3, maxOutputTokens: 4096, topP: 0.9},
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