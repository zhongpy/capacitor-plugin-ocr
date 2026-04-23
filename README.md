# Capacitor OCR Plugin

一个适用于 Capacitor 7 和 Vue 3 的 OCR 插件。Android 使用 Google ML Kit Text Recognition，iOS 使用系统 Vision 框架，支持从本地图片路径识别英文单词，并返回去重后的单词、原始单词列表和简单词形还原结果。

## 功能

- Android/iOS 原生 OCR。
- 只提取英文单词。
- 返回 `words` 去重结果和 `rawWords` 原始识别结果。
- 支持可选的百分比裁剪区域。
- 提供 Vue 3 `useOcr()` helper。

## 安装

```bash
npm install capacitor-plugin-ocr
npx cap sync
```

如果需要拍照或从相册选图，建议在业务 App 中使用 `@capacitor/camera` 获取图片路径，并优先把 `photo.path` 传给本插件。`photo.webPath` 主要给 WebView 显示使用，不建议直接传给原生 OCR。

## 基础用法

```ts
import CapacitorPluginOcr from 'capacitor-plugin-ocr';

const result = await CapacitorPluginOcr.recognizeEnglishText({
  imagePath: '/path/to/image.jpg',
});

console.log(result.words);
```

## Vue 3 用法

```ts
import { createApp } from 'vue';
import App from './App.vue';
import { CapacitorOcrVuePlugin } from 'capacitor-plugin-ocr';

createApp(App).use(CapacitorOcrVuePlugin).mount('#app');
```

```vue
<script setup lang="ts">
import { useOcr } from 'capacitor-plugin-ocr';

const { isProcessing, error, result, recognizeFromImage } = useOcr();

async function runOcr(imagePath: string) {
  await recognizeFromImage(imagePath);
}
</script>
```

## API

### `recognizeEnglishText(options)`

```ts
CapacitorPluginOcr.recognizeEnglishText({
  imagePath: '/path/to/image.jpg',
  cropArea: { x: 0.1, y: 0.1, width: 0.8, height: 0.8 },
});
```

`cropArea` 使用 0 到 1 的百分比坐标，相对于原图尺寸：

```ts
interface CropArea {
  x: number;
  y: number;
  width: number;
  height: number;
}
```

返回值：

```ts
interface OcrResult {
  words: Array<{
    word: string;
    lemma?: string;
    confidence: number;
  }>;
  rawWords: string[];
}
```

### `cropImage(options)`

按百分比区域裁剪图片，并返回临时文件路径。

```ts
const cropResult = await CapacitorPluginOcr.cropImage({
  imagePath: '/path/to/image.jpg',
  cropArea: { x: 0, y: 0.25, width: 1, height: 0.5 },
});
```

### `checkPermissions()` / `requestPermissions()`

当前版本不直接打开相机或相册，因此这两个方法返回 `{ granted: true }`。相机和相册权限应由调用方使用的图片选择插件负责。

### `startCropUI(options)`

当前版本不提供原生交互式裁剪 UI。请使用 `cropImage()` 或在业务 App 中接入专用裁剪组件。

## 平台说明

### Android

插件依赖：

```gradle
implementation 'com.google.mlkit:text-recognition:16.0.1'
```

宿主 Android 工程需要能访问 `google()` 和 `mavenCentral()`。

### iOS

iOS 使用系统 Vision 框架，最低支持 iOS 13。

## 开发

```bash
npm install
npm run build
```
