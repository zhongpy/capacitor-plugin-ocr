import { registerPlugin } from '@capacitor/core';
import { CapacitorOcrVuePlugin, checkOcrPermissions, recognizeEnglishFromImage, requestOcrPermissions, useOcr, } from './vue-plugin';
export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };
const CapacitorPluginOcr = registerPlugin('CapacitorPluginOcr');
export default CapacitorPluginOcr;
