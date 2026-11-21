package com.example.bubblepilot;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/** True 480x856 frames sampled from user video ball 2.mp4. */
public final class VideoContinuousTest {
    private static void require(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception {
        File folder=new File(args[0]);File[] frames=folder.listFiles((d,n)->n.endsWith(".png"));
        require(frames!=null && frames.length>=12,"Missing second video regression frames");
        Arrays.sort(frames);
        GameSolver.resetCalibration();
        int ready=0,popping=0,advancing=0;
        for(File file:frames) {
            BufferedImage image=ImageIO.read(file);
            int width=image.getWidth(),height=image.getHeight();
            GameSolver solver=new GameSolver(new GameSolver.Configuration());
            long start=System.nanoTime();
            GameSolver.Board board=solver.detect(image.getRGB(0,0,width,height,null,0,width),width,height);
            long detected=System.nanoTime();
            GameSolver.Shot shot=solver.bestShot(board);
            long completed=System.nanoTime();
            System.out.printf("VIDEO2 %s n=%d shooter=%c pop=%d drop=%d bounce=%d dt=%d+%dms%n",
                    file.getName(),board==null?0:board.count,board==null?'-':board.shooter,
                    shot==null?0:shot.matched,shot==null?0:shot.dropped,
                    shot==null?0:shot.bounces,(detected-start)/1000000,
                    (completed-detected)/1000000);
            if(file.getName().equals("t000.png")) {
                require(board==null,"Never fire on the initial menu");
            }
            if(file.getName().equals("t068.png")) {
                require(board!=null && board.shooter=='V',"At 68s loaded bubble is purple, not dragon blue");
                require(Math.abs(board.shooterX-width*.5)>10,"Rotated cannon ball must not be forced to center");
            }
            if(file.getName().equals("t100.png"))
                require(board!=null && board.shooter=='R',"At 100s loaded bubble is red, not dragon blue");
            if(file.getName().equals("t052.png"))
                require(board!=null && board.count>=35,"At 52s board is not empty; grid must refit");
            if(board!=null) {
                require(shot!=null,"Active shooter cannot be permanently stalled by zero-pop filter: "+file.getName());
                if(shot.matched>=3)popping++;else advancing++;
                ready++;
            }
        }
        require(ready>=10 && popping>=4 && advancing>=2,
                "Need real-game pop routes AND non-pop advancing routes");
        System.out.printf("VIDEO2 PASS ready=%d pop=%d advance=%d%n",ready,popping,advancing);
        GameSolver.Configuration cfg=new GameSolver.Configuration();
        GameSolver.Ball[][] red=new GameSolver.Ball[GameSolver.ROWS][GameSolver.COLS];
        for(int row=0;row<3;row++)for(int col=0;col<((row&1)==0?9:10);col++) {
            double x=441*(cfg.oddX+cfg.colPitch*col+((row&1)==0?cfg.colPitch*.5:0));
            double y=791*(cfg.topY+cfg.rowPitch*row);
            red[row][col]=new GameSolver.Ball(row,col,'R',x,y,240);
        }
        GameSolver.Board noGreen=new GameSolver.Board(441,791,cfg,red,'G',28,1000031);
        GameSolver.Shot fallback=new GameSolver(cfg).bestShot(noGreen);
        require(fallback!=null && fallback.matched==0,
                "If no matching color exists, select a legal nonpop shot to advance next ball");
        System.out.println("FALLBACK PASS: always progresses when current color cannot score");
    }
}
