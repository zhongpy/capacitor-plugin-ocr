#import <Capacitor/Capacitor.h>

CAP_Plugin(CapacitorPluginOcr, 
  CAP_Plugin_Method(checkPermissions, CAPPluginReturnPromise);
  CAP_Plugin_Method(requestPermissions, CAPPluginReturnPromise);
  CAP_Plugin_Method(recognizeEnglishText, CAPPluginReturnPromise);
  CAP_Plugin_Method(cropImage, CAPPluginReturnPromise);
  CAP_Plugin_Method(startCropUI, CAPPluginReturnPromise);
)
