Pod::Spec.new do |spec|
    spec.name                     = 'windowManager'
    spec.version                  = '1.0'
    spec.homepage                 = 'https://github.com/JetBrains/kotlin'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'CocoaPods test library'
    spec.vendored_frameworks      = 'build/cocoapods/framework/windowManager.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target = '13.5'
                
                
    if !Dir.exist?('build/cocoapods/framework/windowManager.framework') || Dir.empty?('build/cocoapods/framework/windowManager.framework')
        raise "

        Kotlin framework 'windowManager' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :windowManager:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':windowManager',
        'PRODUCT_MODULE_NAME' => 'windowManager',
    }
                
    spec.script_phases = [
        {
            :name => 'Build windowManager',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/ios/windowManager/compose-resources']
end