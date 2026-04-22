Pod::Spec.new do |s|
  s.name = 'CapacitorPluginOcr'
  s.version = '1.0.0'
  s.summary = 'Capacitor OCR Plugin for text recognition'
  s.license = { :type => 'MIT', :file => 'LICENSE' }
  s.homepage = 'https://github.com/example/capacitor-plugin-ocr'
  s.author = { 'Author' => 'author@example.com' }
  s.source = { :git => 'https://github.com/example/capacitor-plugin-ocr.git', :tag => s.version.to_s }
  
  s.ios.deployment_target = '13.0'
  s.source_files = 'Plugin/**/*.{swift,m,h}'
  
  s.dependency 'Capacitor'
  s.dependency 'SnapKit', '~> 5.6'
  
  s.swift_version = '5.0'
end