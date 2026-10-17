package __PKG__

import __PKG__.di.SdkKoin

/**
 * Public entry point of the SDK.
 *
 * Android: `__SDK_FACADE__.initialize(context, AppConfig(baseUrl = "https://api.example.com/", isDebug = BuildConfig.DEBUG))`
 * iOS:     `import __SDK_NAME__` then `__SDK_FACADE__.shared.initialize(config: AppConfig(baseUrl: "https://api.example.com/", isDebug: false))`
 *
 * The facade's name differs from the framework/module name (__SDK_NAME__) on purpose: a Swift type
 * named like its module gets renamed by SKIE (`__SDK_NAME___`) and module-qualified lookups break.
 *
 * Features are exposed as properties (`__SDK_FACADE__.profile.getProfile(...)`), each one a small
 * class listing that feature's use cases. Everything else in the SDK is internal.
 */
public object __SDK_FACADE__ {
    public val isInitialized: Boolean get() = SdkKoin.isStarted

    // tezca-kmp:sdk-features (new-feature adds `public val <feature>: <Feature>Feature get() = <Feature>Feature(SdkKoin.koin)` below)
}
