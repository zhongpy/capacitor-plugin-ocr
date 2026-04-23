import type { App, Ref } from 'vue';
import type { OcrResult } from './definitions';
/**
 * 检查并请求OCR所需权限
 */
export declare function requestOcrPermissions(): Promise<boolean>;
/**
 * 检查OCR权限状态
 */
export declare function checkOcrPermissions(): Promise<boolean>;
/**
 * 识别图片中的英文文字
 * @param imagePath 图片路径
 * @returns 识别结果
 */
export declare function recognizeEnglishFromImage(imagePath: string): Promise<OcrResult>;
/**
 * Vue3 组合式API - useOcr
 * 提供OCR功能的响应式接口
 */
export declare function useOcr(): {
    isProcessing: Readonly<Ref<boolean, boolean>>;
    error: Readonly<Ref<string | null, string | null>>;
    result: Readonly<Ref<{
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
    recognizeFromImage: (imagePath: string) => Promise<OcrResult | null>;
    clearResult: () => void;
};
/**
 * Vue3 插件安装函数
 */
export declare const CapacitorOcrVuePlugin: {
    install(app: App): void;
};
