package com.example.bubblepilot;
import java.io.*;import java.util.*;import javax.imageio.ImageIO;import java.awt.image.BufferedImage;
public class TrainingAudit {
 public static void main(String[] args)throws Exception{
 File[] fs=new File(args[0]).listFiles((d,n)->n.endsWith(".jpg"));Arrays.sort(fs);
 int prev=-1;for(File f:fs){int v=f.getName().charAt(1);if(v!=prev){GameSolver.resetCalibration();prev=v;}
 BufferedImage im=ImageIO.read(f);int w=im.getWidth(),h=im.getHeight();int[] px=im.getRGB(0,0,w,h,null,0,w);
 GameSolver solver=new GameSolver(new GameSolver.Configuration());GameSolver.Board b=solver.detect(px,w,h);GameSolver.Shot s=solver.bestShot(b);
 String icons="";for(SkillVision.Icon i:SkillVision.detect(px,w,h))icons+=String.format(" %s@%d,%.0f",i.kind,i.y,i.error);
 System.out.printf("%s %s n=%d loaded=%c xy=%s pop=%d%s%n",f.getName(),solver.diagnosis(),b==null?0:b.count,b==null?'-':b.shooter,b==null?"-":String.format("%.0f,%.0f",b.shooterX,b.shooterY),s==null?0:s.matched,icons+(solver.hasAirborneBubble(px,b)?" FLIGHT":""));
 }
 }
}
