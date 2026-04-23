import type { CapacitorPluginOcrPlugin } from './definitions.js';
export type { CapacitorPluginOcrPlugin, CropArea, CropImageOptions, CropResult, ImageSourceType, OcrResult, OcrWordResult, PermissionResult, RecognizeEnglishTextOptions, StartCropUIOptions, } from './definitions.js';
import { CapacitorOcrVuePlugin, checkOcrPermissions, recognizeEnglishFromImage, requestOcrPermissions, useOcr } from './vue-plugin.js';
export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };
export declare const CapacitorPluginOcr: CapacitorPluginOcrPlugin;
export default CapacitorPluginOcr;
