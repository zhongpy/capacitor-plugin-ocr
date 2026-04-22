# Capacitor OCR Plugin

适用于Vue3的Capacitor OCR插件，支持Android和iOS平台的文字识别功能。

## 功能特性

- 📷 支持拍照和从相册选择图片进行识别
- 🔤 只识别英文单词，自动过滤其他字符
- 📝 单词去重处理
- 🔄 变形词处理：输出时显示"识别词 + 词根"（如：played + play）
- 📱 支持Android和iOS平台
- 🔐 自动请求摄像头和相册权限

## 技术栈

- **Capacitor**: 7.4.3
- **Android**: Tesseract OCR + 内置词形还原
- **iOS**: Vision框架 + 内置词形还原
- **Vue3**: 组合式API

## 安装

```bash
npm install capacitor-plugin-ocr
```

## 使用方法

### 1. 注册插件

在 `main.ts` 中：

```typescript
import { CapacitorOcrVuePlugin } from "capacitor-plugin-ocr"
import { createApp } from "vue"
import App from "./App.vue"

const app = createApp(App)
app.use(CapacitorOcrVuePlugin)
app.mount("#app")
```

### 2. 使用组合式API

```vue
<template>
	<div>
		<button @click="takePhoto">拍照识别</button>
		<button @click="pickFromGallery">从相册选择</button>

		<div v-if="isProcessing">识别中...</div>

		<div v-if="result">
			<h3>识别结果：</h3>
			<ul>
				<li v-for="word in result.words" :key="word.word">
					{{ word.word }}
					<span v-if="word.lemma">({{ word.lemma }})</span>
				</li>
			</ul>
		</div>

		<div v-if="error" style="color: red;">{{ error }}</div>
	</div>
</template>

<script setup lang="ts">
import { useOcr } from "capacitor-plugin-ocr"
import { Camera, CameraResultType } from "@capacitor/camera"

const { isProcessing, error, result, requestPermissions, recognizeFromImage } = useOcr()

async function takePhoto() {
	// 请求权限
	const hasPermission = await requestPermissions()
	if (!hasPermission) {
		alert("需要摄像头权限")
		return
	}

	// 拍照
	const photo = await Camera.getPhoto({
		resultType: CameraResultType.Uri,
		source: CameraSource.Camera,
		quality: 90,
	})

	// 识别文字
	await recognizeFromImage(photo.path)
}

async function pickFromGallery() {
	// 请求权限
	const hasPermission = await requestPermissions()
	if (!hasPermission) {
		alert("需要相册权限")
		return
	}

	// 从相册选择
	const photo = await Camera.getPhoto({
		resultType: CameraResultType.Uri,
		source: CameraSource.Photos,
		quality: 90,
	})

	// 识别文字
	await recognizeFromImage(photo.path)
}
</script>
```

### 3. 直接调用方法

```typescript
import { requestOcrPermissions, recognizeEnglishFromImage } from "capacitor-plugin-ocr"

// 检查权限
const hasPermission = await requestOcrPermissions()

// 识别图片
const result = await recognizeEnglishFromImage("/path/to/image.jpg")
// result: { words: [...], rawWords: [...] }
```

## API

### 方法

| 方法                                  | 描述             |
| ------------------------------------- | ---------------- |
| `checkPermissions()`                  | 检查权限状态     |
| `requestPermissions()`                | 请求权限         |
| `recognizeEnglishText({ imagePath })` | 识别图片中的英文 |

### 返回结果格式

```typescript
interface OcrResult {
	words: OcrWordResult[]
	rawWords: string[]
}

interface OcrWordResult {
	word: string // 识别出的单词
	lemma?: string // 词根（仅当与原词不同时存在）
	confidence: number // 置信度
}
```

## Android配置

需要在Android项目中添加Tesseract语言数据文件：

- 将 `eng.traineddata` 放入 `android/app/src/main/assets/tessdata/` 目录

可以从以下地址下载：
https://github.com/tesseract-ocr/tessdata

## iOS配置

iOS使用系统Vision框架，无需额外配置。

## 权限说明

### Android

- `CAMERA` - 拍照
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` - 读取相册

### iOS

- `NSCameraUsageDescription`
- `NSPhotoLibraryUsageDescription`

## License

MIT
