import { registerPlugin } from '@capacitor/core';
import type { CapacitorPluginOcrPlugin } from './definitions';

// Re-export all types and interfaces
export type { OcrWordResult, OcrResult, ImageSourceType, CapacitorPluginOcrPlugin } from './definitions';

// Import Vue plugin and re-export
import { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage } from './vue-plugin';

export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };

const CapacitorPluginOcr = registerPlugin<CapacitorPluginOcrPlugin>('CapacitorPluginOcr');

export default CapacitorPluginOcr;
