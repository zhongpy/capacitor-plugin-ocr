// This file provides Vue3 integration for the OCR plugin
// It's designed to work with Vue3's composition API
// The actual ref/reativity will be provided by the user's Vue app
import { Capacitor } from '@capacitor/core';
import CapacitorPluginOcr from './index';
import { ref, readonly } from 'vue';
/**
 * 检查并请求OCR所需权限
 */
export async function requestOcrPermissions() {
    if (Capacitor.getPlatform() === 'android' || Capacitor.getPlatform() === 'ios') {
        const result = await CapacitorPluginOcr.requestPermissions();
        return result.granted;
    }
    // 桌面平台不需要权限
    return true;
}
/**
 * 检查OCR权限状态
 */
export async function checkOcrPermissions() {
    if (Capacitor.getPlatform() === 'android' || Capacitor.getPlatform() === 'ios') {
        const result = await CapacitorPluginOcr.checkPermissions();
        return result.granted;
    }
    return true;
}
/**
 * 识别图片中的英文文字
 * @param imagePath 图片路径
 * @returns 识别结果
 */
export async function recognizeEnglishFromImage(imagePath) {
    return await CapacitorPluginOcr.recognizeEnglishText({ imagePath });
}
// ===== Vue3 组合式API =====
/**
 * Vue3 组合式API - useOcr
 * 提供OCR功能的响应式接口
 */
export function useOcr() {
    const isProcessing = ref(false);
    const error = ref(null);
    const result = ref(null);
    /**
     * 检查权限
     */
    const checkPermissions = async () => {
        try {
            error.value = null;
            return await checkOcrPermissions();
        }
        catch (e) {
            error.value = e instanceof Error ? e.message : 'Permission check failed';
            return false;
        }
    };
    /**
     * 请求权限
     */
    const requestPermissions = async () => {
        try {
            error.value = null;
            return await requestOcrPermissions();
        }
        catch (e) {
            error.value = e instanceof Error ? e.message : 'Permission request failed';
            return false;
        }
    };
    /**
     * 从图片识别英文
     */
    const recognizeFromImage = async (imagePath) => {
        try {
            isProcessing.value = true;
            error.value = null;
            const ocrResult = await recognizeEnglishFromImage(imagePath);
            result.value = ocrResult;
            return ocrResult;
        }
        catch (e) {
            error.value = e instanceof Error ? e.message : 'OCR recognition failed';
            return null;
        }
        finally {
            isProcessing.value = false;
        }
    };
    /**
     * 清理结果
     */
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
// ===== Vue3 插件 =====
/**
 * Vue3 插件安装函数
 */
export const CapacitorOcrVuePlugin = {
    install(app) {
        // 将方法挂载到全局属性
        app.config.globalProperties.$ocr = {
            requestPermissions: requestOcrPermissions,
            checkPermissions: checkOcrPermissions,
            recognizeFromImage: recognizeEnglishFromImage,
        };
        // 提供组合式API
        app.provide('ocr', {
            requestPermissions: requestOcrPermissions,
            checkPermissions: checkOcrPermissions,
            recognizeFromImage: recognizeEnglishFromImage,
            useOcr,
        });
    },
};
