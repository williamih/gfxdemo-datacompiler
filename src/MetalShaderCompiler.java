import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String METAL_LIBRARY_FILE = "library.metallib";

    private static final int SHADER_FORMAT_VERSION = 1;

    private boolean runMetal(File inputFile, File outputFile, File diagFile, List<String> macros) throws IOException {
        List<String> args = new ArrayList<String>(Arrays.asList(
            TOOL_METAL, "-emit-llvm", "-c", "-isysroot", SYSROOT, "-ffast-math",
            "-serialize-diagnostics", diagFile.getAbsolutePath(), "-o", outputFile.getAbsolutePath(),
            "-mmacosx-version-min=10.9", "-std=osx-metal1.1"
        ));
        for (String macro : macros) {
            args.add("-D");
            args.add(macro);
        }
        args.add(inputFile.getAbsolutePath());
        Process p = new ProcessBuilder(args).start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            return false;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
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

    private Map<Integer, String> findOptionIfdefs(File file) throws IOException {
        Map<Integer, String> map = new HashMap<Integer, String>();
        Pattern pattern = Pattern.compile("#ifdef\\s+F_(\\d\\d)([\\w_]*)");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                Matcher m = pattern.matcher(line);
                if (!m.matches())
                    continue;
                int index = Integer.parseInt(m.group(1));
                String ifdefName = m.group(2);
                if (index > 63) {
                    System.out.printf("Error: shader flag F_%d%s - index cannot be greater than 63%n", index, ifdefName);
                    return null;
                }
                map.put(index, ifdefName);
            }
        }
        return map;
    }

    private byte[] internalCompileShader(File inputFile, String tempDir, List<String> macros) throws IOException {
        File airFile = new File(tempDir, AIR_FILE);
        File diagFile = new File(tempDir, DIAG_FILE);
        File metalArFile = new File(tempDir, METAL_AR_FILE);
        File metalLibFile = new File(tempDir, METAL_LIBRARY_FILE);

        if (!runMetal(inputFile, airFile, diagFile, macros)) return null;
        if (!diagFile.delete()) return null;

        if (!runMetalAr(airFile, metalArFile)) return null;
        if (!airFile.delete()) return null;

        if (!runMetalLib(metalArFile, metalLibFile)) return null;
        if (!metalArFile.delete()) return null;

        byte[] contents = Files.readAllBytes(metalLibFile.toPath());

        if (!metalLibFile.delete()) return null;

        return contents;
    }

    private void logPermutationCompile(List<String> macros) {
        System.out.print("\tCompiling permutation with options: {");
        if (macros.size() != 0)
            System.out.print(macros.get(0));
        for (int i = 1; i < macros.size(); ++i) {
            System.out.print(", ");
            System.out.print(macros.get(i));
        }
        System.out.println("}");
    }

    static class DescendingPopcountComparator implements Comparator<Integer> {

        @Override
        public int compare(Integer o1, Integer o2) {
            return Integer.bitCount(o2) - Integer.bitCount(o1);
        }

    }

    @Override
    public boolean compile(File inputFile, File outputFile) {
        String tempDir = null;
        try (BinaryWriter writer = new BinaryWriter(outputFile)) {
            tempDir = Files.createTempDirectory(null).toString();

            Map<Integer, String> ifdefs = findOptionIfdefs(inputFile);
            if (ifdefs == null) {
                return false;
            }
            List<Integer> sortedOptionIndices = new ArrayList<Integer>(ifdefs.keySet());
            Collections.sort(sortedOptionIndices);
            int nPermutations = 1 << sortedOptionIndices.size();
            int nOptions = sortedOptionIndices.size();

            writer.write(new char[] {'R', 'D', 'H', 'S'});
            writer.write32(SHADER_FORMAT_VERSION); // version
            writer.write(new char[] {'L', 'T', 'E', 'M' });
            writer.write32(nPermutations);

            List<Integer> numbers = new ArrayList<Integer>();
            for (int i = 0; i < nPermutations; ++i) {
                numbers.add(i);
            }
            Collections.sort(numbers, new DescendingPopcountComparator());

            for (int k = 0; k < nPermutations; ++k) {
                int i = numbers.get(k);
                List<String> macros = new ArrayList<String>();
                long permuteMask = 0;
                for (int j = 0; j < nOptions; ++j) {
                    if ((i & (1 << j)) != 0) {
                        int bitIndex = sortedOptionIndices.get(j);
                        macros.add(String.format("F_%02d%s", bitIndex, ifdefs.get(bitIndex)));
                        permuteMask |= 1 << bitIndex;
                    }
                }
                logPermutationCompile(macros);
                byte[] data = internalCompileShader(inputFile, tempDir, macros);
                if (data == null) {
                    return false;
                }

                long permuteHeaderPos = writer.getFilePointer();
                writer.write64(permuteMask);
                writer.write32(data.length); // vs data length
                writer.write32(0); // ps data length
                long pos_ofsNextPermutation = writer.writeTemp32();
                writer.write32(0); // padding (for alignment purposes)
                writer.write(data);
                writer.align(8); // 8 byte alignment - necessary because the permutation mask is 64-bit
                writer.overwriteTemp32(pos_ofsNextPermutation, (int)(writer.getFilePointer() - permuteHeaderPos));
            }

        } catch (IOException e) {
            return false;
        } finally {
           if (tempDir != null && !new File(tempDir).delete())
                return false;
        }
        return true;
    }
}
