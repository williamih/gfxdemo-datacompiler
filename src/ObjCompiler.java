import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.io.*;

public class ObjCompiler implements AssetCompiler {
    static class Vector3 {
        float x;
        float y;
        float z;
    }

    static class Vertex {
        int idxPosition;
        int idxNormal;

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Vertex)) return false;
            Vertex v = (Vertex)other;
            return idxPosition == v.idxPosition && idxNormal == v.idxNormal;
        }

        @Override
        public int hashCode() {
            return Objects.hash(idxPosition, idxNormal);
        }
    }

    @Override
    public boolean compile(File inputFile, File outputFile) {
        List<Vector3> positions = new ArrayList<Vector3>();
        List<Vector3> normals = new ArrayList<Vector3>();
        Map<Vertex, Integer> vertices = new HashMap<Vertex, Integer>();
        List<Integer> indices = new ArrayList<Integer>();

        try (Scanner scanner = new Scanner(inputFile)) {
            while (scanner.hasNext()) {
                if (scanner.hasNext("#")) {
                    scanner.nextLine(); // skip comment
                }
                else if (scanner.hasNext("v")) {
                    scanner.next();
                    Vector3 v = new Vector3();
                    v.x = scanner.nextFloat();
                    v.y = scanner.nextFloat();
                    v.z = scanner.nextFloat();
                    positions.add(v);
                    scanner.nextLine();
                }
                else if (scanner.hasNext("vn")) {
                    scanner.next();
                    Vector3 v = new Vector3();
                    v.x = scanner.nextFloat();
                    v.y = scanner.nextFloat();
                    v.z = scanner.nextFloat();
                    normals.add(v);
                }
                else if (scanner.hasNext("f")) {
                    scanner.next();
                    for (int i = 0; i < 3; ++i) {
                        Pattern delim = scanner.delimiter();
                        scanner.useDelimiter("(\\p{javaWhitespace}+)|(/)");
                        int posIdx = scanner.nextInt();
                        int normalIdx = Integer.MAX_VALUE;
                        if (scanner.hasNextInt()) {
                            normalIdx = scanner.nextInt();
                            if (scanner.hasNextInt()) {
                                normalIdx = scanner.nextInt();
                            }
                        }
                        scanner.useDelimiter(delim);
                        if (normalIdx == Integer.MAX_VALUE) {
                            System.out.println("Error: obj file doesn't have normals");
                            return false;
                        }
                        Vertex v = new Vertex();
                        v.idxPosition = posIdx;
                        v.idxNormal = normalIdx;
                        Integer index = vertices.get(v);
                        if (index == null) {
                            index = vertices.size();
                            vertices.put(v, index);
                        }
                        indices.add(index);
                    }
                } else {
                    scanner.nextLine();
                }
            }
        } catch (FileNotFoundException e) {
            return false;
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
                writer.writeFloat(pos.x);
                writer.writeFloat(pos.y);
                writer.writeFloat(pos.z);
                writer.writeFloat(normal.x);
                writer.writeFloat(normal.y);
                writer.writeFloat(normal.z);
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
