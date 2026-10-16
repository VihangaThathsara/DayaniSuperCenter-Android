package com.nexcentauri.dayanisupercenter;

import com.nexcentauri.dayanisupercenter.util.ConversationSession;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationSessionTest {
    @Test public void existingMessagesRemainVisibleBeforeFirstReset() {
        assertTrue(ConversationSession.includes(null, null));
        assertTrue(ConversationSession.includes("legacy", null));
        assertTrue(ConversationSession.includes(null, "legacy"));
    }

    @Test public void resetHidesOldCustomerMessagesWhileNewRepliesStayVisible() {
        assertFalse(ConversationSession.includes("new-session", null));
        assertFalse(ConversationSession.includes("new-session", "old-session"));
        assertTrue(ConversationSession.includes("new-session", "new-session"));
    }
}
