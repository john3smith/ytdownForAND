package com.local.ytdown;

import static org.junit.Assert.*;
import org.junit.Test;

public class LoginPageReadinessTest {
    @Test public void requiresLoadCompletionThenThreeSeconds() {
        LoginPageReadiness state = new LoginPageReadiness();
        state.started("https://example.test/page");
        assertFalse(state.ready("https://example.test/page", 50_000));
        state.finished("https://example.test/page", 50_000);
        assertEquals(3_000, state.remaining("https://example.test/page", 50_000));
        assertFalse(state.ready("https://example.test/page", 52_999));
        assertTrue(state.ready("https://example.test/page", 53_000));
    }
    @Test public void redirectInvalidatesPreviousPageAndCallback() {
        LoginPageReadiness state = new LoginPageReadiness();
        state.started("a"); state.finished("a", 0);
        long generation = state.generation();
        state.started("b");
        assertNotEquals(generation, state.generation());
        state.finished("a", 10_000);
        assertFalse(state.ready("a", 50_000));
        assertFalse(state.ready("b", 50_000));
        state.finished("b", 50_000);
        assertFalse(state.ready("b", 52_999));
        assertTrue(state.ready("b", 53_000));
    }
    @Test public void duplicateFinishedDoesNotRestartCountdown() {
        LoginPageReadiness state = new LoginPageReadiness();
        state.started("a"); state.finished("a", 0); state.finished("a", 2_500);
        assertTrue(state.ready("a", 3_000));
    }
    @Test public void errorOrDestroyedPageCannotBeReady() {
        LoginPageReadiness state = new LoginPageReadiness();
        state.started("a"); state.finished("a", 0); state.invalidate();
        assertFalse(state.ready("a", 60_000));
        assertFalse(state.ready(null, 60_000));
    }
}
