import Foundation
import UIKit
import AVFoundation
import Photos

@objc(CapacitorPluginOcr)
class CapacitorPluginOcr: NSObject {
    
    private var pendingCall: CAPPluginCall?
    private var imagePicker: UIImagePickerController?
    
    @objc static func moduleName() -> String {
        return "CapacitorPluginOcr"
    }
    
    @objc static func requires() -> [Any]? {
        return nil
    }
    
    @objc func checkPermissions(_ call: CAPPluginCall) {
        var cameraGranted = false
        var photoLibraryGranted = false
        
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
        cameraGranted = cameraStatus == .authorized
        
        let photoStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        photoLibraryGranted = photoStatus == .authorized
        
        let result: [String: Any] = [
            "granted": cameraGranted && photoLibraryGranted
        ]
        call.resolve(result)
    }
    
    @objc func requestPermissions(_ call: CAPPluginCall) {
        pendingCall = call
        
        // Request camera permission
        AVCaptureDevice.requestAccess(for: .video) { [weak self] cameraGranted in
            // Then request photo library permission
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { photoGranted in
                DispatchQueue.main.async {
                    let result: [String: Any] = [
                        "granted": cameraGranted && photoGranted
                    ]
                    call.resolve(result)
                }
            }
        }
    }
    
    @objc func recognizeEnglishText(_ call: CAPPluginCall) {
        guard let imagePath = call.getString("imagePath") else {
            call.reject("Image path is required")
            return
        }
        
        // Handle different path formats
        var image: UIImage?
        
        if imagePath.hasPrefix("file://") {
            let path = String(imagePath.dropFirst(7))
            image = UIImage(contentsOfFile: path)
        } else if imagePath.hasPrefix("ph://") {
            // Handle Photos framework URLs
            let assetId = String(imagePath.dropFirst(5))
            loadImageFromPhotosLibrary(assetId: assetId, call: call)
            return
        } else if imagePath.hasPrefix("content://") {
            // Handle content URIs
            if let url = URL(string: imagePath), let data = try? Data(contentsOf: url) {
                image = UIImage(data: data)
            }
        } else {
            // Try as file path
            image = UIImage(contentsOfFile: imagePath)
        }
        
        guard let processedImage = image else {
            call.reject("Could not load image from path: \(imagePath)")
            return
        }
        
        performOCR(on: processedImage, call: call)
    }
    
    private func loadImageFromPhotosLibrary(assetId: String, call: CAPPluginCall) {
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [assetId], options: nil)
        
        guard let asset = fetchResult.firstObject else {
            call.reject("Could not find photo with id: \(assetId)")
            return
        }
        
