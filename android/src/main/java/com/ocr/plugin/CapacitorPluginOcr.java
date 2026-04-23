package com.ocr.plugin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CapacitorPlugin(name = "CapacitorPluginOcr")
public class CapacitorPluginOcr extends Plugin {

    private static final String TAG = "CapacitorPluginOcr";
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    private final Lemmatizer lemmatizer = new Lemmatizer();

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", true);
        call.resolve(result);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", true);
        call.resolve(result);
    }

    @PluginMethod
    public void recognizeEnglishText(PluginCall call) {
        String imagePath = call.getString("imagePath");
        if (imagePath == null || imagePath.trim().isEmpty()) {
            call.reject("Image path is required");
            return;
        }

        JSObject cropArea = call.getObject("cropArea");
        try {
            InputImage image = loadInputImage(imagePath, cropArea);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(text -> call.resolve(processText(text)))
                    .addOnFailureListener(error -> {
                        Log.e(TAG, "OCR failed", error);
                        call.reject("OCR recognition failed: " + error.getMessage(), error);
                    })
                    .addOnCompleteListener(task -> recognizer.close());
        } catch (Exception error) {
            Log.e(TAG, "Failed to load image", error);
            call.reject("Could not load image: " + error.getMessage(), error);
        }
    }

    @PluginMethod
    public void cropImage(PluginCall call) {
        String imagePath = call.getString("imagePath");
        JSObject cropArea = call.getObject("cropArea");

        if (imagePath == null || imagePath.trim().isEmpty()) {
            call.reject("Image path is required");
            return;
        }

        if (cropArea == null) {
            call.reject("Crop area is required");
            return;
        }

        try {
            Bitmap original = decodeBitmap(resolveUri(imagePath));
            File croppedFile = cropBitmapToFile(original, cropArea);
            original.recycle();

            JSObject result = new JSObject();
            result.put("croppedImagePath", croppedFile.getAbsolutePath());
            call.resolve(result);
        } catch (Exception error) {
            Log.e(TAG, "Crop failed", error);
            call.reject("Crop failed: " + error.getMessage(), error);
        }
    }

    @PluginMethod
    public void startCropUI(PluginCall call) {
        call.reject("Interactive crop UI is not implemented. Use cropImage with a cropArea instead.");
    }

    private InputImage loadInputImage(String imagePath, JSObject cropArea) throws IOException {
        Uri uri = resolveUri(imagePath);

        if (cropArea == null) {
            return InputImage.fromFilePath(getContext(), uri);
        }

        Bitmap original = decodeBitmap(uri);
        File croppedFile = cropBitmapToFile(original, cropArea);
        original.recycle();
        return InputImage.fromFilePath(getContext(), Uri.fromFile(croppedFile));
    }

    private Uri resolveUri(String imagePath) {
        String path = imagePath.trim();

        if (path.startsWith("file://") || path.startsWith("content://")) {
            return Uri.parse(path);
        }

        return Uri.fromFile(new File(path));
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        InputStream stream = getContext().getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new IOException("Could not open image stream");
        }

        try (InputStream input = stream) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IOException("Could not decode image");
            }
            return bitmap;
        }
    }

    private File cropBitmapToFile(Bitmap original, JSObject cropArea) throws IOException {
        int imgWidth = original.getWidth();
        int imgHeight = original.getHeight();

        double x = clamp(cropArea.optDouble("x", 0), 0, 1);
        double y = clamp(cropArea.optDouble("y", 0), 0, 1);
        double width = clamp(cropArea.optDouble("width", 1), 0, 1);
        double height = clamp(cropArea.optDouble("height", 1), 0, 1);

        int cropX = Math.min((int) Math.round(x * imgWidth), imgWidth - 1);
        int cropY = Math.min((int) Math.round(y * imgHeight), imgHeight - 1);
        int cropW = Math.max(1, Math.min((int) Math.round(width * imgWidth), imgWidth - cropX));
        int cropH = Math.max(1, Math.min((int) Math.round(height * imgHeight), imgHeight - cropY));

        Bitmap cropped = Bitmap.createBitmap(original, cropX, cropY, cropW, cropH);
        File outputFile = new File(getContext().getCacheDir(), "ocr_crop_" + System.currentTimeMillis() + ".jpg");

        try (FileOutputStream output = new FileOutputStream(outputFile)) {
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, output);
        } finally {
            cropped.recycle();
        }

        return outputFile;
    }

    private JSObject processText(Text text) {
        Set<String> uniqueWords = new HashSet<>();
        JSArray rawWords = new JSArray();
        JSArray words = new JSArray();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Matcher matcher = ENGLISH_WORD_PATTERN.matcher(element.getText());

                    while (matcher.find()) {
                        String word = matcher.group().toLowerCase(Locale.ROOT);
                        if (word.length() <= 1) {
                            continue;
                        }

                        rawWords.put(word);
                        if (uniqueWords.add(word)) {
                            JSObject wordResult = new JSObject();
                            wordResult.put("word", word);
                            wordResult.put("confidence", 1.0);

                            String lemma = lemmatizer.lemmatize(word);
                            if (!lemma.equals(word)) {
                                wordResult.put("lemma", lemma);
                            }

                            words.put(wordResult);
                        }
                    }
                }
            }
        }

        JSObject result = new JSObject();
        result.put("words", words);
        result.put("rawWords", rawWords);
        return result;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Lemmatizer {
        private static final Map<String, String> IRREGULAR_VERBS = new HashMap<>();

        static {
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
            IRREGULAR_VERBS.put("wrote", "write");
            IRREGULAR_VERBS.put("sat", "sit");
            IRREGULAR_VERBS.put("stood", "stand");
            IRREGULAR_VERBS.put("lost", "lose");
            IRREGULAR_VERBS.put("paid", "pay");
            IRREGULAR_VERBS.put("met", "meet");
            IRREGULAR_VERBS.put("built", "build");
            IRREGULAR_VERBS.put("stayed", "stay");
            IRREGULAR_VERBS.put("fell", "fall");
            IRREGULAR_VERBS.put("sold", "sell");
            IRREGULAR_VERBS.put("sent", "send");
            IRREGULAR_VERBS.put("died", "die");
        }

        String lemmatize(String word) {
            String lowerWord = word.toLowerCase(Locale.ROOT);
            int length = lowerWord.length();

            if (IRREGULAR_VERBS.containsKey(lowerWord)) {
                return IRREGULAR_VERBS.get(lowerWord);
            }

            if (length > 3 && lowerWord.endsWith("ied")) {
                return lowerWord.substring(0, length - 3) + "y";
            }

            if (length > 3 && lowerWord.endsWith("ed")) {
                return removeDoubledFinalConsonant(lowerWord.substring(0, length - 2));
            }

            if (length > 4 && lowerWord.endsWith("ing")) {
                return removeDoubledFinalConsonant(lowerWord.substring(0, length - 3));
            }

            if (length > 3 && lowerWord.endsWith("ies")) {
                return lowerWord.substring(0, length - 3) + "y";
            }

            if (length > 3 && lowerWord.endsWith("es")) {
                String base = lowerWord.substring(0, length - 2);
                if (base.endsWith("sh") || base.endsWith("ch") || base.endsWith("x") || base.endsWith("z")
                        || base.endsWith("ss")) {
                    return base;
                }
                return lowerWord.substring(0, length - 1);
            }

            if (length > 2 && lowerWord.endsWith("s")) {
                String base = lowerWord.substring(0, length - 1);
                if (!base.endsWith("s")) {
                    return base;
                }
            }

            return lowerWord;
        }

        private String removeDoubledFinalConsonant(String word) {
            int length = word.length();
            if (length > 2) {
                char last = word.charAt(length - 1);
                char previous = word.charAt(length - 2);
                if (last == previous && isConsonant(last)) {
                    return word.substring(0, length - 1);
                }
            }
            return word;
        }

        private boolean isConsonant(char value) {
            return "bcdfghjklmnpqrstvwxyz".indexOf(value) >= 0;
        }
    }
}
