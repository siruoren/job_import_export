package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
import com.siruoren.jobimportexport.engine.model.TreeNode;
import com.siruoren.jobimportexport.engine.tree.TreeBuilder;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipInputStream;

public class ImportEngine {

    private final TreeBuilder treeBuilder = new TreeBuilder();
    private final ExecutionEngine executionEngine = new ExecutionEngine();

    public List<ImportResult> importZip(ZipInputStream zipInputStream, ImportContext ctx) throws IOException {
        TreeNode root = treeBuilder.buildTree(zipInputStream);
        return executionEngine.execute(root, ctx);
    }

    public List<ImportResult> importZip(List<String> zipPaths, ImportContext ctx) {
        TreeNode root = treeBuilder.buildTree(zipPaths);
        return executionEngine.execute(root, ctx);
    }

    public TreeNode buildTree(List<String> zipPaths) {
        return treeBuilder.buildTree(zipPaths);
    }

    public TreeNode buildTree(ZipInputStream zipInputStream) throws IOException {
        return treeBuilder.buildTree(zipInputStream);
    }

    public List<ImportResult> execute(TreeNode root, ImportContext ctx) {
        return executionEngine.execute(root, ctx);
    }
}
