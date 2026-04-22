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
  }): Promise<OcrResult>;

  /**
   * 检查并请求必要的权限
   * @returns 权限是否已授予
   */
  requestPermissions(): Promise<{ granted: boolean }>;

  /**
   * 检查权限状态
   * @returns 权限状态
   */
  checkPermissions(): Promise<{ granted: boolean }>;
}