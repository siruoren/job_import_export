package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.DiffResult;
import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;

import java.util.ArrayList;
import java.util.List;

public class PreviewEngine {

    public List<DiffResult> preview(List<String> zipPaths, ImportContext ctx) {
        ctx.dryRun = true;

        ImportEngine engine = new ImportEngine();
        List<ImportResult> results = engine.importZip(zipPaths, ctx);

        return collectDiff(results, ctx);
    }

    private List<DiffResult> collectDiff(List<ImportResult> results, ImportContext ctx) {
        List<DiffResult> diffs = new ArrayList<>();

        for (ImportResult result : results) {
            DiffResult diff = new DiffResult();
            diff.sourcePath = result.sourcePath;
            diff.targetPath = result.finalName;
            diff.status = result.status;
            diff.message = result.message;
            diff.missingPlugins = result.missingPlugins;

            diffs.add(diff);
        }

        return diffs;
    }
}