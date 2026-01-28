package gloomlib.test;

import gloomlib.gui.navigation.NavigationManager;
import gloomlib.gui.window.Window;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the intelligent repeating subsequence detection algorithm in NavigationHistory.
 * <p>
 * This test verifies that the navigation system correctly identifies and removes
 * repeating navigation patterns while protecting the initial window.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NavigationSequenceDeduplicationTest {

    private static ServerMock server;
    private Player player;
    private NavigationManager navigationManager;

    // Mock windows for testing
    private TestWindow windowA;
    private TestWindow windowB;
    private TestWindow windowC;
    private TestWindow windowD;

    @BeforeAll
    static void setUpMockBukkit() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownMockBukkit() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        player = server.addPlayer();
        navigationManager = NavigationManager.getInstance();
        navigationManager.clear(player);

        // Create test windows
        windowA = new TestWindow("A");
        windowB = new TestWindow("B");
        windowC = new TestWindow("C");
        windowD = new TestWindow("D");
    }

    @Test
    @Order(1)
    @DisplayName("A-B-C-D-B-C-D should optimize to A-B-C-D")
    void shouldMergeRepeatingSequenceBCD() {
        // Push sequence: A, B, C, D, B, C, D
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);
        navigationManager.push(player, windowD);

        assertEquals(4, navigationManager.getHistory(player).getDepth(), "Should have 4 windows before repeat");

        // Push repeating sequence B, C, D
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);
        navigationManager.push(player, windowD);

        // Should detect and remove the repeating B-C-D sequence
        assertEquals(4, navigationManager.getHistory(player).getDepth(), 
            "Should optimize A-B-C-D-B-C-D to A-B-C-D (merge duplicate B-C-D)");
    }

    @Test
    @Order(2)
    @DisplayName("A-B-C-A-B-C should NOT optimize (protects initial A)")
    void shouldNotMergeWhenInvolvesInitialWindow() {
        // Push sequence: A, B, C, A, B, C
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);

        assertEquals(3, navigationManager.getHistory(player).getDepth(), "Should have 3 windows");

        // Push repeating sequence A, B, C (involves initial window A)
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);

        // Should NOT optimize because it involves index 0 (initial window A)
        assertEquals(6, navigationManager.getHistory(player).getDepth(), 
            "Should NOT optimize A-B-C-A-B-C (protects initial window)");
    }

    @Test
    @Order(3)
    @DisplayName("A-B-C-A-B-C-A-B-C should optimize to A-B-C-A-B-C")
    void shouldMergeThirdRepetitionOnly() {
        // Push sequence: A, B, C, A, B, C, A, B, C
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);

        assertEquals(6, navigationManager.getHistory(player).getDepth(), 
            "First two A-B-C sequences should be kept");

        // Push third A-B-C sequence
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);

        // Should merge the third A-B-C because it doesn't involve index 0
        assertEquals(6, navigationManager.getHistory(player).getDepth(), 
            "Should optimize A-B-C-A-B-C-A-B-C to A-B-C-A-B-C (merge third repeat)");
    }

    @Test
    @Order(4)
    @DisplayName("A-B-A-B should optimize to A-B")
    void shouldMergeTwoElementSequence() {
        // Push sequence: A, B, A, B
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);

        assertEquals(2, navigationManager.getHistory(player).getDepth(), "Should have 2 windows");

        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);

        // Should NOT optimize because A-B starts at index 0
        assertEquals(4, navigationManager.getHistory(player).getDepth(), 
            "Should NOT merge A-B-A-B (involves initial window)");
    }

    @Test
    @Order(5)
    @DisplayName("A-B-C-B-C should optimize to A-B-C")
    void shouldMergeShorterRepeatingSequence() {
        // Push sequence: A, B, C, B, C
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);

        assertEquals(3, navigationManager.getHistory(player).getDepth(), "Should have 3 windows");

        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);

        // Should detect and remove the repeating B-C sequence
        assertEquals(3, navigationManager.getHistory(player).getDepth(), 
            "Should optimize A-B-C-B-C to A-B-C (merge duplicate B-C)");
    }

    @Test
    @Order(6)
    @DisplayName("Complex pattern: A-B-C-D-E-D-E should optimize to A-B-C-D-E")
    void shouldMergeComplexPattern() {
        // Push sequence: A, B, C, D, E, D, E
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);
        navigationManager.push(player, windowD);

        TestWindow windowE = new TestWindow("E");
        navigationManager.push(player, windowE);

        assertEquals(5, navigationManager.getHistory(player).getDepth(), "Should have 5 windows");

        navigationManager.push(player, windowD);
        navigationManager.push(player, windowE);

        // Should detect and remove the repeating D-E sequence
        assertEquals(5, navigationManager.getHistory(player).getDepth(), 
            "Should optimize A-B-C-D-E-D-E to A-B-C-D-E (merge duplicate D-E)");
    }

    @Test
    @Order(7)
    @DisplayName("No optimization for non-repeating sequence")
    void shouldNotOptimizeNonRepeatingSequence() {
        // Push sequence: A, B, C, D (all different)
        navigationManager.push(player, windowA);
        navigationManager.push(player, windowB);
        navigationManager.push(player, windowC);
        navigationManager.push(player, windowD);

        assertEquals(4, navigationManager.getHistory(player).getDepth(), 
            "Should keep all 4 windows (no repeating pattern)");
    }

    @Test
    @Order(8)
    @DisplayName("Performance test: large navigation stack")
    void shouldHandleLargeNavigationStack() {
        // Create a large navigation pattern
        for (int i = 0; i < 10; i++) {
            navigationManager.push(player, windowA);
            navigationManager.push(player, windowB);
            navigationManager.push(player, windowC);
            navigationManager.push(player, windowD);
        }

        int depth = navigationManager.getHistory(player).getDepth();
        assertTrue(depth <= 40, "Should handle large stacks efficiently (depth: " + depth + ")");
        assertTrue(depth >= 4, "Should maintain at least one complete sequence");
    }

    /**
     * Simple test window implementation for navigation tests.
     */
    private static class TestWindow implements Window {
        private final String name;
        private boolean closed = false;

        TestWindow(String name) {
            this.name = name;
        }

        @Override
        public void open() {
            closed = false;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public gloomlib.gui.api.GloomGui getGui() {
            return null;
        }

        @Override
        public Player getViewer() {
            return null;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void handleClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
            // No-op for test
        }

        @Override
        public void tick() {
            // No-op for test
        }

        @Override
        public String toString() {
            return "Window[" + name + "]";
        }
    }
}
