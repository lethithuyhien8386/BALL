import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import com.example.bubblepilot.GameSolver;
import com.example.bubblepilot.ShotGate;

/** Regression for the game screenshot where the old APK showed CHO BONG. */
public final class NewGamePaletteTest {
    public static void main(String[] args) throws Exception {
        BufferedImage image=ImageIO.read(new File(args[0]));
        int w=image.getWidth(),h=image.getHeight();
        int[] pixels=image.getRGB(0,0,w,h,null,0,w);
        GameSolver.resetCalibration();
        GameSolver solver=new GameSolver(new GameSolver.Configuration());
        long started=System.nanoTime();
        GameSolver.Board board=solver.detect(pixels,w,h);
        if(board==null)throw new AssertionError("The previous APK failed: "+solver.diagnosis());
        if(board.shooter!='V')throw new AssertionError("Expected the purple shooter, got "+board.shooter);
        if(board.count<58 || board.count>68)
            throw new AssertionError("Detected "+board.count+" bubbles instead of the visible grid");
        if(board.balls[0][0]==null || board.balls[0][0].color!='V')
            throw new AssertionError("Top row origin must align with the purple bubble");
        if(board.balls[0][7]==null || board.balls[0][7].color!='B')
            throw new AssertionError("Cyan ball not recognized");
        if(board.balls[4][7]==null || board.balls[4][7].color!='W')
            throw new AssertionError("Special light-colored bubble not recognized");
        for(int c=0;c<GameSolver.COLS;c++)
            if(board.balls[8][c]!=null)
                throw new AssertionError("Background mistaken for a bubble in empty row 8, col "+c);
        GameSolver.Shot shot=solver.bestShot(board);
        if(shot==null)throw new AssertionError("No tap computed for the purple projectile");
        if(shot.tapX<2 || shot.tapX>=w || shot.tapY<2 || shot.tapY>=h*.75)
            throw new AssertionError("Invalid tap coordinate: "+shot.tapX+","+shot.tapY);
        long elapsed=System.nanoTime()-started;
        GameSolver.Board nextFrame=solver.detect(pixels,w,h);
        if(nextFrame==null || nextFrame.signature!=board.signature)
            throw new AssertionError("Static board signature must stay stable between captures");
        ShotGate gate=new ShotGate();
        if(gate.canShoot(board.signature,0)) throw new AssertionError("One frame isn't stable");
        if(!gate.canShoot(nextFrame.signature,150)) throw new AssertionError("Second frame must be ready");
        gate.shotDispatched(board.signature,160);
        if(gate.canShoot(nextFrame.signature,400)) throw new AssertionError("Duplicate shot not allowed");
        System.out.printf("NEW GAME PASS: %dx%d, shooter=%c, balls=%d, special=W, tap=%d,%d, score=%.1f, initial=%.1f ms%n",
                w,h,board.shooter,board.count,shot.tapX,shot.tapY,shot.score,elapsed/1e6);
    }
}
