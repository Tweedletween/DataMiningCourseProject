import java.io.*;
import java.util.*;
import java.nio.file.Paths;

public class P1 {
    public static final String FILE_PATH = "/home/mwang2/test/coen281/";
    public static final int SHINGLE_SIZE = 9;
    // Constant Locality-Simalarity Family(d1, d2, p1, p2)
    public static final double D2 = 0.8;
    public static final double P1 = 0.997;
    private static Map<String, Set<String>> shinglesMap = new HashMap<>();
    private static boolean[][] shingleMatrix = null;
    private static int numOfShingles = 0;
    private static int[][] permutation = null;
    private static int[][] signatureMatrix = null;
    private static Map<Integer, String> fileMap = new HashMap<>();
    private static int M, b, r = 0;

    public static void main(String[] args) {
        System.out.println(Paths.get(".").toAbsolutePath().normalize().toString());
        get9Shingles();
        genMinHashing();
        LSH();
    }

    private static void get9Shingles() {
        Scanner scanner = new Scanner(System.in);
        List<File> files = new LinkedList<>();

        while(scanner.hasNext()) {
            String filename = FILE_PATH + scanner.next();
            System.out.println(filename);
            File tmp = new File(filename);
            if (tmp.isFile()) {
                files.add(tmp);
            }
        }

        if (files == null || files.size() == 0) {
            System.out.println("Get no files...");
            return;
        }

        // Get each file's 9-Shingling set
        for (File file : files) {
            if (file.isFile()) {
                shinglesMap.put(file.getName(), new HashSet<String>());
                get1FileShingles(file, file.getName());
            }
        }

        getShingleMatrix();
    }

