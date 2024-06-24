Pod::Spec.new do |spec|
    spec.name                     = 'webView'
    spec.version                  = '1.0'
    spec.homepage                 = 'https://github.com/JetBrains/kotlin'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'CocoaPods test library'
    spec.vendored_frameworks      = 'build/cocoapods/framework/webView.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target = '13.5'
                
                
    if !Dir.exist?('build/cocoapods/framework/webView.framework') || Dir.empty?('build/cocoapods/framework/webView.framework')
        raise "

        Kotlin framework 'webView' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :webView:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':webView',
        'PRODUCT_MODULE_NAME' => 'webView',
    }
                
    spec.script_phases = [
        {
            :name => 'Build webView',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../../../../../../../private/var/folders/nr/v2b7jwc51gj_15wg471jgdgm0000gp/T/wrap1loc/gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
end