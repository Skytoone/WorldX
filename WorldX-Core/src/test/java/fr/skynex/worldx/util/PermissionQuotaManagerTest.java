package fr.skynex.worldx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PermissionQuotaManagerTest {

    @Test
    @DisplayName("Test fallback values when player is null")
    public void testNullPlayerFallbacks() {
        assertEquals(3, PermissionQuotaManager.getMaxClaims(null, null));
        assertEquals(50000, PermissionQuotaManager.getMaxBlocksPerClaim(null, null));
        assertEquals(10000, PermissionQuotaManager.getEditQuota(null, null));
        assertEquals(20, PermissionQuotaManager.getMaxHistorySize(null, null));
    }
}
