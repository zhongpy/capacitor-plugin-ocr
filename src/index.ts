import { registerPlugin } from '@capacitor/core';
import type { CapacitorPluginOcrPlugin } from './definitions.js';

export type {
  CapacitorPluginOcrPlugin,
  CropArea,
  CropImageOptions,
  CropResult,
  ImageSourceType,
  OcrResult,
  OcrWordResult,
  PermissionResult,
  RecognizeEnglishTextOptions,
  StartCropUIOptions,
} from './definitions.js';

import {
  CapacitorOcrVuePlugin,
  checkOcrPermissions,
  recognizeEnglishFromImage,
  requestOcrPermissions,
  useOcr,
} from './vue-plugin.js';

export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };

export const CapacitorPluginOcr = registerPlugin<CapacitorPluginOcrPlugin>('CapacitorPluginOcr');

export default CapacitorPluginOcr;
