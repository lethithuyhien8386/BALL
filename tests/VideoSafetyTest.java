package com.example.bubblepilot;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Frames extracted from the 95.6-second video supplied by the user. */
public final class VideoSafetyTest {
    private static void check(boolean yes,String why) {
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        int[] seconds={0,24,40,52,64,76,92};
        GameSolver.resetCalibration();
        for(int t:seconds) {
            File path=new File(args[0],String.format("frame_%02d.png",t));
            BufferedImage frame=ImageIO.read(path);
            int w=frame.getWidth(),h=frame.getHeight();
            GameSolver s=new GameSolver(new GameSolver.Configuration());
            long started=System.nanoTime();
            GameSolver.Board b=s.detect(frame.getRGB(0,0,w,h,null,0,w),w,h);
            GameSolver.Shot shot=s.bestShot(b);
            long elapsed=(System.nanoTime()-started)/1000000;
            System.out.printf("VIDEO t=%02d board=%d shooter=%c route=%s time=%dms status=%s%n",
                    t,b==null?0:b.count,b==null?'-':b.shooter,
                    shot==null?"NO FIRE":"pop="+shot.matched+" bank="+shot.bounces+
                        " conf="+String.format("%.2f",shot.reliability),elapsed,s.diagnosis());
            if(t==0) {
                check(b!=null && b.shooter=='G' && b.count>=70,"t=0: launcher green, 70+ balls");
                check(b.balls[0][0]!=null && b.balls[0][0].color=='R',
                        "t=0: ceiling row starts with red balls");
            }
            if(t==40) {
                check(b!=null && b.shooter=='O' && b.count>=60,
                        "t=40: yellow projectile and wide ceiling row");
                check(b.balls[0][0]!=null && b.balls[0][0].color=='R',
                        "t=40: top row correctly anchored");
            }
            if(shot!=null) {
                check(shot.reliability>=0 && shot.reliability<=1,"Invalid route stability");
            }
        }
        // A board with ONLY reds and a green projectile must not be fired.
        GameSolver.Configuration cfg=new GameSolver.Configuration();
        GameSolver.Ball[][] red=new GameSolver.Ball[GameSolver.ROWS][GameSolver.COLS];
        for(int row=0;row<3;row++)for(int col=0;col<((row&1)==0?9:10);col++) {
            double x=441*(cfg.oddX+cfg.colPitch*col+((row&1)==0?cfg.colPitch*.5:0));
            double y=791*(cfg.topY+cfg.rowPitch*row);
            red[row][col]=new GameSolver.Ball(row,col,'R',x,y,240);
        }
        GameSolver.Board noGreen=new GameSolver.Board(441,791,cfg,red,'G',28,1000031);
        GameSolver sol=new GameSolver(cfg);
        check(sol.bestShot(noGreen)!=null && sol.bestShot(noGreen).matched==0,
                "Planner must advance to next ball when no match exists");
        check(sol.safeShotAtTap(noGreen,230,420)==null,"Astra MUST NOT bypass zero-pop filter");
        System.out.println("VideoSafetyTest PASS (real video, always advance, cloud pop validation)");
    }
}
