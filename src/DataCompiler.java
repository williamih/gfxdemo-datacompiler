import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

public final class DataCompiler {
    static class CompilerInfo {
        public CompilerInfo(AssetCompiler compiler, List<String> outputFilePatterns) {
            this.compiler = compiler;
            this.outputFilePatterns = outputFilePatterns;
        }

        AssetCompiler compiler;
        List<String> outputFilePatterns;
    }

    private static void printCompileFailureMessage(String filename, List<String> outputFilenames) {
        System.out.printf("Failed to compile %s (", filename);
        if (outputFilenames.size() == 1) {
            System.out.printf("output filename: %s", outputFilenames.get(0));
        } else {
            System.out.printf("output filenames: {");
            System.out.print(outputFilenames.get(0));
            for (int i = 1; i < outputFilenames.size(); ++i) {
                System.out.printf(", %s", outputFilenames.get(i));
            }
            System.out.print("}");
        }
        System.out.println(")");
    }

    private static boolean compile(String filename, Map<Pattern, CompilerInfo> map) {
        for (Pattern pattern : map.keySet()) {
            Matcher m = pattern.matcher(filename);
            if (!m.matches())
                continue;

            CompilerInfo info = map.get(pattern);
            if (info.outputFilePatterns.isEmpty()) {
                System.out.printf("Warning: no outputs specified for file %s; ignoring file.%n", filename);
                continue;
            }

            List<File> outputFiles = new ArrayList<File>();
            List<String> outputFilenames = new ArrayList<String>();
            for (String outputFilePattern : info.outputFilePatterns) {
                String outputFilename = m.replaceFirst(outputFilePattern);
                outputFiles.add(new File(outputFilename));
                outputFilenames.add(outputFilename);
            }

            System.out.printf("Compiling %s...%n", filename);
            if (!info.compiler.compile(new File(filename), outputFiles)) {
                printCompileFailureMessage(filename, outputFilenames);
                return false;
            }
            return true;
        }
        System.out.printf("Warning: %s listed in manifest but no compiler found%n", filename);
        return true;
    }

    private static void addRule(Map<Pattern, CompilerInfo> map,
                                String inputPattern,
                                AssetCompiler compiler,
                                Object... outputPatterns) {
        List<String> outputFilePatternsList = new ArrayList<String>();
        for (Object obj : outputPatterns) {
            if (!(obj instanceof String)) {
                throw new IllegalArgumentException();
            }
            outputFilePatternsList.add((String)obj);
        }
        CompilerInfo compilerInfo = new CompilerInfo(compiler, outputFilePatternsList);
        map.put(Pattern.compile(inputPattern), compilerInfo);
    }

    public static void main(String[] args) {
        Map<Pattern, CompilerInfo> map = new HashMap<Pattern, CompilerInfo>();
        addRule(map, "(.*)\\.obj", new ObjCompiler(), "Assets/$1.mdl", "Assets/$1.mdg");
        addRule(map, "(.*)\\.metal", new MetalShaderCompiler(), "Assets/$1_MTL.shd");

        for (String manifestFilename : args) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(manifestFilename));
            } catch (FileNotFoundException e) {
                System.out.println("Failed to open manifest file " + manifestFilename);
                continue;
            }
            try {
                String filename;
                while ((filename = reader.readLine()) != null) {
                    if (filename.trim() == "")
                        continue;
                    compile(filename, map);
                }
            } catch (IOException e) {
                System.out.println("I/O error while processing manifest file " + manifestFilename);
            }
        }
    }
}
