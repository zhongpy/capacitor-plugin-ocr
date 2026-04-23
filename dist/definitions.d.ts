/**
 * OCR识别结果项
 */
export interface OcrWordResult {
    /** 识别出的英文单词 */
    word: string;
    /** 词根/原形（如果存在变形） */
    lemma?: string;
    /** 单词在图片中的置信度 */
    confidence: number;
}
/**
 * OCR识别结果
 */
export interface OcrResult {
    /** 识别出的单词列表（去重后） */
    words: OcrWordResult[];
    /** 原始识别的所有单词（未去重） */
    rawWords: string[];
}
/**
 * 图像来源类型
 */
export type ImageSourceType = 'camera' | 'gallery';
/**
 * 裁剪区域（百分比，相对于原图尺寸）
 */
export interface CropArea {
    /** 左上角X坐标（0-1） */
    x: number;
    /** 左上角Y坐标（0-1） */
    y: number;
    /** 宽度（0-1） */
    width: number;
    /** 高度（0-1） */
    height: number;
}
/**
 * 裁剪结果
 */
export interface CropResult {
    /** 裁剪后的图片路径 */
    croppedImagePath: string;
}
/**
 * CapacitorPluginOcr 插件接口
 */
export interface CapacitorPluginOcrPlugin {
    /**
     * 识别图片中的英文文字
     * @param options 识别选项
     */
    recognizeEnglishText(options: {
        /** 图片路径（本地文件路径） */
        imagePath: string;
        /** 可选：裁剪区域（百分比，0-1） */
        cropArea?: CropArea;
    }): Promise<OcrResult>;
    /**
     * 裁剪图片
     * @param options 裁剪选项
     */
    cropImage(options: {
        /** 图片路径 */
        imagePath: string;
        /** 裁剪区域（百分比，0-1） */
        cropArea: CropArea;
    }): Promise<CropResult>;
    /**
     * 启动交互式裁剪UI（让用户手动选择区域）
     * @param options 选项
     */
    startCropUI(options: {
        /** 图片路径 */
        imagePath: string;
    }): Promise<CropResult>;
    /**
     * 检查并请求必要的权限
     * @returns 权限是否已授予
     */
    requestPermissions(): Promise<{
        granted: boolean;
    }>;
    /**
     * 检查权限状态
     * @returns 权限状态
     */
    checkPermissions(): Promise<{
        granted: boolean;
    }>;
}
