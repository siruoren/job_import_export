package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.LocaleHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class LocaleHolderTest {

    @AfterEach
    void tearDown() {
        LocaleHolder.clear();
    }

    @Test
    void testSetAndGetLocale() {
        Locale locale = Locale.CHINESE;

        LocaleHolder.setLocale(locale);

        assertEquals(locale, LocaleHolder.getLocale());
    }

    @Test
    void testSetLocale_ToChinese() {
        Locale locale = Locale.CHINESE;

        LocaleHolder.setLocale(locale);

        assertEquals(Locale.CHINESE, LocaleHolder.getLocale());
    }

    @Test
    void testSetLocale_ToUs() {
        Locale locale = Locale.US;

        LocaleHolder.setLocale(locale);

        assertEquals(Locale.US, LocaleHolder.getLocale());
    }

    @Test
    void testSetLocale_ToChina() {
        Locale locale = Locale.CHINA;

        LocaleHolder.setLocale(locale);

        assertEquals(Locale.CHINA, LocaleHolder.getLocale());
    }

    @Test
    void testSetLocale_ToSimplifiedChinese() {
        Locale locale = Locale.SIMPLIFIED_CHINESE;

        LocaleHolder.setLocale(locale);

        assertEquals(Locale.SIMPLIFIED_CHINESE, LocaleHolder.getLocale());
    }

    @Test
    void testSetLocale_ToTraditionalChinese() {
        Locale locale = Locale.TRADITIONAL_CHINESE;

        LocaleHolder.setLocale(locale);

        assertEquals(Locale.TRADITIONAL_CHINESE, LocaleHolder.getLocale());
    }

    @Test
    void testSetLocale_WithNull() {
        LocaleHolder.setLocale(Locale.CHINESE);
        LocaleHolder.setLocale(null);

        assertNull(LocaleHolder.getLocale());
    }

    @Test
    void testGetLocale_WithoutSet_ReturnsNull() {
        LocaleHolder.clear();

        assertNull(LocaleHolder.getLocale());
    }

    @Test
    void testClear_RemovesLocale() {
        LocaleHolder.setLocale(Locale.CHINESE);

        LocaleHolder.clear();

        assertNull(LocaleHolder.getLocale());
    }

    @Test
    void testClear_WhenNotSet_DoesNotThrow() {
        assertDoesNotThrow(() -> LocaleHolder.clear());
    }

    @Test
    void testSetLocale_OverwritesPrevious() {
        LocaleHolder.setLocale(Locale.CHINESE);

        LocaleHolder.setLocale(Locale.US);

        assertEquals(Locale.US, LocaleHolder.getLocale());
    }

    @Test
    void testSetAndGet_CanBeCalledMultipleTimes() {
        Locale locale = Locale.CHINA;

        LocaleHolder.setLocale(locale);
        Locale result1 = LocaleHolder.getLocale();
        Locale result2 = LocaleHolder.getLocale();
        Locale result3 = LocaleHolder.getLocale();

        assertEquals(locale, result1);
        assertEquals(locale, result2);
        assertEquals(locale, result3);
    }

    @Test
    void testThreadIsolation() throws InterruptedException {
        Locale mainLocale = Locale.CHINESE;
        Locale threadLocale = Locale.US;

        LocaleHolder.setLocale(mainLocale);

        Thread thread = new Thread(() -> {
            assertNull(LocaleHolder.getLocale());
            LocaleHolder.setLocale(threadLocale);
            assertEquals(threadLocale, LocaleHolder.getLocale());
        });

        thread.start();
        thread.join();

        assertEquals(mainLocale, LocaleHolder.getLocale());
    }

    @Test
    void testClear_DoesNotAffectOtherThreads() throws InterruptedException {
        Locale mainLocale = Locale.CHINESE;
        Locale threadLocale = Locale.US;

        LocaleHolder.setLocale(mainLocale);

        Thread thread = new Thread(() -> {
            LocaleHolder.setLocale(threadLocale);
            LocaleHolder.clear();
        });

        thread.start();
        thread.join();

        assertEquals(mainLocale, LocaleHolder.getLocale());
    }
}