    private static byte[] getFileContent(File file) {
        FileInputStream fis = null;
        byte[] data = new byte[(int) file.length()];

        try {
            fis = new FileInputStream(file);
            fis.read(data);
            fis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }

    private static void get1FileShingles(File file, String name) {
        byte[] fcontent = getFileContent(file);
        StringBuffer buf = new StringBuffer();

        for (int i = 0; i < fcontent.length; i++) {
            // Only use the letter char
            if (Character.isLetter((char)fcontent[i])) {
                buf.append((char)Character.toLowerCase(fcontent[i]));

                if (buf.length() == SHINGLE_SIZE) {
                    shinglesMap.get(name).add(buf.toString());
                    buf.deleteCharAt(0);
                }
            }
        }

        /*for (String s : shinglesMap.keySet()) {
            Set<String> tmp = shinglesMap.get(s);
            for (String x : tmp) {
                System.out.println(x);
            }
        }*/
    }

    private static void getShingleMatrix() {
        if (shinglesMap == null || shinglesMap.size() == 0) {
            return;
        }

        Set<String> set = new TreeSet<String>();
        for (String s : shinglesMap.keySet()) {
            set.addAll(shinglesMap.get(s));
        }
        numOfShingles = set.size();

        int hashVal = 0;
        Map<String, Integer> map = new HashMap<>();
        for (String s : set) {
            map.put(s, hashVal++);
        }

        System.out.println("\nShingles mapped into Integers: ");
        for (String s : map.keySet()) {
            System.out.println(s + " => " +  map.get(s));
        }

        shingleMatrix = new boolean[set.size()][shinglesMap.size()];
        int fileIndex = 0;
        for (String s : shinglesMap.keySet()) {
            for (String tmp : shinglesMap.get(s)) {
                shingleMatrix[map.get(tmp)][fileIndex] = true;
            }
            fileMap.put(fileIndex++, s);
        }

        System.out.println("Number of Shinglings: " + numOfShingles + "\n");
        System.out.println("File Name and Shingle Matrix / Signature Matrix Mapping: ");
        for (int i: fileMap.keySet()) {
            System.out.println(String.format("Column: " + "%3d" + " => File name: " + fileMap.get(i), i));
        }

        if (shingleMatrix != null) {
            System.out.println("\nOriginal Shingle Matrix:");
            for (int i = 0; i < shingleMatrix.length; i++) {
                
                for (int j = 0; j < shingleMatrix[0].length; j++) {
                    System.out.print(shingleMatrix[i][j] ? "1 " : "0 ");
                }
                System.out.println();
            }
        }
    }

    private static void genMinHashing() {
        if (shinglesMap == null || shinglesMap.size() == 0) {
            return;
        }

        M = (int) Math.ceil(Math.sqrt(numOfShingles));
        if (M < 4) M = 4;

        boolean found = false;
        while (!found) {
            // If an integer is prime, and > 4, then the integer must be odd, add 1 so that it becomes even and not prime
            if (isPrime(M)) {
                M++;
            }

            // Find valid r & b, if not valid r & b, adjust M and refind to make sure (d1, d2, p1, p2)-sensitive
            for (int i = 2; i < M; i++) {
                if (M % i == 0) {
                    if (1 - Math.pow(1 - Math.pow(D2, M / i), i) >= P1) {
                        r = M / i;
                        b = i;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                M++;
            }
        }
        System.out.println("\nNumber of permutaions: " + M);

        // Initialize permutation matrix
        permutation = new int[(int) numOfShingles][M];
        for (int i = 0; i < permutation.length; i++) {
            for (int j = 0; j < permutation[0].length; j++) {
                permutation[i][j] = i + 1;
            }
        }

        // Generate random permutation matrix
        if (permutation != null && permutation.length != 0) {
            for (int i = 0; i < permutation[0].length; i++) {
                for (int j = 0; j < permutation.length; j++) {
                    int r = (int) (Math.random() * (j + 1));
                    int swap = permutation[r][i];
                    permutation[r][i] = permutation[j][i];
                    permutation[j][i] = swap;
                }
            }
        }

        // Generate Signature matrix
        if (permutation != null && shingleMatrix != null) {
            // Initialize the Signature matrix
            signatureMatrix = new int[permutation[0].length][shingleMatrix[0].length];

            for (int i = 0; i < permutation[0].length; i++) {
                for (int j = 0; j < shingleMatrix[0].length; j++) {
                    int tmp = Integer.MAX_VALUE;
                    for (int k = 0; k < permutation.length; k++) {
                        if (permutation[k][i] < tmp && shingleMatrix[k][j] == true) {
                            tmp = permutation[k][i];
                        }
                    }
                    signatureMatrix[i][j] = tmp;
                }
            }
        }

        // Print the Signature matrix
        if (signatureMatrix != null && signatureMatrix[0].length > 0) {
            System.out.println("\nSignature Matrix: ");
            for (int i = 0; i < signatureMatrix.length; i++) {
                for (int j = 0; j < signatureMatrix[0].length; j++) {
                    System.out.print(String.format("%8d", signatureMatrix[i][j]));
                }
                System.out.println();
            }
        }
    }

    private static boolean isPrime(int n) {
        if (n % 2 == 0) {
            return false;
        }

        for (int i = 3; i * i <= n; i += 2) {
            if(n % i == 0) {
                return false;
            }
        }

        return true;
    }

    private static void LSH() {
        if (shinglesMap == null || shinglesMap.size() == 0) {
            return;
        }

        // Print r & b, and the way to construct
        if (r == 0 && b == 0) {
            return;
        } else {
            System.out.println("\nr = " + r + ", b = " + b);
            System.out.println("r‐way AND construction first, then b‐way OR construction");
        }

        // Iterate through all the the pairs of document columns in the signature matrix;
        // r‐way AND, then b‐way OR, so if one band is true(all the same), then the two columns are similar;
        System.out.println("\nPossible plagiarism pairs: ");
        if (signatureMatrix != null) {
            // First document in pair
            for (int i = 0; i < signatureMatrix[0].length; i++) {
                // Second document in pair
                for (int j = i + 1; j < signatureMatrix[0].length; j++) {
                    for (int k = 0; k < b; k++) {
                        boolean same = true;
                        // System.out.println("i = " + i + ", j = " + j + ", k = " + k);
                        for (int l = 0; l < r; l++) {
                            same = same && (signatureMatrix[k * r + l][i] == signatureMatrix[k * r + l][j]);
                        }
                        if (same == true) {
                            System.out.println(fileMap.get(i) + " & " + fileMap.get(j));
                            break;
                        }
                    }
                }
            }
        }
    }
}
