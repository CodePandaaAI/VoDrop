import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { v2 } from "@google-cloud/speech";
import * as path from "path";
import * as os from "os";
import * as fs from "fs";

admin.initializeApp();

const geminiApiKey = defineSecret("GEMINI_API_KEY");

// 1. Unified Location: US Multi-Region
// This matches your manual setup: Recognizer in "us", Bucket in "us".
const REGION = "us";
const RECOGNIZER_ID = "vodrop-chirp";

// 2. Specialized Client for US Multi-Region
const speechClient = new v2.SpeechClient({
  apiEndpoint: `${REGION}-speech.googleapis.com`, // us-speech.googleapis.com
});

export const transcribeChirp = onCall(
  {
    timeoutSeconds: 540,
    memory: "512MiB",
    maxInstances: 10,
  },
  async (request) => {
    // DEBUG LOG
    console.log(`[Start] Processing in ${REGION} (Multi-Region)`);

    if (!request.auth) throw new HttpsError("unauthenticated", "Must be logged in");

    const gcsUri = request.data.gcsUri as string;
    if (!gcsUri) throw new HttpsError("invalid-argument", "Missing gcsUri");

    const projectId = process.env.GCLOUD_PROJECT || "vodrop-app";

    // Extract bucket name from Input URI or fallback to the new US bucket
    const matches = gcsUri.match(/gs:\/\/([^\/]+)\//);
    const bucketName = matches ? matches[1] : "vodrop-audio-buc";

    // Define Recognizer Path (Strictly US Multi-Region)
    const recognizer = `projects/${projectId}/locations/${REGION}/recognizers/${RECOGNIZER_ID}`;

    // Output Path (Same bucket, transcripts folder)
    // NOTE: This is the request path, but the API might create a subdirectory.
    const outputFileName = `${path.basename(gcsUri)}.json`;
    const outputUri = `gs://${bucketName}/transcripts/${outputFileName}`;

    console.log(`Using Recognizer: ${recognizer}`);

    // Configuration for the Request
    const recognitionRequest = {
      recognizer: recognizer,
      config: {
        autoDecodingConfig: {},
        model: "chirp_3", // User confirmed chirp_3 is created in 'us'
        languageCodes: ["en-US"],
        features: {
          enableAutomaticPunctuation: true,
          enableWordTimeOffsets: false,
        },
      },
      files: [{ uri: gcsUri }],
      recognitionOutputConfig: {
        gcsOutputConfig: { uri: outputUri }
      }
    };

    let tempLocalFile = null;

    try {
      // 3. Execute Transcription
      const [operation] = await speechClient.batchRecognize(recognitionRequest);
      const [response] = await operation.promise(); // Wait for completion

      // DEBUG: Log the full operation response
      console.log(`[Operation Result]`, JSON.stringify(response, null, 2));

      // 3.1 Check for Errors in the Response
      // The response structure is: { results: { "gs://server/path": { uri: "...", error: { message: "..." } } } }
      const fileResult = response.results?.[gcsUri];

      if (fileResult?.error) {
        console.error(`[Error] Cloud Speech API Error for file: ${fileResult.error.message}`);
        throw new Error(`Cloud Speech API Error: ${fileResult.error.message}`);
      }

      // CRITICAL FIX: The API might create a subdirectory or use a different filename.
      // We must use the 'uri' returned in 'cloudStorageResult'.
      // Log Example: ".../transcripts/file.wav.json/file_transcript_hash.json"
      const actualOutputUri = fileResult?.cloudStorageResult?.uri;

      if (!actualOutputUri) {
        throw new Error("Transcription finished but no output URI returned.");
      }

      console.log(`[Info] Actual Output URI: ${actualOutputUri}`);

      // Parse the bucket and file path from the actual URI
      // Format: gs://bucket-name/path/to/file.json
      const uriMatches = actualOutputUri.match(/gs:\/\/([^\/]+)\/(.+)/);
      if (!uriMatches) {
        throw new Error(`Could not parse output URI: ${actualOutputUri}`);
      }

      // const resultBucketName = uriMatches[1]; // Should match bucketName
      const resultFilePath = uriMatches[2];

      // 4. Download Result
      const bucket = admin.storage().bucket(bucketName);

      // Retry loop for file consistency (up to 3 seconds)
      let exists = false;
      for (let i = 0; i < 3; i++) {
        [exists] = await bucket.file(resultFilePath).exists();
        if (exists) break;
        console.log(`[Info] File not found yet, retrying... (${i + 1}/3)`);
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }

      if (!exists) {
        console.error(`[Error] Output file does not exist at: gs://${bucketName}/${resultFilePath}`);
        throw new Error(`Output file missing. Check logs for [Operation Result] to see why. Path: ${resultFilePath}`);
      }

      tempLocalFile = path.join(os.tmpdir(), "result.json");
      await bucket.file(resultFilePath).download({ destination: tempLocalFile });

      const fileContent = fs.readFileSync(tempLocalFile, "utf8");
      const jsonResult = JSON.parse(fileContent);

      // 5. Cleanup the Result File from Cloud Storage
      // Clean up the actual file found
      await bucket.file(resultFilePath).delete().catch(() => { });

      // Optional: Try to clean up the parent directory if it was created by the API
      // We know `outputFileName` was the directory name we asked for
      // const parentDir = `transcripts/${outputFileName}`;
      // await bucket.file(parentDir).delete().catch(() => {});

      // 6. Extract Text
      const transcription = (jsonResult.results || [])
        .flatMap((r: any) => (r.alternatives || []).map((a: any) => a.transcript))
        .join(" ");

      return { text: transcription || "(No speech detected)" };

    } catch (error: any) {
      console.error("[Error] Transcription failed:", error);
      throw new HttpsError("internal", `Transcription failed: ${error.message}`);
    } finally {
      // 7. Local Cleanup
      if (tempLocalFile && fs.existsSync(tempLocalFile)) {
        fs.unlinkSync(tempLocalFile);
      }
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