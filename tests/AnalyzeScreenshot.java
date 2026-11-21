import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.example.bubblepilot.GameSolver;

public class AnalyzeScreenshot {
    public static void main(String[] args) throws Exception {
        BufferedImage image=ImageIO.read(new File(args[0]));
        int w=image.getWidth(),h=image.getHeight();
        int[] pixels=image.getRGB(0,0,w,h,null,0,w);
        GameSolver solver=new GameSolver(new GameSolver.Configuration());
        long begin=System.nanoTime();
        GameSolver.Board b=solver.detect(pixels,w,h);
        if(b==null)throw new IllegalStateException("Failed detection");
        System.out.println("Image: "+w+"x"+h+"; shooter="+b.shooter+"; ballCount="+b.count);
        for(int r=0;r<19;r++) {
            StringBuilder sb=new StringBuilder();
            for(int c=0;c<10;c++)sb.append(b.balls[r][c]==null?'_':b.balls[r][c].color);
            if(sb.toString().replace("_","").isEmpty() && r>=9)break;
            System.out.println("row "+r+" "+sb);
        }
        GameSolver.Shot shot=solver.bestShot(b);
        long elapsed=System.nanoTime()-begin;
        if(shot==null)throw new IllegalStateException("Failed to find aim");
        System.out.printf("Proposed tap=(%d,%d), angle=%.1f deg, estimated score=%.2f, matched=%d, dropped=%d, time=%.1f ms%n",
            shot.tapX,shot.tapY,shot.angle,shot.score,shot.matched,shot.dropped,elapsed/1e6);
        if(b.count<50||b.count>100)throw new IllegalStateException("Unexpected board detection count: "+b.count);
        if(shot.tapX<0||shot.tapX>=w||shot.tapY<0||shot.tapY>=h)throw new IllegalStateException("Invalid tap");
    }
}
