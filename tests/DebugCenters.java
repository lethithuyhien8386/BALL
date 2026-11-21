import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.example.bubblepilot.GameSolver;
public class DebugCenters {public static void main(String[] args)throws Exception {BufferedImage image=ImageIO.read(new File(args[0]));int w=image.getWidth(),h=image.getHeight(); GameSolver s=new GameSolver(new GameSolver.Configuration());GameSolver.Board b=s.detect(image.getRGB(0,0,w,h,null,0,w),w,h);System.out.println("diag="+s.diagnosis()); if(b==null)return;for(int r=0;r<14;r++)for(int c=0;c<10;c++)if(b.balls[r][c]!=null) {GameSolver.Ball a=b.balls[r][c];System.out.printf("%d,%d %-1s %.1f %.1f pixels %d%n",r,c,a.color,a.x,a.y,a.visiblePixels);}}}
