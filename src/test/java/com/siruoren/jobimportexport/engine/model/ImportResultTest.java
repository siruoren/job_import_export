package com.siruoren.jobimportexport.engine.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportResultTest {

    @Test
    void testSetStatusEnum_SetsBothFields() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.CREATE_JOB);

        assertEquals(Status.CREATE_JOB, result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }

    @Test
    void testSetStatusEnum_WithError() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.ERROR);

        assertEquals(Status.ERROR, result.statusEnum);
        assertNotNull(result.status);
    }

    @Test
    void testSetStatusEnum_WithSkipExists() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.SKIP_EXISTS);

        assertEquals(Status.SKIP_EXISTS, result.statusEnum);
        assertNotNull(result.status);
    }

    @Test
    void testSetStatusEnum_WithBlocked() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.BLOCKED);

        assertEquals(Status.BLOCKED, result.statusEnum);
        assertNotNull(result.status);
    }

    @Test
    void testSetStatusEnum_WithOverwriteJob() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.OVERWRITE_JOB);

        assertEquals(Status.OVERWRITE_JOB, result.statusEnum);
        assertNotNull(result.status);
    }

    @Test
    void testSetStatusEnum_WithRenameJob() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.RENAME_JOB);

        assertEquals(Status.RENAME_JOB, result.statusEnum);
        assertNotNull(result.status);
    }

    @Test
    void testSetStatusEnum_WithNull() {
        ImportResult result = new ImportResult("testJob");
        result.setStatusEnum(Status.ERROR);

        result.setStatusEnum(null);

        assertNull(result.statusEnum);
    }

    @Test
    void testSetStatus_SetsBothFields() {
        ImportResult result = new ImportResult("testJob");

        result.setStatus("CREATE_JOB");

        assertEquals(Status.CREATE_JOB, result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }

    @Test
    void testSetStatus_WithErrorInvalidName_NotInEnum() {
        ImportResult result = new ImportResult("testJob");

        result.setStatus("ERROR_INVALID_NAME");

        assertNull(result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }

    @Test
    void testSetStatus_WithErrorPlugin_NotInEnum() {
        ImportResult result = new ImportResult("testJob");

        result.setStatus("ERROR_PLUGIN");

        assertNull(result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }

    @Test
    void testSetStatus_WithReuse_NotInEnum() {
        ImportResult result = new ImportResult("testJob");

        result.setStatus("REUSE");

        assertNull(result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }

    @Test
    void testSetStatus_WithSkipFolderMissing_NotInEnum() {
        ImportResult result = new ImportResult("testJob");

        result.setStatus("SKIP_FOLDER_MISSING");

        assertNull(result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }

    @Test
    void testSetStatusEnumAndMessage() {
        ImportResult result = new ImportResult("testJob");
        String message = "Custom error message";

        result.setStatusEnumAndMessage(Status.ERROR, message);

        assertEquals(Status.ERROR, result.statusEnum);
        assertEquals(message, result.message);
    }

    @Test
    void testSetStatusEnum_OverwritesPreviousStatus() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.CREATE_JOB);
        String firstStatus = result.status;

        result.setStatusEnum(Status.ERROR);
        String secondStatus = result.status;

        assertNotEquals(firstStatus, secondStatus);
    }

    @Test
    void testConstructor_InitializesDefaults() {
        ImportResult result = new ImportResult("testJob");

        assertEquals("testJob", result.jobName);
        assertEquals("", result.folderPath);
        assertEquals("testJob", result.finalName);
        assertNotNull(result.missingPlugins);
        assertTrue(result.missingPlugins.isEmpty());
    }

    @Test
    void testConstructor_WithFolderPath() {
        ImportResult result = new ImportResult("testJob", "folder/path");

        assertEquals("testJob", result.jobName);
        assertEquals("folder/path", result.folderPath);
        assertEquals("testJob", result.finalName);
    }

    @Test
    void testSetStatus_WithUnknownCode_StillHasStatus() {
        ImportResult result = new ImportResult("testJob");

        result.setStatus("UNKNOWN_STATUS");

        assertNull(result.statusEnum);
        assertEquals("UNKNOWN_STATUS", result.status);
    }

    @Test
    void testStatusAndStatusEnum_AreConsistent() {
        ImportResult result = new ImportResult("testJob");

        result.setStatusEnum(Status.OK);

        assertEquals(Status.OK, result.statusEnum);
        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
    }
}
