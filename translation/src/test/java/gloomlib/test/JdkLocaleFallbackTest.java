package gloomlib.test;

import gloomlib.translation.impl.JdkLocaleFallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JdkLocaleFallback.
 */
class JdkLocaleFallbackTest {

    private JdkLocaleFallback fallback;

    @BeforeEach
    void setUp() {
        fallback = JdkLocaleFallback.INSTANCE;
        fallback.clearCache();
        fallback.setRootLocale(Locale.US);
    }

    @Test
    @DisplayName("zh_HK fallback chain contains locale and root")
    void chineseHongKongFallbackChain() {
        Locale zhHK = Locale.of("zh", "HK");
        List<Locale> chain = fallback.getFallbackChain(zhHK);

        assertNotNull(chain);
        assertFalse(chain.isEmpty());
        assertTrue(
            chain.stream().anyMatch(l ->
                l.getLanguage().equals("zh") && l.getCountry().equals("HK")),
            "Should contain zh_HK"
        );
        assertTrue(
            chain.stream().anyMatch(l ->
                l.getLanguage().equals("zh") && l.getCountry().isEmpty()),
            "Should contain zh"
        );
        assertEquals(Locale.US, chain.get(chain.size() - 1), "Last should be root");
    }

    @Test
    @DisplayName("zh_CN fallback chain contains locale and root")
    void chineseSimplifiedFallbackChain() {
        Locale zhCN = Locale.of("zh", "CN");
        List<Locale> chain = fallback.getFallbackChain(zhCN);

        assertNotNull(chain);
        assertTrue(
            chain.stream().anyMatch(l ->
                l.getLanguage().equals("zh") && l.getCountry().equals("CN")),
            "Should contain zh_CN"
        );
        assertTrue(
            chain.stream().anyMatch(l ->
                l.getLanguage().equals("zh") && l.getCountry().isEmpty()),
            "Should contain zh"
        );
        assertEquals(Locale.US, chain.get(chain.size() - 1), "Last should be root");
    }

    @Test
    @DisplayName("Language-only locale fallback chain")
    void languageOnlyFallbackChain() {
        Locale zh = Locale.of("zh");
        List<Locale> chain = fallback.getFallbackChain(zh);

        assertNotNull(chain);
        assertEquals(zh, chain.get(0));
        assertTrue(chain.contains(Locale.US));
    }

    @Test
    @DisplayName("en_US fallback chain")
    void englishUSFallbackChain() {
        List<Locale> chain = fallback.getFallbackChain(Locale.US);

        assertNotNull(chain);
        assertEquals(Locale.US, chain.get(0));
    }

    @Test
    @DisplayName("getParent returns correct parent")
    void getParentReturnsCorrectParent() {
        Locale zhCN = Locale.of("zh", "CN");
        Locale parent = fallback.getParent(zhCN);

        assertEquals(Locale.of("zh"), parent);
    }

    @Test
    @DisplayName("getParent returns null for language-only locale")
    void getParentForLanguageOnlyReturnsNull() {
        Locale parent = fallback.getParent(Locale.of("zh"));

        assertNull(parent);
    }

    @Test
    @DisplayName("Cache works correctly")
    void cacheShouldWork() {
        Locale zhCN = Locale.of("zh", "CN");

        fallback.getFallbackChain(zhCN);
        int sizeAfterFirst = fallback.getCacheSize();

        fallback.getFallbackChain(zhCN);
        int sizeAfterSecond = fallback.getCacheSize();

        assertEquals(sizeAfterFirst, sizeAfterSecond);
    }

    @Test
    @DisplayName("setRootLocale changes chain end")
    void setRootLocaleShouldChangeChainEnd() {
        fallback.setRootLocale(Locale.of("en", "GB"));

        List<Locale> chain = fallback.getFallbackChain(Locale.of("zh", "CN"));

        assertTrue(chain.contains(Locale.of("en", "GB")));
    }

    @Test
    @DisplayName("clearCache empties cache")
    void clearCacheShouldEmptyCache() {
        fallback.getFallbackChain(Locale.of("zh", "CN"));
        assertTrue(fallback.getCacheSize() > 0);

        fallback.clearCache();

        assertEquals(0, fallback.getCacheSize());
    }
}
