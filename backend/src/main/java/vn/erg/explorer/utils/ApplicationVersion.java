package vn.erg.explorer.utils;

import java.util.ResourceBundle;

/** Version and build date from {@code application.properties} (Maven fills the build date at package time). */
public final class ApplicationVersion {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("application");

    private ApplicationVersion() {
    }

    public static String applicationVersion() {
        return BUNDLE.getString("application.version");
    }

    public static String applicationBuildDate() {
        return BUNDLE.getString("application.buildDate");
    }

}
