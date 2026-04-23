import type { CapacitorPluginOcrPlugin } from './definitions';
export type { OcrWordResult, OcrResult, ImageSourceType, CapacitorPluginOcrPlugin } from './definitions';
import { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage } from './vue-plugin';
export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };
declare const CapacitorPluginOcr: CapacitorPluginOcrPlugin;
export default CapacitorPluginOcr;
