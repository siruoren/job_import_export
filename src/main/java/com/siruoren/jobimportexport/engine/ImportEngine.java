package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
import com.siruoren.jobimportexport.engine.model.Node;
import com.siruoren.jobimportexport.engine.tree.ZipTreeBuilder;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

public class ImportEngine {

    private final ZipTreeBuilder builder = new ZipTreeBuilder();
    private final ExecutionEngine engine = new ExecutionEngine();

    public List<ImportResult> importZip(ZipInputStream zipInputStream, ImportContext ctx) throws IOException {
        Node root = builder.build(zipInputStream);
        return engine.execute(root, "", ctx);
    }

    public List<ImportResult> importZip(List<String> zipPaths, ImportContext ctx) {
        Node root = builder.build(zipPaths);
        return engine.execute(root, "", ctx);
    }
}