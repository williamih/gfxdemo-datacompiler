import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class MetalShaderCompiler implements AssetCompiler {
    private static final String TOOL_METAL =
            "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/usr/bin/metal";

    private static final String TOOL_METAL_AR =
            "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/usr/bin/metal-ar";

    private static final String TOOL_METALLIB =
            "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/usr/bin/metallib";

    private static final String SYSROOT =
            "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.11.sdk";

    private static final String AIR_FILE = "out.air";
    private static final String DIAG_FILE = "diag.dia";
    private static final String METAL_AR_FILE = "out.metal-ar";

    private boolean runMetal(File inputFile, File outputFile, File diagFile) throws IOException {
        Process p = new ProcessBuilder(
                TOOL_METAL, "-emit-llvm", "-c", "-isysroot", SYSROOT, "-ffast-math",
                "-serialize-diagnostics", diagFile.getAbsolutePath(), "-o", outputFile.getAbsolutePath(),
                "-mmacosx-version-min=10.9", "-std=osx-metal1.1", inputFile.getAbsolutePath()
                ).start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    private boolean runMetalAr(File inputFile, File outputFile) throws IOException {
        Process p = new ProcessBuilder(
                TOOL_METAL_AR, "r", outputFile.getAbsolutePath(), inputFile.getAbsolutePath()
                ).start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    private boolean runMetalLib(File inputFile, File outputFile) throws IOException {
        Process p = new ProcessBuilder(
                TOOL_METALLIB, "-o", outputFile.getAbsolutePath(), inputFile.getAbsolutePath()
                ).start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    public boolean compile(File inputFile, File outputFile) {
        try {
            String tempDir = Files.createTempDirectory(null).toString();

            File airFile = new File(tempDir, AIR_FILE);
            File diagFile = new File(tempDir, DIAG_FILE);
            File metalArFile = new File(tempDir, METAL_AR_FILE);

            if (!runMetal(inputFile, airFile, diagFile)) return false;
            if (!diagFile.delete()) return false;

            if (!runMetalAr(airFile, metalArFile)) return false;
            if (!airFile.delete()) return false;

            if (!runMetalLib(metalArFile, outputFile)) return false;
            if (!metalArFile.delete()) return false;

            if (!new File(tempDir).delete())
                return false;

            return true;

        } catch (IOException e) {
            return false;
        }
    }
}
