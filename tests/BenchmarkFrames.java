import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.example.bubblepilot.GameSolver;
public class BenchmarkFrames {
    public static void main(String[] args) throws Exception {
        BufferedImage im=ImageIO.read(new File(args[0]));int w=im.getWidth(),h=im.getHeight();
        int[] px=im.getRGB(0,0,w,h,null,0,w);
        GameSolver.resetCalibration();
        GameSolver s=new GameSolver(new GameSolver.Configuration());
        for(int i=0;i<5;i++) {
            long t=System.nanoTime(); GameSolver.Board b=s.detect(px,w,h);
            long d=System.nanoTime();GameSolver.Shot a=s.bestShot(b);
            long e=System.nanoTime();
            System.out.printf("frame%d detect=%.1fms shot=%.1fms result=%s%n",i,(d-t)/1e6,(e-d)/1e6,a==null?"missing":a.tapX+","+a.tapY);
        }
    }
}
