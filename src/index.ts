import { registerPlugin } from '@capacitor/core';
import type { CapacitorPluginOcrPlugin } from './definitions';

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
} from './definitions';

import {
  CapacitorOcrVuePlugin,
  checkOcrPermissions,
  recognizeEnglishFromImage,
  requestOcrPermissions,
  useOcr,
} from './vue-plugin';

export { CapacitorOcrVuePlugin, useOcr, requestOcrPermissions, checkOcrPermissions, recognizeEnglishFromImage };

const CapacitorPluginOcr = registerPlugin<CapacitorPluginOcrPlugin>('CapacitorPluginOcr');

export default CapacitorPluginOcr;