        let options = PHImageRequestOptions()
        options.isSynchronous = false
        options.deliveryMode = .highQualityFormat
        
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: PHImageManagerMaximumSize,
            contentMode: .default,
            options: options
        ) { [weak self] image, _ in
            if let image = image {
                self?.performOCR(on: image, call: call)
            } else {
                call.reject("Could not load image from Photos")
            }
        }
    }
    
    private func performOCR(on image: UIImage, call: CAPPluginCall) {
        // Use Vision framework for OCR (available in iOS 13+)
        guard let cgImage = image.cgImage else {
            call.reject("Could not get CGImage from UIImage")
            return
        }
        
        let request = VNRecognizeTextRequest { [weak self] request, error in
            if let error = error {
                DispatchQueue.main.async {
                    call.reject("OCR failed: \(error.localizedDescription)")
                }
                return
            }
            
            guard let observations = request.results as? [VNRecognizedTextObservation] else {
                DispatchQueue.main.async {
                    call.reject("No text found in image")
                }
                return
            }
            
            let result = self?.processOCRResults(observations)
            DispatchQueue.main.async {
                if let result = result {
                    call.resolve(result)
                } else {
                    call.reject("Failed to process OCR results")
                }
            }
        }
        
        // Configure for English only
        request.recognitionLanguages = ["en-US"]
        request.usesLanguageCorrection = true
        request.recognitionLevel = .accurate
        
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                try handler.perform([request])
            } catch {
                DispatchQueue.main.async {
                    call.reject("Failed to perform OCR: \(error.localizedDescription)")
                }
            }
        }
    }
    
    private func processOCRResults(_ observations: [VNRecognizedTextObservation]) -> [String: Any] {
        var uniqueWords = Set<String>()
        var rawWords: [String] = []
        var resultWords: [[String: Any]] = []
        
        // Pattern for English words only
        let englishPattern = "[a-zA-Z]+"
        guard let regex = try? NSRegularExpression(pattern: englishPattern, options: []) else {
            return ["words": [], "rawWords": []]
        }
        
        for observation in observations {
            guard let topCandidate = observation.topCandidates(1).first else { continue }
            
            let text = topCandidate.string
            let range = NSRange(text.startIndex..<text.endIndex, in: text)
            let matches = regex.matches(in: text, options: [], range: range)
            
            for match in matches {
                if let wordRange = Range(match.range, in: text) {
                    let word = String(text[wordRange]).lowercased()
                    
                    if word.count > 1 {
                        rawWords.append(word)
                        
                        if !uniqueWords.contains(word) {
                            uniqueWords.insert(word)
                            
                            // Get lemma
                            let lemma = lemmatize(word)
                            
                            var wordResult: [String: Any] = [
                                "word": word,
                                "confidence": Double(topCandidate.confidence)
                            ]
                            
                            if lemma != word {
                                wordResult["lemma"] = lemma
                            }
                            
                            resultWords.append(wordResult)
                        }
                    }
                }
            }
        }
        
        return [
            "words": resultWords,
            "rawWords": rawWords
        ]
    }
    
    // MARK: - Crop Methods
    
    @objc func cropImage(_ call: CAPPluginCall) {
        guard let imagePath = call.getString("imagePath"),
              let cropData = call.getObject("cropArea") else {
            call.reject("Image path and crop area are required")
            return
        }
        
        let x = cropData["x"] as? Double ?? 0
        let y = cropData["y"] as? Double ?? 0
        let width = cropData["width"] as? Double ?? 1
        let height = cropData["height"] as? Double ?? 1
        
        var image: UIImage?
        
        if imagePath.hasPrefix("file://") {
            let path = String(imagePath.dropFirst(7))
            image = UIImage(contentsOfFile: path)
        } else {
            image = UIImage(contentsOfFile: imagePath)
        }
        
        guard let originalImage = image, let cgImage = originalImage.cgImage else {
            call.reject("Could not load image")
            return
        }
        
        let imgWidth = CGFloat(cgImage.width)
        let imgHeight = CGFloat(cgImage.height)
        
        let cropX = Int(x * imgWidth)
        let cropY = Int(y * imgHeight)
        let cropW = Int(width * imgWidth)
        let cropH = Int(height * imgHeight)
        
        guard let croppedCGImage = cgImage.cropping(to: CGRect(x: cropX, y: cropY, width: cropW, height: cropH)) else {
            call.reject("Could not crop image")
            return
        }
        
        let croppedImage = UIImage(cgImage: croppedCGImage, scale: originalImage.scale, orientation: originalImage.imageOrientation)
        
        // Save to temp file
        guard let data = croppedImage.jpegData(compressionQuality: 0.9) else {
            call.reject("Could not create image data")
            return
        }
        
        let tempDir = NSTemporaryDirectory()
        let fileName = "cropped_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
        let filePath = (tempDir as NSString).appendingPathComponent(fileName)
        
        do {
            try data.write(to: URL(fileURLWithPath: filePath))
            call.resolve(["croppedImagePath": filePath])
        } catch {
            call.reject("Could not save cropped image: \(error.localizedDescription)")
        }
    }
    
    @objc func startCropUI(_ call: CAPPluginCall) {
        guard let imagePath = call.getString("imagePath") else {
            call.reject("Image path is required")
            return
        }
        
        var image: UIImage?
        
        if imagePath.hasPrefix("file://") {
            let path = String(imagePath.dropFirst(7))
            image = UIImage(contentsOfFile: path)
        } else {
            image = UIImage(contentsOfFile: imagePath)
        }
        
        guard let selectedImage = image else {
            call.reject("Could not load image")
            return
        }
        
        // For iOS, we'll use the built-in crop view controller
        // This requires implementing a custom UIImagePickerController or using a library
        // For now, we'll just reject with a message to implement custom UI
        
        pendingCall = call
        
        // Present image picker with crop enabled
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            if UIImagePickerController.isSourceTypeAvailable(.photoLibrary) {
                let picker = UIImagePickerController()
                picker.sourceType = .photoLibrary
                picker.delegate = self
                picker.allowsEditing = true
                
                if let vc = self.bridge?.viewController {
                    vc.present(picker, animated: true)
                } else {
                    call.reject("Could not present picker")
                }
            } else {
                call.reject("Photo library not available")
            }
        }
    }
    
    // Simple lemmatizer using common rules
    private func lemmatize(_ word: String) -> String {
        let lowerWord = word.lowercased()
        
        // Irregular verbs dictionary
        let irregularVerbs: [String: String] = [
            "was": "be", "were": "be", "been": "be", "being": "be",
            "had": "have", "has": "have",
            "did": "do", "done": "do",
            "went": "go", "gone": "go",
            "came": "come", "came": "come",
            "took": "take", "took": "take",
            "saw": "see", "seen": "see",
            "knew": "know", "known": "know",
            "thought": "think", "thought": "think",
            "said": "say", "said": "say",
            "told": "tell", "told": "tell",
            "got": "get", "got": "get",
            "made": "make", "made": "make",
            "gave": "give", "gave": "give",
            "found": "find", "found": "find",
            "became": "become", "became": "become",
            "left": "leave", "left": "leave",
            "kept": "keep", "kept": "keep",
            "began": "begin", "began": "begin",
            "shown": "show", "shown": "show",
            "heard": "hear", "heard": "hear",
            "played": "play", "played": "play",
            "ran": "run", "ran": "run",
            "moved": "move", "moved": "move",
            "lived": "live", "lived": "live",
            "believed": "believe", "believed": "believe",
            "brought": "bring", "brought": "bring",
            "wrote": "write", "wrote": "write",
            "sat": "sit", "sat": "sit",
            "stood": "stand", "stood": "stand",
            "lost": "lose", "lost": "lose",
            "paid": "pay", "paid": "pay",
            "met": "meet", "met": "meet",
            "built": "build", "built": "build",
            "stayed": "stay", "stayed": "stay",
            "fell": "fall", "fell": "fall",
            "cut": "cut", "cut": "cut",
            "sold": "sell", "sold": "sell",
            "sent": "send", "sent": "send",
            "died": "die", "died": "die"
        ]
        
        // Check irregular verbs
        if let lemma = irregularVerbs[lowerWord] {
            return lemma
        }
        
        let length = lowerWord.count
        guard length > 2 else { return lowerWord }
        
        // Past tense (-ed)
        if lowerWord.hasSuffix("ed") && length > 3 {
            var base = String(lowerWord.dropLast(2))
            
            // Handle -ied -> -y
            if lowerWord.hasSuffix("ied") && length > 3 {
                return String(lowerWord.dropLast(3)) + "y"
            }
            
            // Handle doubled consonant
            if let last = base.last, let secondLast = base.dropLast().last {
                let consonants = "bcdfghjklmnpqrstvwxyz"
                if consonants.contains(last) && last == secondLast {
                    base = String(base.dropLast())
                }
            }
            
            return base
        }
        
        // Progressive/Continuous (-ing)
        if lowerWord.hasSuffix("ing") && length > 4 {
            var base = String(lowerWord.dropLast(3))
            
            // Handle doubled consonant
            if let last = base.last, let secondLast = base.dropLast().last {
                let consonants = "bcdfghjklmnpqrstvwxyz"
                if consonants.contains(last) && last == secondLast {
                    base = String(base.dropLast())
                }
            }
            
            return base
        }
        
        // Plural (-s, -es)
        if lowerWord.hasSuffix("s") || lowerWord.hasSuffix("es") {
            // -ies -> -y
            if lowerWord.hasSuffix("ies") && length > 3 {
                return String(lowerWord.dropLast(3)) + "y"
            }
            
            // -es
            if lowerWord.hasSuffix("es") && length > 3 {
                let base = String(lowerWord.dropLast(2))
                let esEndings = ["sh", "ch", "x", "z", "ss"]
                if esEndings.contains(where: { base.hasSuffix($0) }) {
                    return base
                }
                return String(lowerWord.dropLast(1))
            }
            
            // -s
            if lowerWord.hasSuffix("s") && length > 2 {
                let base = String(lowerWord.dropLast(1))
                if !base.hasSuffix("ss") {
                    return base
                }
            }
        }
        
        return lowerWord
    }
}