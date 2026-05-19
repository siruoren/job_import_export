package com.siruoren.jobimportexport.engine.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatusUtilTest {

    @Test
    void testGetLocalizedStatus_WithErrorCode() {
        String result = StatusUtil.getLocalizedStatus("ERROR");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithSkipExistsCode() {
        String result = StatusUtil.getLocalizedStatus("SKIP_EXISTS");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithCreateJobCode() {
        String result = StatusUtil.getLocalizedStatus("CREATE_JOB");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithOverwriteJobCode() {
        String result = StatusUtil.getLocalizedStatus("OVERWRITE_JOB");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithRenameJobCode() {
        String result = StatusUtil.getLocalizedStatus("RENAME_JOB");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithUpdateConfigCode() {
        String result = StatusUtil.getLocalizedStatus("UPDATE_CONFIG");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithBlockedCode() {
        String result = StatusUtil.getLocalizedStatus("BLOCKED");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithConflictCode() {
        String result = StatusUtil.getLocalizedStatus("CONFLICT");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithOkCode() {
        String result = StatusUtil.getLocalizedStatus("OK");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithExportedCode() {
        String result = StatusUtil.getLocalizedStatus("EXPORTED");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithSkippedCode() {
        String result = StatusUtil.getLocalizedStatus("SKIPPED");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithErrorInvalidNameCode() {
        String result = StatusUtil.getLocalizedStatus("ERROR_INVALID_NAME");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithErrorPluginCode() {
        String result = StatusUtil.getLocalizedStatus("ERROR_PLUGIN");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithReuseCode() {
        String result = StatusUtil.getLocalizedStatus("REUSE");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithSkipFolderMissingCode() {
        String result = StatusUtil.getLocalizedStatus("SKIP_FOLDER_MISSING");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithNull() {
        String result = StatusUtil.getLocalizedStatus((String) null);
        assertNull(result);
    }

    @Test
    void testGetLocalizedStatus_WithEmpty() {
        String result = StatusUtil.getLocalizedStatus("");
        assertEquals("", result);
    }

    @Test
    void testGetLocalizedStatus_WithUnknownCode() {
        String result = StatusUtil.getLocalizedStatus("UNKNOWN_STATUS");
        assertEquals("UNKNOWN_STATUS", result);
    }

    @Test
    void testGetLocalizedStatus_WithStatusEnum() {
        String result = StatusUtil.getLocalizedStatus(Status.ERROR);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetLocalizedStatus_WithNullStatusEnum() {
        String result = StatusUtil.getLocalizedStatus((Status) null);
        assertEquals("", result);
    }

    @Test
    void testGetLocalizedStatus_DifferentStatuses_ReturnDifferentValues() {
        String error = StatusUtil.getLocalizedStatus("ERROR");
        String skipExists = StatusUtil.getLocalizedStatus("SKIP_EXISTS");
        String createJob = StatusUtil.getLocalizedStatus("CREATE_JOB");

        assertNotEquals(error, skipExists);
        assertNotEquals(skipExists, createJob);
        assertNotEquals(error, createJob);
    }
}
