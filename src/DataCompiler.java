import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

public final class DataCompiler {
    static class CompilerInfo {
        public CompilerInfo(AssetCompiler compiler, String replacementString) {
            this.compiler = compiler;
            this.replacementString = replacementString;
        }

        AssetCompiler compiler;
        String replacementString;
    }

    private static boolean compile(String filename, Map<Pattern, CompilerInfo> map) {
        for (Pattern pattern : map.keySet()) {
            Matcher m = pattern.matcher(filename);
            if (!m.matches())
                continue;
            CompilerInfo info = map.get(pattern);
            String outputFilename = m.replaceFirst(info.replacementString);
            System.out.printf("Compiling %s...\n", filename);
            if (!info.compiler.compile(new File(filename), new File(outputFilename))) {
                System.out.printf("Failed to compile %s (output filename: %s)\n", filename, outputFilename);
                return false;
            }
            return true;
        }
        System.out.printf("Warning: %s listed in manifest but no compiler found\n", filename);
        return true;
    }

    public static void main(String[] args) {
        Map<Pattern, CompilerInfo> map = new HashMap<Pattern, CompilerInfo>();
        map.put(Pattern.compile("(.*)\\.obj"), new CompilerInfo(new ObjCompiler(), "Assets/$1.mdl"));
        map.put(Pattern.compile("(.*)\\.metal"), new CompilerInfo(new MetalShaderCompiler(), "Assets/$1_MTL.shd"));

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
