export interface OcrWordResult {
    word: string;
    lemma?: string;
    confidence: number;
}
export interface OcrResult {
    words: OcrWordResult[];
    rawWords: string[];
}
export type ImageSourceType = 'camera' | 'gallery';
export interface CropArea {
    x: number;
    y: number;
    width: number;
    height: number;
}
export interface CropResult {
    croppedImagePath: string;
}
export interface RecognizeEnglishTextOptions {
    imagePath: string;
    cropArea?: CropArea;
}
export interface CropImageOptions {
    imagePath: string;
    cropArea: CropArea;
}
export interface StartCropUIOptions {
    imagePath: string;
}
export interface PermissionResult {
    granted: boolean;
}
export interface CapacitorPluginOcrPlugin {
    recognizeEnglishText(options: RecognizeEnglishTextOptions): Promise<OcrResult>;
    cropImage(options: CropImageOptions): Promise<CropResult>;
    startCropUI(options: StartCropUIOptions): Promise<CropResult>;
    requestPermissions(): Promise<PermissionResult>;
    checkPermissions(): Promise<PermissionResult>;
}
