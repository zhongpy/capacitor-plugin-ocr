import { registerPlugin } from '@capacitor/core';
// Import Vue plugin and re-export
import { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage } from './vue-plugin';
export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };
const CapacitorPluginOcr = registerPlugin('CapacitorPluginOcr');
export default CapacitorPluginOcr;
