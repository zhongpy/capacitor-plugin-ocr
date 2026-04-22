#import <Capacitor/Capacitor.h>

CAP_PLUGIN(CapacitorPluginOcr, 
  CAP_PLUGIN_METHOD(checkPermissions, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(requestPermissions, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(recognizeEnglishText, CAPPluginReturnPromise);
)