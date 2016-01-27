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
            } while (('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z'));
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

        public void skipLine() throws IOException {
            while (c != '\n') {
                c = reader.read();
            }
            fetchNextToken();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    };

    @Override
    public boolean compile(File inputFile, File outputFile) {
        List<Vector3> positions = new ArrayList<Vector3>();
        List<Vector3> normals = new ArrayList<Vector3>();
        List<Vector2> texcoords = new ArrayList<Vector2>();
        Map<Vertex, Integer> vertices = new HashMap<Vertex, Integer>();
        List<Integer> indices = new ArrayList<Integer>();

        try (Lexer lexer = new Lexer(inputFile)) {
            while (lexer.hasNext()) {
                String token = lexer.nextToken();
                if (token.equals("v")) {
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
                        Integer index = vertices.get(v);
                        if (index == null) {
                            index = vertices.size();
                            vertices.put(v, index);
                        }
                        indices.add(index);
                    }
                } else {
                    lexer.skipLine();
                }
            }
        } catch (IOException e) {
            return false;
        }

        if (texcoords.isEmpty()) {
            Vector2 uv = new Vector2();
            uv.x = 0.0f;
            uv.y = 0.0f;
            texcoords.add(uv);
        }

        try (BinaryWriter writer = new BinaryWriter(outputFile)) {
            writer.write(new char[] {'M', 'O', 'D', 'L'});
            writer.write32(0); // version
            writer.write32(vertices.size()); // nVertices
            writer.write32(indices.size()); // nIndices

            List<Map.Entry<Vertex, Integer>> sortedVertices
                = new ArrayList<Entry<Vertex, Integer>>(vertices.entrySet());
            Collections.sort(sortedVertices,
                             (Map.Entry<Vertex, Integer> e1, Map.Entry<Vertex, Integer> e2) -> {
                return e1.getValue() - e2.getValue();
            });
            for (Map.Entry<Vertex, Integer> e : sortedVertices) {
                Vertex v = e.getKey();
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

            for (int index : indices) {
                writer.write32(index);
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
