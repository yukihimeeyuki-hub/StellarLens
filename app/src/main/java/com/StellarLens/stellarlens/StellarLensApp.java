package com.StellarLens.stellarlens;

import androidx.multidex.MultiDexApplication;

/**
 * Enables legacy multidex on API 19-20; API 21+ uses the platform implementation.
 */
public class StellarLensApp extends MultiDexApplication {
}
