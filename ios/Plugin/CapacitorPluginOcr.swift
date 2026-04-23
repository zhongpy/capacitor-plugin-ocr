import Capacitor
import Foundation
import ImageIO
import Photos
import UIKit
import Vision

@objc(CapacitorPluginOcr)
public class CapacitorPluginOcr: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapacitorPluginOcr"
    public let jsName = "CapacitorPluginOcr"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(#selector(checkPermissions(_:)), returnType: .promise),
        CAPPluginMethod(#selector(requestPermissions(_:)), returnType: .promise),
        CAPPluginMethod(#selector(recognizeEnglishText(_:)), returnType: .promise),
        CAPPluginMethod(#selector(cropImage(_:)), returnType: .promise),
        CAPPluginMethod(#selector(startCropUI(_:)), returnType: .promise)
    ]

    @objc public override func checkPermissions(_ call: CAPPluginCall) {
        call.resolve(["granted": true])
    }

    @objc public override func requestPermissions(_ call: CAPPluginCall) {
        call.resolve(["granted": true])
    }

    @objc public func recognizeEnglishText(_ call: CAPPluginCall) {
        guard let imagePath = call.getString("imagePath"), !imagePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            call.reject("Image path is required")
            return
        }

        let cropArea = call.getObject("cropArea")
        loadImage(from: imagePath) { [weak self] image, errorMessage in
            guard let self = self else { return }

            if let errorMessage = errorMessage {
                call.reject(errorMessage)
                return
            }

            guard var workingImage = image else {
                call.reject("Could not load image")
                return
            }

            if let cropArea = cropArea {
                guard let croppedImage = self.crop(image: workingImage, cropArea: cropArea) else {
                    call.reject("Could not crop image")
                    return
                }
                workingImage = croppedImage
            }

            self.performOCR(on: workingImage, call: call)
        }
    }

    @objc public func cropImage(_ call: CAPPluginCall) {
        guard let imagePath = call.getString("imagePath"), !imagePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            call.reject("Image path is required")
            return
        }

        guard let cropArea = call.getObject("cropArea") else {
            call.reject("Crop area is required")
            return
        }

        loadImage(from: imagePath) { [weak self] image, errorMessage in
            guard let self = self else { return }

            if let errorMessage = errorMessage {
                call.reject(errorMessage)
                return
            }

            guard let image = image, let croppedImage = self.crop(image: image, cropArea: cropArea) else {
                call.reject("Could not crop image")
                return
            }

            do {
                let filePath = try self.writeTemporaryJpeg(croppedImage)
                call.resolve(["croppedImagePath": filePath])
            } catch {
                call.reject("Could not save cropped image: \(error.localizedDescription)")
            }
        }
    }

    @objc public func startCropUI(_ call: CAPPluginCall) {
        call.reject("Interactive crop UI is not implemented. Use cropImage with a cropArea instead.")
    }

    private func loadImage(from imagePath: String, completion: @escaping (UIImage?, String?) -> Void) {
        let trimmedPath = imagePath.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmedPath.hasPrefix("ph://") {
            loadPhotoAsset(identifier: String(trimmedPath.dropFirst(5)), completion: completion)
            return
        }

        if trimmedPath.hasPrefix("file://"), let url = URL(string: trimmedPath) {
            completion(UIImage(contentsOfFile: url.path), nil)
            return
        }

        if let url = URL(string: trimmedPath), url.scheme != nil, url.scheme != "file" {
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    let data = try Data(contentsOf: url)
                    let image = UIImage(data: data)
                    DispatchQueue.main.async {
                        completion(image, image == nil ? "Could not decode image data" : nil)
                    }
                } catch {
                    DispatchQueue.main.async {
                        completion(nil, "Could not load image: \(error.localizedDescription)")
                    }
                }
            }
            return
        }

        completion(UIImage(contentsOfFile: trimmedPath), nil)
    }

    private func loadPhotoAsset(identifier: String, completion: @escaping (UIImage?, String?) -> Void) {
        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
        guard let asset = fetchResult.firstObject else {
            completion(nil, "Could not find photo asset")
            return
        }

        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.isNetworkAccessAllowed = true

        PHImageManager.default().requestImage(
            for: asset,
            targetSize: PHImageManagerMaximumSize,
            contentMode: .default,
            options: options
        ) { image, _ in
            DispatchQueue.main.async {
                completion(image, image == nil ? "Could not load photo asset" : nil)
            }
        }
    }

    private func performOCR(on image: UIImage, call: CAPPluginCall) {
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

            let observations = request.results as? [VNRecognizedTextObservation] ?? []
            let result = self?.processOCRResults(observations) ?? ["words": [], "rawWords": []]
            DispatchQueue.main.async {
                call.resolve(result)
            }
        }

        request.recognitionLanguages = ["en-US"]
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true

        let handler = VNImageRequestHandler(
            cgImage: cgImage,
            orientation: CGImagePropertyOrientation(image.imageOrientation),
            options: [:]
        )

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
        let regex = try? NSRegularExpression(pattern: "[a-zA-Z]+", options: [])
        var uniqueWords = Set<String>()
        var rawWords: [String] = []
        var resultWords: [[String: Any]] = []

        for observation in observations {
            guard let candidate = observation.topCandidates(1).first else { continue }
            let text = candidate.string
            let range = NSRange(text.startIndex..<text.endIndex, in: text)

            regex?.matches(in: text, options: [], range: range).forEach { match in
                guard let wordRange = Range(match.range, in: text) else { return }

                let word = String(text[wordRange]).lowercased()
                if word.count <= 1 {
                    return
                }

                rawWords.append(word)
                if uniqueWords.insert(word).inserted {
                    let lemma = lemmatize(word)
                    var wordResult: [String: Any] = [
                        "word": word,
                        "confidence": Double(candidate.confidence)
                    ]

                    if lemma != word {
                        wordResult["lemma"] = lemma
                    }

                    resultWords.append(wordResult)
                }
            }
        }

        return [
            "words": resultWords,
            "rawWords": rawWords
        ]
    }

    private func crop(image: UIImage, cropArea: JSObject) -> UIImage? {
        guard let cgImage = image.cgImage else {
            return nil
        }

        let imageWidth = CGFloat(cgImage.width)
        let imageHeight = CGFloat(cgImage.height)
        let x = CGFloat(clamp(doubleValue(cropArea["x"], defaultValue: 0), min: 0, max: 1))
        let y = CGFloat(clamp(doubleValue(cropArea["y"], defaultValue: 0), min: 0, max: 1))
        let width = CGFloat(clamp(doubleValue(cropArea["width"], defaultValue: 1), min: 0, max: 1))
        let height = CGFloat(clamp(doubleValue(cropArea["height"], defaultValue: 1), min: 0, max: 1))

        let cropX = min(x * imageWidth, imageWidth - 1)
        let cropY = min(y * imageHeight, imageHeight - 1)
        let cropWidth = max(1, min(width * imageWidth, imageWidth - cropX))
        let cropHeight = max(1, min(height * imageHeight, imageHeight - cropY))
        let cropRect = CGRect(x: cropX, y: cropY, width: cropWidth, height: cropHeight)

        guard let croppedCGImage = cgImage.cropping(to: cropRect) else {
            return nil
        }

        return UIImage(cgImage: croppedCGImage, scale: image.scale, orientation: image.imageOrientation)
    }

    private func writeTemporaryJpeg(_ image: UIImage) throws -> String {
        guard let data = image.jpegData(compressionQuality: 0.92) else {
            throw OcrPluginError.imageEncodingFailed
        }

        let fileURL = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("ocr_crop_\(Int(Date().timeIntervalSince1970 * 1000)).jpg")
        try data.write(to: fileURL, options: .atomic)
        return fileURL.path
    }

    private func doubleValue(_ value: Any?, defaultValue: Double) -> Double {
        if let double = value as? Double {
            return double
        }

        if let number = value as? NSNumber {
            return number.doubleValue
        }

        return defaultValue
    }

    private func clamp(_ value: Double, min: Double, max: Double) -> Double {
        Swift.max(min, Swift.min(max, value))
    }

    private func lemmatize(_ word: String) -> String {
        let lowerWord = word.lowercased()
        let irregularVerbs: [String: String] = [
            "was": "be", "were": "be", "been": "be", "being": "be",
            "had": "have", "has": "have",
            "did": "do", "done": "do",
            "went": "go", "gone": "go",
            "came": "come",
            "took": "take",
            "saw": "see", "seen": "see",
            "knew": "know", "known": "know",
            "thought": "think",
            "said": "say",
            "told": "tell",
            "got": "get",
            "made": "make",
            "gave": "give",
            "found": "find",
            "became": "become",
            "left": "leave",
            "kept": "keep",
            "began": "begin",
            "shown": "show",
            "heard": "hear",
            "played": "play",
            "ran": "run",
            "moved": "move",
            "lived": "live",
            "believed": "believe",
            "brought": "bring",
            "wrote": "write",
            "sat": "sit",
            "stood": "stand",
            "lost": "lose",
            "paid": "pay",
            "met": "meet",
            "built": "build",
            "stayed": "stay",
            "fell": "fall",
            "sold": "sell",
            "sent": "send",
            "died": "die"
        ]

        if let lemma = irregularVerbs[lowerWord] {
            return lemma
        }

        if lowerWord.count > 3 && lowerWord.hasSuffix("ied") {
            return String(lowerWord.dropLast(3)) + "y"
        }

        if lowerWord.count > 3 && lowerWord.hasSuffix("ed") {
            return removeDoubledFinalConsonant(String(lowerWord.dropLast(2)))
        }

        if lowerWord.count > 4 && lowerWord.hasSuffix("ing") {
            return removeDoubledFinalConsonant(String(lowerWord.dropLast(3)))
        }

        if lowerWord.count > 3 && lowerWord.hasSuffix("ies") {
            return String(lowerWord.dropLast(3)) + "y"
        }

        if lowerWord.count > 3 && lowerWord.hasSuffix("es") {
            let base = String(lowerWord.dropLast(2))
            if ["sh", "ch", "x", "z", "ss"].contains(where: { base.hasSuffix($0) }) {
                return base
            }
            return String(lowerWord.dropLast(1))
        }

        if lowerWord.count > 2 && lowerWord.hasSuffix("s") {
            let base = String(lowerWord.dropLast(1))
            if !base.hasSuffix("s") {
                return base
            }
        }

        return lowerWord
    }

    private func removeDoubledFinalConsonant(_ word: String) -> String {
        guard word.count > 2, let last = word.last, let previous = word.dropLast().last else {
            return word
        }

        if last == previous && "bcdfghjklmnpqrstvwxyz".contains(last) {
            return String(word.dropLast())
        }

        return word
    }
}

private enum OcrPluginError: Error {
    case imageEncodingFailed
}

private extension CGImagePropertyOrientation {
    init(_ orientation: UIImage.Orientation) {
        switch orientation {
        case .up:
            self = .up
        case .upMirrored:
            self = .upMirrored
        case .down:
            self = .down
        case .downMirrored:
            self = .downMirrored
        case .left:
            self = .left
        case .leftMirrored:
            self = .leftMirrored
        case .right:
            self = .right
        case .rightMirrored:
            self = .rightMirrored
        @unknown default:
            self = .up
        }
    }
}
