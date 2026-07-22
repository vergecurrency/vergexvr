package com.vergepay.wallet.tor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TorConnectionAttemptPlanTest {
    @Test
    public void advancesThroughEveryFallbackInOrder() {
        TorConnectionAttemptPlan plan = new TorConnectionAttemptPlan();

        assertTrue(plan.isDirect());
        assertNull(plan.getCurrentTransport());
        assertEquals("obfs4", plan.advance());
        assertFalse(plan.isDirect());
        assertEquals("snowflake", plan.advance());
        assertEquals("meek", plan.advance());
        assertNull(plan.advance());
        assertEquals("meek", plan.getCurrentTransport());
    }

    @Test
    public void resetReturnsToDirectConnection() {
        TorConnectionAttemptPlan plan = new TorConnectionAttemptPlan();
        plan.advance();
        plan.advance();

        plan.reset();

        assertTrue(plan.isDirect());
        assertNull(plan.getCurrentTransport());
    }

    @Test
    public void choosesSnowflakeSpecificPluginDeclaration() {
        assertEquals("lyrebird", TorConnectionAttemptPlan.pluginNameFor("obfs4"));
        assertEquals("snowflake", TorConnectionAttemptPlan.pluginNameFor("snowflake"));
        assertEquals("lyrebird", TorConnectionAttemptPlan.pluginNameFor("meek"));
    }
}
