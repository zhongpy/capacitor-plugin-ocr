import type { App } from 'vue';
import type { CropArea, OcrResult } from './definitions';
export declare function requestOcrPermissions(): Promise<boolean>;
export declare function checkOcrPermissions(): Promise<boolean>;
export declare function recognizeEnglishFromImage(imagePath: string, cropArea?: CropArea): Promise<OcrResult>;
export declare function useOcr(): {
    isProcessing: Readonly<import("vue").Ref<boolean, boolean>>;
    error: Readonly<import("vue").Ref<string | null, string | null>>;
    result: Readonly<import("vue").Ref<{
        readonly words: readonly {
            readonly word: string;
            readonly lemma?: string | undefined;
            readonly confidence: number;
        }[];
        readonly rawWords: readonly string[];
    } | null, {
        readonly words: readonly {
            readonly word: string;
            readonly lemma?: string | undefined;
            readonly confidence: number;
        }[];
        readonly rawWords: readonly string[];
    } | null>>;
    checkPermissions: () => Promise<boolean>;
    requestPermissions: () => Promise<boolean>;
    recognizeFromImage: (imagePath: string, cropArea?: CropArea) => Promise<OcrResult | null>;
    clearResult: () => void;
};
export declare const CapacitorOcrVuePlugin: {
    install(app: App): void;
};
