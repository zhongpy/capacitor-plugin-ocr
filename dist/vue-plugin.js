import { Capacitor } from '@capacitor/core';
import { readonly, ref } from 'vue';
import CapacitorPluginOcr from './index.js';
export async function requestOcrPermissions() {
    if (Capacitor.isNativePlatform()) {
        const result = await CapacitorPluginOcr.requestPermissions();
        return result.granted;
    }
    return true;
}
export async function checkOcrPermissions() {
    if (Capacitor.isNativePlatform()) {
        const result = await CapacitorPluginOcr.checkPermissions();
        return result.granted;
    }
    return true;
}
export async function recognizeEnglishFromImage(imagePath, cropArea) {
    return CapacitorPluginOcr.recognizeEnglishText({ imagePath, cropArea });
}
export function useOcr() {
    const isProcessing = ref(false);
    const error = ref(null);
    const result = ref(null);
    const checkPermissions = async () => {
        try {
            error.value = null;
            return await checkOcrPermissions();
        }
        catch (cause) {
            error.value = cause instanceof Error ? cause.message : 'Permission check failed';
            return false;
        }
    };
    const requestPermissions = async () => {
        try {
            error.value = null;
            return await requestOcrPermissions();
        }
        catch (cause) {
            error.value = cause instanceof Error ? cause.message : 'Permission request failed';
            return false;
        }
    };
    const recognizeFromImage = async (imagePath, cropArea) => {
        try {
            isProcessing.value = true;
            error.value = null;
            const ocrResult = await recognizeEnglishFromImage(imagePath, cropArea);
            result.value = ocrResult;
            return ocrResult;
        }
        catch (cause) {
            error.value = cause instanceof Error ? cause.message : 'OCR recognition failed';
            return null;
        }
        finally {
            isProcessing.value = false;
        }
    };
    const clearResult = () => {
        result.value = null;
        error.value = null;
    };
    return {
        isProcessing: readonly(isProcessing),
        error: readonly(error),
        result: readonly(result),
        checkPermissions,
        requestPermissions,
        recognizeFromImage,
        clearResult,
    };
}
export const CapacitorOcrVuePlugin = {
    install(app) {
        app.config.globalProperties.$ocr = {
            requestPermissions: requestOcrPermissions,
            checkPermissions: checkOcrPermissions,
            recognizeFromImage: recognizeEnglishFromImage,
        };
        app.provide('ocr', {
            requestPermissions: requestOcrPermissions,
            checkPermissions: checkOcrPermissions,
            recognizeFromImage: recognizeEnglishFromImage,
            useOcr,
        });
    },
};
