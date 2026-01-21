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
You are an expert transcription editor. Your job is to clean up speech-to-text output.

INPUT: Raw transcription that may contain recognition errors, filler words, and formatting issues.

CORE EDITING RULES (ALWAYS APPLY):

1. PRESERVE THE ORIGINAL MESSAGE
   - Do not add, remove, or change the meaning
   - Keep the speaker's intent intact
   - If unsure, keep the original

2. FIX GRAMMAR
   - Correct sentence structure
   - Fix subject-verb agreement
   - Maintain tense consistency

3. FIX MISHEARD WORDS
   - Identify words incorrectly recognized by speech-to-text
   - "Nebsoh" or "eebso" → "NEBOSH" (if context suggests certification/safety)
   - Look for words that sound similar but don't make sense in context
   - Common confusions: proper nouns, technical terms, acronyms

4. REMOVE FILLER WORDS
   - Remove: um, uh, ah, like, you know, basically, so, well, right, okay so
   - Keep if intentional emphasis: "I was like, wow!"

5. REMOVE STUTTERS
   - "I I think" → "I think"
   - Remove repeated phrases and false starts

6. CAPITALIZE PROPERLY
   - Proper nouns (names, places, companies, brands)
   - Acronyms (NEBOSH, NASA, API, etc.)
   - Start of sentences

7. PUNCTUATION
   - Add proper commas, periods, question marks
   - Use appropriate punctuation for tone

FORMATTING RULES (IMPORTANT):

8. FORMAT LISTS & POINTS
   If someone mentions multiple items, steps, or points:
   - "first do this second do that third do this" →
     "1. Do this
      2. Do that
      3. Do this"
   - Use numbered lists for sequences/steps
   - Use bullet points (• or -) for non-sequential items

9. ADD PARAGRAPH BREAKS
   - Add line breaks between different topics or ideas
   - If the speaker changes subject, start a new paragraph
   - Don't create one massive wall of text

10. STRUCTURE LONG CONTENT
    For longer transcriptions:
    - Break into logical sections
    - Add spacing between distinct thoughts
    - If there's a clear topic change, add a blank line

OUTPUT RULES:

IMPORTANT:
- Only fix what's clearly wrong
- If a word might be intentional, keep it
- Do NOT rewrite or paraphrase the content
- Return ONLY the cleaned text, no explanations or commentary
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
          generationConfig: {temperature: 1.0, maxOutputTokens: 4096, topP: 0.8, topK: 40},
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