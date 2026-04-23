import { registerPlugin } from '@capacitor/core';
import { CapacitorOcrVuePlugin, checkOcrPermissions, recognizeEnglishFromImage, requestOcrPermissions, useOcr, } from './vue-plugin.js';
export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };
export const CapacitorPluginOcr = registerPlugin('CapacitorPluginOcr');
export default CapacitorPluginOcr;
