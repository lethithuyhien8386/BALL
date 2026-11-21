package com.example.bubblepilot;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
/** Assertions on manually inspected frames extracted from all five new videos. */
public final class TrainingRegressionTest {
    private final File folder;
    TrainingRegressionTest(String path){folder=new File(path);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    BufferedImage load(String name)throws Exception{return ImageIO.read(new File(folder,name+".jpg"));}
    int[] pixels(BufferedImage im){return im.getRGB(0,0,im.getWidth(),im.getHeight(),null,0,im.getWidth());}
    List<SkillVision.Icon> icons(String name)throws Exception{BufferedImage im=load(name);return SkillVision.detect(pixels(im),im.getWidth(),im.getHeight());}
    void checkIcons(String f,SkillVision.Kind... expected)throws Exception{
        EnumSet<SkillVision.Kind> actual=EnumSet.noneOf(SkillVision.Kind.class);
        for(SkillVision.Icon i:icons(f))actual.add(i.kind);
        check(actual.equals(expected.length==0?EnumSet.noneOf(SkillVision.Kind.class):EnumSet.copyOf(Arrays.asList(expected))),f+" icons="+actual);
    }
    GameSolver.Board frame(String name)throws Exception{
        BufferedImage im=load(name);GameSolver.resetCalibration();GameSolver solver=new GameSolver(new GameSolver.Configuration());
        return solver.detect(pixels(im),im.getWidth(),im.getHeight());
    }
    static boolean has(GameSolver.Board b,char c){for(GameSolver.Ball[] row:b.balls)for(GameSolver.Ball ball:row)if(ball!=null && ball.color==c)return true;return false;}
    public static void main(String[] a)throws Exception{
        TrainingRegressionTest t=new TrainingRegressionTest(a[0]);
        t.checkIcons("v1_000");t.checkIcons("v2_000");t.checkIcons("v3_000");t.checkIcons("v4_000");t.checkIcons("v5_000");
        t.checkIcons("v1_015",SkillVision.Kind.GOLD,SkillVision.Kind.IGNORE);
        t.checkIcons("v1_030",SkillVision.Kind.RAINBOW,SkillVision.Kind.GOLD,SkillVision.Kind.IGNORE);
        t.checkIcons("v2_025",SkillVision.Kind.TIME);
        t.checkIcons("v2_075",SkillVision.Kind.RAINBOW,SkillVision.Kind.IGNORE,SkillVision.Kind.TIME);
        t.checkIcons("v3_045",SkillVision.Kind.GOLD,SkillVision.Kind.RAINBOW,SkillVision.Kind.IGNORE);
        t.checkIcons("v4_060",SkillVision.Kind.TIME,SkillVision.Kind.GOLD,SkillVision.Kind.IGNORE);
        t.checkIcons("v5_030",SkillVision.Kind.TIME);
        t.checkIcons("v5_020");t.checkIcons("v3_095");
        GameSolver.Board one=t.frame("v1_000");check(one!=null && one.count==76 && one.shooter=='P',"v1 initial 76 bubbles");
        GameSolver.Board two=t.frame("v2_000");check(two!=null && two.count>=70 && has(two,'O') && has(two,'Y'),"v2 must separate orange/yellow");
        GameSolver.Board three=t.frame("v3_000");check(three!=null && three.shooter=='B',"v3 cyan launcher");
        GameSolver.Board four=t.frame("v4_000");check(four!=null && has(four,'P') && has(four,'V'),"v4 must separate violet/magenta");
        GameSolver.Board five=t.frame("v5_000");check(five!=null && has(five,'Y') && has(five,'P') && has(five,'V'),"v5 palette");
        check(t.frame("v3_095")==null,"v3 score screen must never be playable");
        BufferedImage flight=t.load("v4_040");GameSolver.resetCalibration();GameSolver solver=new GameSolver(new GameSolver.Configuration());
        GameSolver.Board moving=solver.detect(t.pixels(flight),flight.getWidth(),flight.getHeight());
        check(moving!=null && solver.hasAirborneBubble(t.pixels(flight),moving),"v4 airborne bubble");
        for(String f:new String[]{"v1_000","v2_000","v3_000","v4_000","v5_000"}){
            BufferedImage im=t.load(f);GameSolver.resetCalibration();GameSolver sol=new GameSolver(new GameSolver.Configuration());GameSolver.Board b=sol.detect(t.pixels(im),im.getWidth(),im.getHeight());
            check(b!=null && !sol.hasAirborneBubble(t.pixels(im),b),f+" static initial board");
            check(sol.bestShot(b)!=null,f+" legal route");
        }
        System.out.println("TRAINING PASS: 5 videos, 14 icon sets, palette separation, 76-ball grid, score-screen gate, flight and initial readiness");
    }
}
