import java.util.*;
import java.util.Map.Entry;
import java.io.*;

public class ObjCompiler implements AssetCompiler {
    static class Vector2 {
        float x;
        float y;
    }

    static class Vector3 {
        float x;
        float y;
        float z;
    }

    static class Vertex {
        int idxPosition;
        int idxNormal;
        int idxTexCoord;

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Vertex)) return false;
            Vertex v = (Vertex)other;
            return idxPosition == v.idxPosition &&
                   idxNormal == v.idxNormal &&
                   idxTexCoord == v.idxTexCoord;
        }

        @Override
        public int hashCode() {
            return Objects.hash(idxPosition, idxNormal, idxTexCoord);
        }
    }

    static class Lexer implements Closeable {
        static enum TokenType {
            STRING,
            INTEGER,
            FLOAT,
            SYMBOL,
        };

        private BufferedReader reader;
        private StringBuilder stringBuilder;
        private TokenType tokenType;
        private String currentToken;
        private int c;

        private void parseString() throws IOException {
            tokenType = TokenType.STRING;
            do {
                stringBuilder.append((char)c);
                c = reader.read();
            } while (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || c == '_');
        }

        private void parseNumber(boolean isNegative) throws IOException {
            if (isNegative) {
                stringBuilder.append('-');
            }
            do {
                stringBuilder.append((char)c);
                c = reader.read();
            } while ('0' <= c && c <= '9');
            if (c == '.') {
                tokenType = TokenType.FLOAT;
                do {
                    stringBuilder.append((char)c);
                    c = reader.read();
                } while ('0' <= c && c <= '9');
            } else {
                tokenType = TokenType.INTEGER;
            }
        }

        private void parseSymbol() throws IOException {
            tokenType = TokenType.SYMBOL;
            stringBuilder.append((char)c);
            c = reader.read();
        }

        private void skipWhitespace() throws IOException {
            while (c != -1 && Character.isWhitespace(c)) {
                c = reader.read();
            }
        }

        private void skipCommentsAndWhitespace() throws IOException {
            skipWhitespace();
            while (c == '#') {
                while (c != -1 && c != '\n') {
                    c = reader.read();
                }
                skipWhitespace();
            }
        }

        private void fetchNextToken() throws IOException {
            skipCommentsAndWhitespace();
            if (c == -1) { // end of file
                currentToken = null;
                tokenType = null;
                return;
            }
            if (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')) {
                parseString();
            }
            else if ('0' <= c && c <= '9') {
                parseNumber(false);
            }
            else if (c == '-') {
                c = reader.read();
                if ('0' <= c && c <= '9') {
                    parseNumber(true);
                } else {
                    // Don't want to use parseSymbol() here as we've already read the next character.
                    tokenType = TokenType.SYMBOL;
                    stringBuilder.append('-');
                }
            }
            else {
                parseSymbol();
            }
            currentToken = stringBuilder.toString();
            stringBuilder.setLength(0);
        }

        public Lexer(File file) throws IOException {
            reader = new BufferedReader(new FileReader(file));
            stringBuilder = new StringBuilder();
            c = reader.read();
            fetchNextToken();
        }

        public boolean hasNext() {
            return currentToken != null;
        }

        public String nextToken() throws IOException {
            String s = currentToken;
            fetchNextToken();
            return s;
        }

        public int nextInt() throws IOException {
            if (tokenType != TokenType.INTEGER) {
                throw new InputMismatchException();
            }
            int i = Integer.parseInt(currentToken);
            fetchNextToken();
            return i;
        }

        public float nextFloat() throws IOException {
            if (tokenType != TokenType.FLOAT) {
                throw new InputMismatchException();
            }
            float f = Float.parseFloat(currentToken);
            fetchNextToken();
            return f;
        }

        public String nextSymbol() throws IOException {
            if (tokenType != TokenType.SYMBOL) {
                throw new InputMismatchException();
            }
            String s = currentToken;
            fetchNextToken();
            return s;
        }

        public boolean hasNext(String s) {
            return currentToken.equals(s);
        }

        public String readToNextLine() throws IOException {
            stringBuilder.append(currentToken);
            while (c != '\n' && c != '\r') {
                stringBuilder.append((char)c);
                c = reader.read();
            }
            String s = stringBuilder.toString();
            stringBuilder.setLength(0);
            fetchNextToken();
            return s;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    };

    static class IndexedPool<E> {
        private Map<E, Integer> map = new HashMap<E, Integer>();

        public int indexFor(E obj) {
            Integer index = map.get(obj);
            if (index != null)
                return index;
            index = map.size();
            map.put(obj, index);
            return index;
        }

        public int size() {
            return map.size();
        }

        public List<E> convertToList() {
            List<Map.Entry<E, Integer>> sorted = new ArrayList<Entry<E, Integer>>(map.entrySet());
            Collections.sort(sorted, (Map.Entry<E, Integer> e1, Map.Entry<E, Integer> e2) -> {
                return e1.getValue() - e2.getValue();
            });
            List<E> result = new ArrayList<E>();
            for (Map.Entry<E, Integer> entry : sorted) {
                result.add(entry.getKey());
            }
            return result;
        }
    };

    static class Material {
        public String diffuseTexture = null;
    };

    static class Submesh {
        public int indexStart = 0;
        public int indexCount = 0;
        public long diffuseTextureIndex = 0xFFFFFFFFFFFFFFFFL;
    };

    private Map<String, Material> parseMaterialFile(File materialFile) throws IOException {
        Map<String, Material> map = new HashMap<String, Material>();
        Material currentMaterial = null;
        try (Lexer lexer = new Lexer(materialFile)) {
            while (lexer.hasNext()) {
                String token = lexer.nextToken();
                if (token.equals("newmtl")) {
                    String name = lexer.readToNextLine();
                    currentMaterial = new Material();
                    map.put(name, currentMaterial);
                }
                else if (token.equals("map_Kd")) {
                    if (currentMaterial == null) {
                        System.out.println("Material (.mtl) file invalid: material name not specified");
                        return null;
                    }
                    currentMaterial.diffuseTexture = lexer.readToNextLine();
                } else {
                    lexer.readToNextLine();
                }
            }
        }
        return map;
    }

    private void writeMDLFile(BinaryWriter writer, List<Submesh> submeshes) throws IOException {
        writer.write(new char[] {'M', 'O', 'D', 'L'});
        writer.write32(0); // version
        writer.write32(submeshes.size()); // nSubmeshes
        long ofsSubmeshesPos = writer.writeTemp32();

        writer.overwriteTemp32(ofsSubmeshesPos, (int)writer.getFilePointer());
        for (Submesh s : submeshes) {
            writer.write32(s.indexStart); // indexStart
            writer.write32(s.indexCount); // indexCount
            writer.write64(s.diffuseTextureIndex);
        }
    }

    private void writeMDGFile(BinaryWriter writer,
                              List<Vector3> positions,
                              List<Vector3> normals,
                              List<Vector2> texcoords,
                              List<Vertex> vertices,
                              List<Integer> indices,
                              List<String> textures) throws IOException {
        writer.write(new char[] {'M', 'D', 'L', 'G'});
        writer.write32(vertices.size()); // nVertices
        long ofsVerticesPos = writer.writeTemp32(); // ofsVertices
        writer.write32(indices.size()); // nIndices
        long ofsIndicesPos = writer.writeTemp32(); // ofsIndices
        writer.write32(textures.size()); // nTextures
        long ofsTexturesPos = writer.writeTemp32(); // ofsTextures

        long texturesPos = writer.getFilePointer();
        writer.overwriteTemp32(ofsTexturesPos, (int)texturesPos);
        for (String s : textures) {
            writer.write32(s.length()); // lenFilename
            writer.writeTemp32(); // ofsFilename
        }
        for (int i = 0; i < textures.size(); ++i) {
            writer.overwriteTemp32(texturesPos + 8*i + 4, (int)writer.getFilePointer());
            writer.write(textures.get(i).getBytes());
            writer.write((byte)0);
        }
        writer.align(4);

        writer.overwriteTemp32(ofsVerticesPos, (int)writer.getFilePointer());
        for (Vertex v : vertices) {
            Vector3 pos = positions.get(v.idxPosition - 1);
            Vector3 normal = normals.get(v.idxNormal - 1);
            Vector2 texcoord = texcoords.get(v.idxTexCoord - 1);
            writer.writeFloat(pos.x);
            writer.writeFloat(pos.y);
            writer.writeFloat(pos.z);
            writer.writeFloat(normal.x);
            writer.writeFloat(normal.y);
            writer.writeFloat(normal.z);
            writer.writeFloat(texcoord.x);
            writer.writeFloat(texcoord.y);
        }

        writer.overwriteTemp32(ofsIndicesPos, (int)writer.getFilePointer());
        for (int index : indices) {
            writer.write32(index);
        }
    }

    @Override
    public boolean compile(File inputFile, List<File> outputFiles) {
        List<Vector3> positions = new ArrayList<Vector3>();
        List<Vector3> normals = new ArrayList<Vector3>();
        List<Vector2> texcoords = new ArrayList<Vector2>();
        IndexedPool<Vertex> vertices = new IndexedPool<Vertex>();
        List<Integer> indices = new ArrayList<Integer>();

        Map<String, Material> materials = null;

        List<Submesh> submeshes = new ArrayList<Submesh>();
        Submesh currentSubmesh = null;
        IndexedPool<String> texturePaths = new IndexedPool<String>();

        try (Lexer lexer = new Lexer(inputFile)) {
            while (lexer.hasNext()) {
                String token = lexer.nextToken();
                if (token.equals("mtllib")) {
                    String filename = lexer.readToNextLine();
                    File f = new File(inputFile.getParentFile(), filename);
                    if (f.exists())
                        materials = parseMaterialFile(f);
                }
                else if (token.equals("usemtl") && materials != null) {
                    String materialName = lexer.readToNextLine();
                    Material material = materials.get(materialName);
                    if (material == null) {
                        System.out.printf("Material '%s' does not exist in .mtl file%n", materialName);
                        return false;
                    }

                    if (currentSubmesh != null) {
                        currentSubmesh.indexCount = indices.size() - currentSubmesh.indexStart;
                    }

                    currentSubmesh = new Submesh();
                    currentSubmesh.indexStart = indices.size();
                    if (material.diffuseTexture != null) {
                        currentSubmesh.diffuseTextureIndex = texturePaths.indexFor(material.diffuseTexture);
                    }
                    submeshes.add(currentSubmesh);
                }
                else if (token.equals("v")) {
                    Vector3 v = new Vector3();
                    v.x = lexer.nextFloat();
                    v.y = lexer.nextFloat();
                    v.z = lexer.nextFloat();
                    positions.add(v);
                }
                else if (token.equals("vn")) {
                    Vector3 v = new Vector3();
                    v.x = lexer.nextFloat();
                    v.y = lexer.nextFloat();
                    v.z = lexer.nextFloat();
                    normals.add(v);
                }
                else if (token.equals("vt")) {
                    Vector2 uv = new Vector2();
                    uv.x = lexer.nextFloat();
                    uv.y = lexer.nextFloat();
                    texcoords.add(uv);
                }
                else if (token.equals("f")) {
                    if (currentSubmesh == null) {
                        currentSubmesh = new Submesh();
                        currentSubmesh.indexStart = indices.size();
                        submeshes.add(currentSubmesh);
                    }
                    for (int i = 0; i < 3; ++i) {
                        int posIdx = lexer.nextInt();
                        int normalIdx = Integer.MAX_VALUE;
                        int texCoordIdx = Integer.MAX_VALUE;
                        if (lexer.hasNext("/")) {
                            lexer.nextToken();
                            if (lexer.hasNext("/")) {
                                lexer.nextToken();
                                normalIdx = lexer.nextInt();
                            } else {
                                texCoordIdx = lexer.nextInt();
                                if (lexer.hasNext("/")) {
                                    lexer.nextToken();
                                    normalIdx = lexer.nextInt();
                                }
                            }
                        }
                        if (normalIdx == Integer.MAX_VALUE) {
                            System.out.println("Error: obj file doesn't have normals");
                            return false;
                        }
                        if (texCoordIdx == Integer.MAX_VALUE) {
                            texCoordIdx = 1; // use first texcoord
                        }
                        Vertex v = new Vertex();
                        v.idxPosition = posIdx;
                        v.idxNormal = normalIdx;
                        v.idxTexCoord = texCoordIdx;
                        int index = vertices.indexFor(v);
                        indices.add(index);
                    }
                } else {
                    lexer.readToNextLine();
                }
            }
        } catch (IOException e) {
            return false;
        }

        if (currentSubmesh != null) {
            currentSubmesh.indexCount = indices.size() - currentSubmesh.indexStart;
        }

        if (texcoords.isEmpty()) {
            Vector2 uv = new Vector2();
            uv.x = 0.0f;
            uv.y = 0.0f;
            texcoords.add(uv);
        }

        try (BinaryWriter mdlFileWriter = new BinaryWriter(outputFiles.get(0));
             BinaryWriter mdgFileWriter = new BinaryWriter(outputFiles.get(1))) {
            writeMDLFile(mdlFileWriter, submeshes);
            writeMDGFile(
                mdgFileWriter,
                positions,
                normals,
                texcoords,
                vertices.convertToList(),
                indices,
                texturePaths.convertToList()
            );
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
