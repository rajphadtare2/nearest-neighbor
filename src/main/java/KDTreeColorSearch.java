package main.java;

import org.apache.commons.imaging.color.ColorCieLab;
import org.apache.commons.imaging.color.ColorConversions;
import org.apache.commons.imaging.color.ColorXyz;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class KDNode implements Serializable {
    private static final long serialVersionUID = 1L;

    double l, a, b;
    String hex;
    KDNode left, right;

    KDNode(ColorCieLab point, String hex) {
        this.l = point.L;
        this.a = point.a;
        this.b = point.b;
        this.hex = hex;
    }

    ColorCieLab toLab() {
        return new ColorCieLab(l, a, b);
    }
}

class KDTree implements Serializable {
    private final KDNode root;

    public KDTree(List<KDNode> points) {
        root = build(points, 0);
    }

    private KDNode build(List<KDNode> nodes, int depth) {
        if (nodes.isEmpty()) return null;

        int axis = depth % 3;
        nodes.sort(Comparator.comparingDouble(n -> getAxisValue(n.toLab(), axis)));

        int median = nodes.size() / 2;
        KDNode node = nodes.get(median);

        node.left = build(nodes.subList(0, median), depth + 1);
        node.right = build(nodes.subList(median + 1, nodes.size()), depth + 1);

        return node;
    }

    private double getAxisValue(ColorCieLab point, int axis) {
        if (axis == 0) return point.L;
        else if (axis == 1) return point.a;
        else return point.b;
    }

    public KDNode nearest(ColorCieLab target){
        return nearest(root,target,root,Double.MAX_VALUE, 0);
    }

    public KDNode nearest(KDNode currentNode, ColorCieLab target, KDNode best, double bestDist, int depth){

        if(currentNode == null) return best;
        double currentDist = distance(currentNode.toLab(),target);
        if( currentDist < bestDist){
            bestDist = currentDist;
            best = currentNode;
        }
        int axis = depth % 3;

        // here we check if the current axis' value is smaller or greater. since the nodes are sorted, we choose left or right accordingly.
        KDNode sameSideChild = getAxisValue(currentNode.toLab(),axis) < getAxisValue(target, axis) ? currentNode.left : currentNode.right ;
        KDNode oppositeSideChild = getAxisValue(currentNode.toLab(),axis) < getAxisValue(target, axis) ? currentNode.right : currentNode.left ;

        best = nearest(sameSideChild, target, best, bestDist, depth + 1);
        bestDist = distance(best.toLab(), target);


        //here we check if current dimension value difference is less than the best distance till now. IF it's less
        // that means there is a chance that even smaller distance could be found on the other side if the tree
        if(Math.abs(getAxisValue(target, axis) - getAxisValue(currentNode.toLab(),axis)) < bestDist){
            best = nearest(oppositeSideChild, target, best, bestDist, depth + 1);
        }

        return best;
    }

    public static double distance(ColorCieLab c1, ColorCieLab c2) {
        double deltaL = c1.L - c2.L;
        double deltaA = c1.a - c2.a;
        double deltaB = c1.b - c2.b;
        return Math.sqrt(deltaL * deltaL + deltaA * deltaA + deltaB * deltaB);
    }
}

class KDTreeUtils {

    public static int[] hexToRGB(String hex) throws IOException {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new int[]{r,g,b};
    }

    public static List<KDNode> readRGBFile(String filePath) throws IOException {
        List<KDNode> nodes = new ArrayList<>();

        // Load resource from classpath
        InputStream inputStream = KDTreeColorSearch.class.getResourceAsStream(filePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found: " + filePath);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String hex;
            while ((hex = br.readLine()) != null) {
                hex = hex.trim(); // remove extra spaces
                if (hex.isEmpty()) continue;

                int[] rgb = hexToRGB(hex);
                ColorCieLab lab = rgbToLab(rgb[0], rgb[1], rgb[2]);

                // Store primitive LAB values in KDNode
                nodes.add(new KDNode(lab, hex));
            }
        }

        return nodes;
    }

    public static ColorCieLab rgbToLab(int r, int g, int b) {
        int rgb = (r << 16) | (g << 8) | b;
        ColorXyz xyz = ColorConversions.convertRGBtoXYZ(rgb);
        return ColorConversions.convertXYZtoCIELab(xyz);
    }

    //we are saving tree into a file thats why it is serializable.
    // Also, so that we don't have to create tree again when we run the program.
    public static void saveTree(KDTree tree, String filePath) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            out.writeObject(tree);
        }
    }

    //de-serialize tree from the file
    public static KDTree loadTree(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(Paths.get(filePath)))) {
            return (KDTree) in.readObject();
        }
    }
}

public class KDTreeColorSearch {
    public static void main(String[] args) {
        String datasetFile = "/colors.csv";   //this file contains all the RGB colors data
        String treeFile = "kdtree.ser";      //this file will have our kd tree data

        try {
            KDTree tree;

            File f = new File(treeFile);
            if (f.exists()) {
                tree = KDTreeUtils.loadTree(treeFile);
                System.out.println("KD-Tree loaded from file.");
            } else {
                List<KDNode> points = KDTreeUtils.readRGBFile(datasetFile);
                tree = new KDTree(points);

                KDTreeUtils.saveTree(tree, treeFile);
                System.out.println("KD-Tree built and saved.");
            }

            String input = "#7F5FE3";
            int[] rgb = KDTreeUtils.hexToRGB(input);

            KDNode nearest = tree.nearest(KDTreeUtils.rgbToLab(rgb[0],rgb[1],rgb[2]));

            System.out.println("Input: " + input);
            System.out.println("Nearest: " + nearest.hex);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
