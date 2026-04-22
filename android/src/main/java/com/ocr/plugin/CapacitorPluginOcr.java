package com.ocr.plugin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Capacitor OCR Plugin for Android
 * Uses Tesseract OCR for text recognition
 * Uses WordNet for lemmatization
 */
@CapacitorPlugin(name = "CapacitorPluginOcr", permissions = {
        @Permission(alias = "camera", strings = {
                Manifest.permission.CAMERA
        }),
        @Permission(alias = "storage", strings = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }),
        @Permission(alias = "photos", strings = {
                Manifest.permission.READ_MEDIA_IMAGES
        })
})
public class CapacitorPluginOcr extends Plugin {

    private static final String TAG = "CapacitorPluginOcr";
    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_IMAGE_PICK = 1002;

    // English word pattern (only letters)
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    // Tesseract instance
    private Tesseract tesseract;

    // Lemmatization cache
    private Lemmatizer lemmatizer;

    // Pending plugin call
    private PluginCall pendingCall;

    // Temp image file
    private File tempImageFile;

    @Override
    public void load() {
        super.load();
        initTesseract();
        lemmatizer = new Lemmatizer();
    }

    /**
     * Initialize Tesseract OCR engine
     */
    private void initTesseract() {
        try {
            tesseract = new Tesseract();
            // Set OCR language to English only
            tesseract.setLanguage("eng");
            // Set datapath - for Android, assets are extracted to filesDir
            String datapath = getContext().getFilesDir().getAbsolutePath() + "/tessdata";
            tesseract.setDatapath(datapath);
            // Set page segmentation mode
            tesseract.setPageSegMode(3); // PSM_AUTO
            // Set whitelist - only allow English letters
            tesseract.setVariable("tessedit_char_whitelist", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
            Log.d(TAG, "Tesseract initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Tesseract: " + e.getMessage());
        }
    }

    /**
     * Check and request permissions
     */
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        boolean cameraGranted = hasPermission(Manifest.permission.CAMERA);
        boolean storageGranted;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storageGranted = hasPermission(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            storageGranted = hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        boolean granted = cameraGranted && storageGranted;
        JSObject result = new JSObject();
        result.put("granted", granted);
        call.resolve(result);
    }

    /**
     * Request permissions
     */
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        // Request camera permission
        requestPermissionForAlias("camera", call, "permissionCamera");
    }

