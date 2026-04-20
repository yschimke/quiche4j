package io.quiche4j;

import java.io.IOException;
import java.net.URL;

/**
 * Helper functionality to try to resolve native library from JAR
 * in case when a system dependency is not available.
 *
 * Searches for the native library in platform-specific subdirectories first,
 * then falls back to the flat /native-libs/ directory for backwards compatibility.
 *
 * Platform directory layout:
 *   /native-libs/linux-x86_64/libquiche_jni.so
 *   /native-libs/linux-aarch64/libquiche_jni.so
 *   /native-libs/osx-aarch64/libquiche_jni.dylib
 *   /native-libs/osx-x86_64/libquiche_jni.dylib
 *   /native-libs/windows-x86_64/libquiche_jni.dll
 */
public final class NativeUtils {

    private static final String DEFAULT_DIR = "/native-libs/";

    public static void loadEmbeddedLibrary(String libname) {
        loadEmbeddedLibrary(DEFAULT_DIR, libname);
    }

    public static void loadEmbeddedLibrary(String dir, String libname) {
        // On Android, Native.java's static initializer already called System.loadLibrary(libname)
        // before calling us, and we're only here because that threw UnsatisfiedLinkError. Retrying
        // the same call won't help — the classpath hasn't changed. And the classpath-extraction
        // path below silently swallows IOException from copyFileFromJAR, which would give the
        // caller a confusing "No implementation found" later with no hint that the AAR packaging
        // was wrong. Fail fast with a descriptive error instead.
        if (isAndroid()) {
            throw new UnsatisfiedLinkError(
                "lib" + libname + ".so was not found via System.loadLibrary on Android. "
                    + "The quiche4j-jni JAR/AAR must place native libraries under lib/<abi>/ "
                    + "so that the Android Gradle Plugin can extract them into the APK's "
                    + "jniLibs. Verify the build with `-PandroidAbis=<abi>` targets your device."
            );
        }

        final String filename = "lib" + libname;
        final String platformDir = detectPlatformDir();
        final String ext = detectExtension();

        String nativeLibraryFilepath = null;

        // Try platform-specific subdirectory first
        if (platformDir != null && ext != null) {
            final String filepath = dir + platformDir + "/" + filename + "." + ext;
            final URL url = Quiche.class.getResource(filepath);
            if (url != null) {
                nativeLibraryFilepath = filepath;
            }
        }

        // Fall back to flat directory (backwards compatibility)
        if (nativeLibraryFilepath == null) {
            String[] extensions = new String[]{"so", "dylib", "dll"};
            for (String e : extensions) {
                final String filepath = dir + filename + "." + e;
                final URL url = Quiche.class.getResource(filepath);
                if (url != null) {
                    nativeLibraryFilepath = filepath;
                    break;
                }
            }
        }

        if (nativeLibraryFilepath != null) {
            // native library found within JAR, extract and load
            try {
                final String libfile = Utils.copyFileFromJAR("libs", nativeLibraryFilepath);
                System.load(libfile);
            } catch (IOException e) {
                // no-op
            }
        }
    }

    private static String detectPlatformDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        // Android's os.name is also "Linux", so detect it explicitly so we pick Android-ABI
        // native libs (built against bionic libc via the NDK) rather than desktop Linux ones.
        if (isAndroid()) {
            String abi;
            if (arch.equals("aarch64") || arch.equals("arm64")) {
                abi = "arm64-v8a";
            } else if (arch.startsWith("armv7") || arch.equals("arm")) {
                abi = "armeabi-v7a";
            } else if (arch.equals("amd64") || arch.equals("x86_64")) {
                abi = "x86_64";
            } else if (arch.equals("i686") || arch.equals("x86")) {
                abi = "x86";
            } else {
                return null;
            }
            return "android-" + abi;
        }

        String osName;
        if (os.contains("linux")) {
            osName = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "osx";
        } else if (os.contains("win")) {
            osName = "windows";
        } else {
            return null;
        }

        String archName;
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            archName = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            archName = "aarch64";
        } else {
            return null;
        }

        return osName + "-" + archName;
    }

    private static boolean isAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String detectExtension() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) return "so";
        if (os.contains("mac") || os.contains("darwin")) return "dylib";
        if (os.contains("win")) return "dll";
        return null;
    }
}
