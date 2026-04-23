Pod::Spec.new do |s|
  s.name = 'CapacitorPluginOcr'
  s.version = '1.0.0'
  s.summary = 'Capacitor OCR plugin for English text recognition'
  s.license = { :type => 'MIT', :file => '../LICENSE' }
  s.homepage = 'https://github.com/local/capacitor-plugin-ocr'
  s.author = { 'Author' => 'author@example.com' }
  s.source = { :git => 'https://github.com/local/capacitor-plugin-ocr.git', :tag => s.version.to_s }

  s.ios.deployment_target = '13.0'
  s.source_files = 'Plugin/**/*.{swift,m,h}'
  s.frameworks = 'ImageIO', 'Photos', 'UIKit', 'Vision'

  s.dependency 'Capacitor'
  s.swift_version = '5.0'
end