    /**
     * Called when camera permission result is received
     */
    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);

        if (pendingCall != null) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            JSObject result = new JSObject();
            result.put("granted", allGranted);
            pendingCall.resolve(result);
            pendingCall = null;
        }
    }

    // Crop image helper method
    private File cropImage(Bitmap original, double x, double y, double width, double height) throws IOException {
        int imgWidth = original.getWidth();
        int imgHeight = original.getHeight();

        int cropX = (int) (x * imgWidth);
        int cropY = (int) (y * imgHeight);
        int cropW = (int) (width * imgWidth);
        int cropH = (int) (height * imgHeight);

        // Ensure crop area is within bounds
        cropX = Math.max(0, Math.min(cropX, imgWidth - 1));
        cropY = Math.max(0, Math.min(cropY, imgHeight - 1));
        cropW = Math.max(1, Math.min(cropW, imgWidth - cropX));
        cropH = Math.max(1, Math.min(cropH, imgHeight - cropY));

        Bitmap cropped = Bitmap.createBitmap(original, cropX, cropY, cropW, cropH);

        File outputFile = new File(getContext().getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");
        FileOutputStream out = new FileOutputStream(outputFile);
        cropped.compress(Bitmap.CompressFormat.JPEG, 90, out);
        out.flush();
        out.close();

        return outputFile;
    }

    /**
     * Recognize English text from image with optional crop
     */
    @PluginMethod
    public void recognizeEnglishText(PluginCall call) {
        String imagePath = call.getString("imagePath");

        if (imagePath == null || imagePath.isEmpty()) {
            call.reject("Image path is required");
            return;
        }

        // Handle different path formats
        if (imagePath.startsWith("file://")) {
            imagePath = imagePath.substring(7);
        } else if (imagePath.startsWith("content://")) {
            try {
                Uri uri = Uri.parse(imagePath);
                imagePath = getPathFromUri(uri);
            } catch (Exception e) {
                call.reject("Invalid image path: " + e.getMessage());
                return;
            }
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            call.reject("Image file not found");
            return;
        }

        try {
            // Check if crop area is specified
            JSONObject cropJson = call.getObject("cropArea");
            File workingFile = imageFile;

            if (cropJson != null) {
                double x = cropJson.optDouble("x", 0);
                double y = cropJson.optDouble("y", 0);
                double width = cropJson.optDouble("width", 1);
                double height = cropJson.optDouble("height", 1);

                Bitmap original = BitmapFactory.decodeFile(imagePath);
                if (original != null) {
                    workingFile = cropImage(original, x, y, width, height);
                    original.recycle();
                }
            }

            // Perform OCR
            List<Word> words = tesseract.getWords(workingFile, 3);

            // Process results
            Set<String> uniqueWords = new HashSet<>();
            JSONArray rawWordsArray = new JSONArray();
            JSONArray resultWordsArray = new JSONArray();

            for (Word word : words) {
                String text = word.getText().trim();

                // Extract only English words
                Matcher matcher = ENGLISH_WORD_PATTERN.matcher(text);
                while (matcher.find()) {
                    String englishWord = matcher.group().toLowerCase(Locale.ROOT);

                    if (!englishWord.isEmpty() && englishWord.length() > 1) {
                        // Add to raw words
                        rawWordsArray.put(englishWord);

                        // Check if already processed
                        if (!uniqueWords.contains(englishWord)) {
                            uniqueWords.add(englishWord);

                            // Get lemma (base form)
                            String lemma = lemmatizer.lemmatize(englishWord);

                            // Create result object
                            JSONObject wordObj = new JSONObject();
                            wordObj.put("word", englishWord);
                            wordObj.put("confidence", word.getConfidence());

                            // Only add lemma if different from word
                            if (!lemma.equals(englishWord)) {
                                wordObj.put("lemma", lemma);
                            }

                            resultWordsArray.put(wordObj);
                        }
                    }
                }
            }

            JSObject result = new JSObject();
            result.put("words", resultWordsArray);
            result.put("rawWords", rawWordsArray);
            call.resolve(result);

        } catch (TesseractException e) {
            Log.e(TAG, "OCR Error: " + e.getMessage());
            call.reject("OCR recognition failed: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG, "JSON Error: " + e.getMessage());
            call.reject("Failed to create result: " + e.getMessage());
        }
    }

    /**
     * Crop image method
     */
    @PluginMethod
    public void cropImage(PluginCall call) {
        String imagePath = call.getString("imagePath");
        JSONObject cropJson = call.getObject("cropArea");

        if (imagePath == null || imagePath.isEmpty()) {
            call.reject("Image path is required");
            return;
        }

        if (cropJson == null) {
            call.reject("Crop area is required");
            return;
        }

        // Handle different path formats
        if (imagePath.startsWith("file://")) {
            imagePath = imagePath.substring(7);
        }

        try {
            double x = cropJson.optDouble("x", 0);
            double y = cropJson.optDouble("y", 0);
            double width = cropJson.optDouble("width", 1);
            double height = cropJson.optDouble("height", 1);

            Bitmap original = BitmapFactory.decodeFile(imagePath);
            if (original == null) {
                call.reject("Could not decode image");
                return;
            }

            File croppedFile = cropImage(original, x, y, width, height);
            original.recycle();

            JSObject result = new JSObject();
            result.put("croppedImagePath", croppedFile.getAbsolutePath());
            call.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "Crop error: " + e.getMessage());
            call.reject("Crop failed: " + e.getMessage());
        }
    }

    /**
     * Start interactive crop UI - launches Android crop intent
     */
    @PluginMethod
    public void startCropUI(PluginCall call) {
        String imagePath = call.getString("imagePath");

        if (imagePath == null || imagePath.isEmpty()) {
            call.reject("Image path is required");
            return;
        }

        // Handle different path formats
        if (imagePath.startsWith("file://")) {
            imagePath = imagePath.substring(7);
        }

        try {
            File sourceFile = new File(imagePath);
            Uri sourceUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    sourceFile);

            // Create temp output file
            File outputFile = new File(getContext().getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");
            Uri outputUri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    outputFile);

            // Launch system crop intent
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(sourceUri, "image/*");
            cropIntent.putExtra("crop", "true");
            cropIntent.putExtra("aspectX", 0);
            cropIntent.putExtra("aspectY", 0);
            cropIntent.putExtra("output", outputUri);
            cropIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
            cropIntent.putExtra("return-data", false);
            cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cropIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Start activity and wait for result
            pendingCall = call;
            startActivityForResult(call, cropIntent, "cropResult");

        } catch (Exception e) {
            Log.e(TAG, "Crop UI error: " + e.getMessage());
            call.reject("Failed to start crop: " + e.getMessage());
        }
    }

    /**
     * Handle crop result
     */
    @ActivityCallback(name = "cropResult")
    public void cropResult(PluginCall call, Intent intent) {
        if (pendingCall == null)
            return;

        try {
            File croppedFile = new File(getContext().getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");

            JSObject result = new JSObject();
            result.put("croppedImagePath", croppedFile.getAbsolutePath());
            pendingCall.resolve(result);
        } catch (Exception e) {
            pendingCall.reject("Crop failed: " + e.getMessage());
        }
        pendingCall = null;
    }

    /**
     * Get file path from content URI
     */
    private String getPathFromUri(Uri uri) {
        String path = null;

        if (uri == null)
            return null;

        // Try to get file path directly
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // For content URI, try to get actual file path
        try {
            android.database.Cursor cursor = getContext().getContentResolver()
                    .query(uri, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex("_data");
                if (index >= 0) {
                    path = cursor.getString(index);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting path from URI: " + e.getMessage());
        }

        return path;
    }

    /**
     * Simple lemmatizer using basic rules
     * For production, consider using WordNet JWI library
     */
    private static class Lemmatizer {
        // Common irregular verb mappings
        private static final java.util.Map<String, String> IRREGULAR_VERBS = new java.util.HashMap<>();

        static {
            // Irregular verbs
            IRREGULAR_VERBS.put("was", "be");
            IRREGULAR_VERBS.put("were", "be");
            IRREGULAR_VERBS.put("been", "be");
            IRREGULAR_VERBS.put("being", "be");
            IRREGULAR_VERBS.put("had", "have");
            IRREGULAR_VERBS.put("has", "have");
            IRREGULAR_VERBS.put("did", "do");
            IRREGULAR_VERBS.put("done", "do");
            IRREGULAR_VERBS.put("went", "go");
            IRREGULAR_VERBS.put("gone", "go");
            IRREGULAR_VERBS.put("came", "come");
            IRREGULAR_VERBS.put("took", "take");
            IRREGULAR_VERBS.put("saw", "see");
            IRREGULAR_VERBS.put("seen", "see");
            IRREGULAR_VERBS.put("knew", "know");
            IRREGULAR_VERBS.put("known", "know");
            IRREGULAR_VERBS.put("thought", "think");
            IRREGULAR_VERBS.put("said", "say");
            IRREGULAR_VERBS.put("told", "tell");
            IRREGULAR_VERBS.put("got", "get");
            IRREGULAR_VERBS.put("made", "make");
            IRREGULAR_VERBS.put("gave", "give");
            IRREGULAR_VERBS.put("found", "find");
            IRREGULAR_VERBS.put("became", "become");
            IRREGULAR_VERBS.put("left", "leave");
            IRREGULAR_VERBS.put("kept", "keep");
            IRREGULAR_VERBS.put("began", "begin");
            IRREGULAR_VERBS.put("shown", "show");
            IRREGULAR_VERBS.put("heard", "hear");
            IRREGULAR_VERBS.put("played", "play");
            IRREGULAR_VERBS.put("ran", "run");
            IRREGULAR_VERBS.put("moved", "move");
            IRREGULAR_VERBS.put("lived", "live");
            IRREGULAR_VERBS.put("believed", "believe");
            IRREGULAR_VERBS.put("brought", "bring");
            IRREGULAR_VERBS.put("happened", "happen");
            IRREGULAR_VERBS.put("wrote", "write");
            IRREGULAR_VERBS.put("provided", "provide");
            IRREGULAR_VERBS.put("sat", "sit");
            IRREGULAR_VERBS.put("stood", "stand");
            IRREGULAR_VERBS.put("lost", "lose");
            IRREGULAR_VERBS.put("paid", "pay");
            IRREGULAR_VERBS.put("met", "meet");
            IRREGULAR_VERBS.put("included", "include");
            IRREGULAR_VERBS.put("continued", "continue");
            IRREGULAR_VERBS.put("set", "set");
            IRREGULAR_VERBS.put("learned", "learn");
            IRREGULAR_VERBS.put("changed", "change");
            IRREGULAR_VERBS.put("led", "lead");
            IRREGULAR_VERBS.put("understood", "understand");
            IRREGULAR_VERBS.put("watched", "watch");
            IRREGULAR_VERBS.put("followed", "follow");
            IRREGULAR_VERBS.put("stopped", "stop");
            IRREGULAR_VERBS.put("created", "create");
            IRREGULAR_VERBS.put("spoke", "speak");
            IRREGULAR_VERBS.put("read", "read");
            IRREGULAR_VERBS.put("allowed", "allow");
            IRREGULAR_VERBS.put("added", "add");
            IRREGULAR_VERBS.put("spent", "spend");
            IRREGULAR_VERBS.put("grew", "grow");
            IRREGULAR_VERBS.put("opened", "open");
            IRREGULAR_VERBS.put("walked", "walk");
            IRREGULAR_VERBS.put("won", "win");
            IRREGULAR_VERBS.put("offered", "offer");
            IRREGULAR_VERBS.put("remembered", "remember");
            IRREGULAR_VERBS.put("considered", "consider");
            IRREGULAR_VERBS.put("appeared", "appear");
            IRREGULAR_VERBS.put("bought", "buy");
            IRREGULAR_VERBS.put("waited", "wait");
            IRREGULAR_VERBS.put("served", "serve");
            IRREGULAR_VERBS.put("died", "die");
            IRREGULAR_VERBS.put("sent", "send");
            IRREGULAR_VERBS.put("expected", "expect");
            IRREGULAR_VERBS.put("built", "build");
            IRREGULAR_VERBS.put("stayed", "stay");
            IRREGULAR_VERBS.put("fell", "fall");
            IRREGULAR_VERBS.put("cut", "cut");
            IRREGULAR_VERBS.put("reached", "reach");
            IRREGULAR_VERBS.put("killed", "kill");
            IRREGULAR_VERBS.put("remained", "remain");
            IRREGULAR_VERBS.put("suggested", "suggest");
            IRREGULAR_VERBS.put("raised", "raise");
            IRREGULAR_VERBS.put("passed", "pass");
            IRREGULAR_VERBS.put("sold", "sell");
            IRREGULAR_VERBS.put("required", "require");
            IRREGULAR_VERBS.put("reported", "report");
            IRREGULAR_VERBS.put("decided", "decide");
            IRREGULAR_VERBS.put("pulled", "pull");
        }

        public String lemmatize(String word) {
            if (word == null || word.isEmpty()) {
                return word;
            }

            String lowerWord = word.toLowerCase(Locale.ROOT);
            int len = lowerWord.length();

            // Check irregular verbs first
            if (IRREGULAR_VERBS.containsKey(lowerWord)) {
                return IRREGULAR_VERBS.get(lowerWord);
            }

            // Handle common suffixes
            // Past tense (-ed)
            if (len > 3 && lowerWord.endsWith("ed")) {
                String base = lowerWord.substring(0, len - 2);
                // Don't double the final consonant
                if (base.length() > 2 && isConsonant(base.charAt(base.length() - 1))) {
                    return base;
                }
                // Handle -ied -> -y
                if (len > 3 && lowerWord.endsWith("ied")) {
                    return lowerWord.substring(0, len - 3) + "y";
                }
                return base;
            }

            // Progressive/Continuous (-ing)
            if (len > 4 && lowerWord.endsWith("ing")) {
                String base = lowerWord.substring(0, len - 3);
                // Handle doubled consonant
                if (base.length() > 2 &&
                        isConsonant(base.charAt(base.length() - 1)) &&
                        base.charAt(base.length() - 1) == base.charAt(base.length() - 2)) {
                    return base.substring(0, base.length() - 1);
                }
                return base;
            }

            // Plural (-s, -es)
            if (len > 2 && (lowerWord.endsWith("s") || lowerWord.endsWith("es") || lowerWord.endsWith("ied"))) {
                // -ies -> -y
                if (lowerWord.endsWith("ies") && len > 3) {
                    return lowerWord.substring(0, len - 3) + "y";
                }
                // -es
                if (lowerWord.endsWith("es") && len > 3) {
                    String base = lowerWord.substring(0, len - 2);
                    // Handle -shes, -ches, -xes, -zes, -ces
                    if (base.endsWith("sh") || base.endsWith("ch") ||
                            base.endsWith("x") || base.endsWith("z") || base.endsWith("ce") || base.endsWith("se")) {
                        return base;
                    }
                    return lowerWord.substring(0, len - 1);
                }
                // -s
                if (lowerWord.endsWith("s") && len > 2) {
                    String base = lowerWord.substring(0, len - 1);
                    // Don't remove s after certain letters
                    if (!base.endsWith("ss")) {
                        return base;
                    }
                }
            }

            // Comparative (-er)
            if (len > 3 && lowerWord.endsWith("er")) {
                String base = lowerWord.substring(0, len - 2);
                if (base.length() > 2 && isConsonant(base.charAt(base.length() - 1))) {
                    return base;
                }
                return base;
            }

            // Superlative (-est)
            if (len > 4 && lowerWord.endsWith("est")) {
                String base = lowerWord.substring(0, len - 3);
                if (base.length() > 2 && isConsonant(base.charAt(base.length() - 1))) {
                    return base;
                }
                return base;
            }

            // Adverb formation (-ly)
            if (len > 3 && lowerWord.endsWith("ly")) {
                String base = lowerWord.substring(0, len - 2);
                // Return base without -ly for adjectives
                return base;
            }

            return lowerWord;
        }

        private boolean isConsonant(char c) {
            return !isVowel(c);
        }

        private boolean isVowel(char c) {
            return "aeiou".indexOf(c) >= 0;
        }
    }
}