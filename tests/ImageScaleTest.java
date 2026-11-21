import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.example.bubblepilot.GameSolver;

/** Test that the pixel-relative detector keeps the same board on a larger screen. */
public final class ImageScaleTest {
    public static void main(String[] args)throws Exception {
        BufferedImage original=ImageIO.read(new File(args[0]));
        int w=original.getWidth(),h=original.getHeight();
        int[] base=original.getRGB(0,0,w,h,null,0,w);
        int[] doubled=new int[w*2*h*2];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int color=base[y*w+x];
            int row=(2*y)*(2*w)+2*x;
            doubled[row]=color;doubled[row+1]=color;
            doubled[row+w*2]=color;doubled[row+w*2+1]=color;
        }
        GameSolver solver=new GameSolver(new GameSolver.Configuration());
        GameSolver.Board small=solver.detect(base,w,h);
        GameSolver.Board large=solver.detect(doubled,2*w,2*h);
        if(small==null || large==null)throw new AssertionError("Board missing after scale");
        if(small.count!=large.count || small.shooter!=large.shooter) {
            throw new AssertionError("Pixel detector not resolution independent: "+small.count+" vs "+large.count);
        }
        if(solver.bestShot(large)==null) throw new AssertionError("No shot at doubled resolution");
        System.out.println("ImageScaleTest PASS at "+w+"x"+h+" and "+(w*2)+"x"+(h*2));
    }
}
