package com.example.bubblepilot;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
public final class GeometryRegressionTest {
    static void test(String path,boolean shifted) throws Exception {
        GameSolver.resetCalibration();
        BufferedImage image=ImageIO.read(new File(path));
        int w=image.getWidth(),h=image.getHeight();
        GameSolver solver=new GameSolver(new GameSolver.Configuration());
        GameSolver.Board b=solver.detect(image.getRGB(0,0,w,h,null,0,w),w,h);
        if(b==null)throw new AssertionError("No board: "+path);
        double expectedEdgeLeft=shifted?38:23;
        double expectedEdgeRight=shifted?401:386;
        if(Math.abs((b.leftWall-b.radius)-expectedEdgeLeft)>2 ||
           Math.abs((b.rightWall+b.radius)-expectedEdgeRight)>2)
            throw new AssertionError("Arena walls not measured from visible pixels: "+
                                     (b.leftWall-b.radius)+","+(b.rightWall+b.radius));
        if(!(b.leftWall<b.shooterX && b.rightWall>b.shooterX))
            throw new AssertionError("No flight corridor");
        GameSolver.Shot shot=solver.bestShot(b);
        if(shot==null)throw new AssertionError("No safe path");
        System.out.printf("Geometry PASS %s: walls=%.1f,%.1f, bubbleCenters=%.1f,%.1f, shooter=%.1f,%.1f, best bounce=%d, confidence=%.2f%n",
                shifted?"shifted HUD":"reference",b.leftWall,b.rightWall,
                b.gridX(1,0),b.gridX(1,9),b.shooterX,b.shooterY,shot.bounces,shot.reliability);
    }
    public static void main(String[] args) throws Exception {
        test(args[0],false);test(args[1],true);
    }
}
