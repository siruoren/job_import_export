package com.siruoren.jobimportexport.engine.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExportResultTest {

    @Test
    void testConstructor_SetsJobPathAndFullPath() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", "Success");

        assertEquals("job1", result.jobPath);
        assertEquals("folder/job1", result.fullPath);
    }

    @Test
    void testConstructor_SetsStatusCode() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", "Success");

        assertEquals("EXPORTED", result.statusCode);
    }

    @Test
    void testConstructor_SetsLocalizedStatus() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", "Success");

        assertNotNull(result.status);
        assertFalse(result.status.isEmpty());
        assertNotEquals("EXPORTED", result.status);
    }

    @Test
    void testConstructor_SetsMessage() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", "Export successful");

        assertEquals("Export successful", result.message);
    }

    @Test
    void testConstructor_WithExportedStatus_SetsSuccessTrue() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", "Success");

        assertTrue(result.success);
        assertFalse(result.skipped);
    }

    @Test
    void testConstructor_WithSkippedStatus_SetsSkippedTrue() {
        ExportResult result = new ExportResult("job1", "folder/job1", "SKIPPED", "Already exists");

        assertFalse(result.success);
        assertTrue(result.skipped);
    }

    @Test
    void testConstructor_WithErrorStatus_SetsSuccessFalse() {
        ExportResult result = new ExportResult("job1", "folder/job1", "ERROR", "Failed");

        assertFalse(result.success);
        assertFalse(result.skipped);
    }

    @Test
    void testConstructor_WithUnknownStatus_SetsSuccessFalse() {
        ExportResult result = new ExportResult("job1", "folder/job1", "UNKNOWN", "Unknown status");

        assertFalse(result.success);
        assertFalse(result.skipped);
    }

    @Test
    void testConstructor_StatusIsLocalized() {
        ExportResult exported = new ExportResult("job1", "folder/job1", "EXPORTED", "msg");
        ExportResult skipped = new ExportResult("job2", "folder/job2", "SKIPPED", "msg");
        ExportResult error = new ExportResult("job3", "folder/job3", "ERROR", "msg");

        assertNotEquals(exported.status, skipped.status);
        assertNotEquals(skipped.status, error.status);
        assertNotEquals(exported.status, error.status);
    }

    @Test
    void testConstructor_WithNullMessage() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", null);

        assertEquals("EXPORTED", result.statusCode);
        assertNotNull(result.status);
        assertNull(result.message);
    }

    @Test
    void testConstructor_WithEmptyMessage() {
        ExportResult result = new ExportResult("job1", "folder/job1", "EXPORTED", "");

        assertEquals("", result.message);
    }

    @Test
    void testStatusCode_EqualsOriginalCode() {
        String code = "EXPORTED";
        ExportResult result = new ExportResult("job", "path", code, "msg");

        assertEquals(code, result.statusCode);
    }

    @Test
    void testStatus_NotEqualToStatusCode() {
        ExportResult result = new ExportResult("job", "path", "EXPORTED", "msg");

        assertNotEquals(result.status, result.statusCode);
    }

    @Test
    void testConstructor_WithSummaryStatus() {
        ExportResult result = new ExportResult("", "", "SUMMARY", "3 exported, 1 skipped, 0 errors");

        assertEquals("SUMMARY", result.statusCode);
        assertNotNull(result.status);
        assertEquals("3 exported, 1 skipped, 0 errors", result.message);
    }
}
