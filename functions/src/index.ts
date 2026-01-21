import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { v2 } from "@google-cloud/speech";

admin.initializeApp();

const geminiApiKey = defineSecret("GEMINI_API_KEY");

// 1. Point Client to the Mumbai Endpoint (asia-south1)
const speechClient = new v2.SpeechClient({
  apiEndpoint: "asia-south1-speech.googleapis.com"
});

export const transcribeChirp = onCall(
  {
    timeoutSeconds: 540,
    memory: "512MiB",
    maxInstances: 10,
  },
  async (request) => {
    // DEBUG LOG: This will show up in your Firebase Console Logs
    console.log("v2.1: Starting Asia-South1 Transcription");

    if (!request.auth) throw new HttpsError("unauthenticated", "Must be logged in");

    const gcsUri = request.data.gcsUri as string;
    if (!gcsUri) throw new HttpsError("invalid-argument", "Missing gcsUri");

    const projectId = process.env.GCLOUD_PROJECT || "post-3424f";

    // Extract bucket name
    const matches = gcsUri.match(/gs:\/\/([^\/]+)\//);
    const bucketName = matches ? matches[1] : "post-3424f.firebasestorage.app";

    // Create unique output path
    const outputPrefix = `transcripts/${Date.now()}_${Math.random().toString(36).substring(7)}/`;
    const outputUri = `gs://${bucketName}/${outputPrefix}`;

    // 2. CRITICAL: This string MUST say "asia-south1", NOT "global"
    const recognizer = `projects/${projectId}/locations/asia-south1/recognizers/_`;

    console.log(`Using Recognizer: ${recognizer}`);

    const config = {
      autoDecodingConfig: {},
      model: "chirp_3",
      languageCodes: ["en-US"],
      features: {
        enableAutomaticPunctuation: true,
        enableWordTimeOffsets: false,
      },
    };

    try {
      const [operation] = await speechClient.batchRecognize({
        recognizer: recognizer,
        config: config,
        files: [{ uri: gcsUri }],
        recognitionOutputConfig: {
          gcsOutputConfig: { uri: outputUri }
        }
      });

      const [response] = await operation.promise();

      const resultPath = response.results?.[gcsUri]?.uri;
      if (!resultPath) throw new HttpsError("internal", "No output file found.");

      // Download JSON
      const resultFileName = resultPath.replace(`gs://${bucketName}/`, "");
      const bucket = admin.storage().bucket(bucketName);
      const file = bucket.file(resultFileName);

      const [content] = await file.download();
      const jsonResult = JSON.parse(content.toString());

      // Extract Text
      const transcription = jsonResult.results
        .map((r: any) => r.alternatives?.[0]?.transcript || "")
        .join(" ");

      try { await file.delete(); } catch (e) { }

      return { text: transcription.trim() };

    } catch (error: any) {
      console.error("Chirp Error:", error);
      throw new HttpsError("internal", "Transcription failed: " + error.message);
    }
  }
);

// --- GEMINI CLEANUP FUNCTION ---
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
    memory: "512MiB",
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
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=${geminiApiKey.value()}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{ parts: [{ text: fullPrompt }] }],
          generationConfig: {
            temperature: 0.2,
            maxOutputTokens: 4096,
            topP: 0.8,
            topK: 10
          },
        }),
      }
    );
    if (!response.ok) throw new HttpsError("internal", "Cleanup failed");

    interface GeminiResponse {
      candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
    }
    const result = (await response.json()) as GeminiResponse;
    return { text: result.candidates?.[0]?.content?.parts?.[0]?.text || text };
  }
);