plugins { id 'com.android.application' }

android {
    namespace 'com.namazvaktim.app'
    compileSdk 35
    defaultConfig {
        applicationId 'com.namazvaktim.app'
        minSdk 23
        targetSdk 35
        versionCode 14
        versionName '1.4.0'
    }
    buildTypes { release { minifyEnabled false; shrinkResources false } }
    java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
}
