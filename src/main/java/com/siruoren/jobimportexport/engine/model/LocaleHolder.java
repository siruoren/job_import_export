package com.siruoren.jobimportexport.engine.model;

import org.jvnet.localizer.LocaleProvider;

import java.util.Locale;

public class LocaleHolder {

    private static final ThreadLocal<Locale> THREAD_LOCALE = new ThreadLocal<>();
    private static volatile boolean installed = false;

    private LocaleHolder() {
    }

    public static void install() {
        if (installed) return;
        synchronized (LocaleHolder.class) {
            if (installed) return;
            LocaleProvider original = LocaleProvider.getProvider();
            LocaleProvider.setProvider(new LocaleProvider() {
                @Override
                public Locale get() {
                    Locale locale = THREAD_LOCALE.get();
                    if (locale != null) return locale;
                    return original.get();
                }
            });
            installed = true;
        }
    }

    public static void setLocale(Locale locale) {
        install();
        THREAD_LOCALE.set(locale);
    }

    public static Locale getLocale() {
        return THREAD_LOCALE.get();
    }

    public static void clear() {
        THREAD_LOCALE.remove();
    }
}
