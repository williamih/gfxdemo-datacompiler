import java.io.File;
import java.util.List;

public interface AssetCompiler {
    boolean compile(File inputFile, List<File> outputFiles);
}
