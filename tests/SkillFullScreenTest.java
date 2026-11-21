package com.example.bubblepilot;
import java.awt.*;import java.awt.image.BufferedImage;import java.util.*;import java.io.File;import javax.imageio.ImageIO;
public final class SkillFullScreenTest {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static int[] px(BufferedImage i){return i.getRGB(0,0,i.getWidth(),i.getHeight(),null,0,i.getWidth());}
    static BufferedImage scale(BufferedImage i,int w,int h){BufferedImage b=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=b.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(i,0,0,w,h,null);g.dispose();return b;}
    static void icons(BufferedImage a,double[][] expected){
        int w=a.getWidth(),h=a.getHeight();int[] p=px(a);check(SkillVision.gameplayHud(p,w,h),"Full screen HUD");
        java.util.List<SkillVision.Icon> icons=SkillVision.detect(p,w,h);
        check(icons.size()==3,"Expected 3 recognized skills, got "+icons.size());
        SkillVision.Kind[] types={SkillVision.Kind.GOLD,SkillVision.Kind.X2,SkillVision.Kind.RAINBOW};
        for(int j=0;j<types.length;j++){SkillVision.Icon i=SkillVision.find(icons,types[j]);check(i!=null&&Math.hypot(i.x-expected[j][0]*w,i.y-expected[j][1]*h)<w*.015,"Actual center "+types[j]);}
    }
    static void combo(BufferedImage a,boolean full){
        int w=a.getWidth(),h=a.getHeight();int[] p=px(a);ComboVision.Panel panel=ComboVision.detect(p,w,h);
        check(panel!=null&&panel.options.size()==3,"Three selectable color balls");
        Map<Character,ComboVision.Option> m=new HashMap<>();for(ComboVision.Option o:panel.options)m.put(o.color,o);
        check(m.keySet().equals(new HashSet<>(Arrays.asList('P','V','O'))),"Actual option colors");
        check(Math.abs(m.get('P').x/(double)w-.557)<.02,"location");
        GameSolver.resetCalibration();GameSolver solver=new GameSolver(new GameSolver.Configuration());
        GameSolver.Board b=solver.detectSkillPreview(p,w,h,panel.top,null);check(b!=null&&b.count>=30,"Grid available when launcher is hidden");
        ComboVision.Choice choice=ComboVision.choose(panel,solver,b);check(choice!=null,"Select playable option");
        for(ComboVision.Option o:panel.options){GameSolver.Shot s=solver.bestSkillColor(b,o.color);if(s==null)continue;check(!(s.matched>=3&&choice.shot.matched<3),"Prefer any scoring shot");if((s.matched>=3)==(choice.shot.matched>=3))check(choice.shot.score>=s.score,"Highest model score among actual choices");}
        System.out.println("COMBO "+w+"x"+h+" selected "+choice.option.color+" score="+choice.shot.score+" pop="+choice.shot.matched+" drop="+choice.shot.dropped);
    }
    static void loaded(BufferedImage a){
        int w=a.getWidth(),h=a.getHeight();GameSolver.resetCalibration();
        GameSolver solver=new GameSolver(new GameSolver.Configuration()).specialLauncher(true);
        GameSolver.Board old=solver.detect(px(a),w,h);check(old!=null&&old.shooter=='O',"Actual pre-activation orange ball");
        check(solver.detectLoadedSkill(px(a),w,h,old,old,'Y',true)==null,"Ordinary orange is not a confirmed gold ball");
        check(solver.detectLoadedSkill(px(a),w,h,old,old,'P',false)==null,"Do not confuse pink preview with loaded combo");
        // Synthetic skill projectiles (post-activation images not supplied).
        BufferedImage gold=scale(a,w,h);Graphics2D g=gold.createGraphics();
        g.setClip(new java.awt.geom.Ellipse2D.Double(210,588,32,32));g.drawImage(a,210,588,242,620,91,576,135,620,null);g.dispose();
        GameSolver.Board b=solver.detectLoadedSkill(px(gold),w,h,solver.detect(px(gold),w,h),old,'Y',true);
        check(b!=null&&b.shooter=='Y'&&Math.hypot(b.shooterX-226,b.shooterY-604)<10,"Synthetic metallic gold is recognized at launcher");
        BufferedImage dual=scale(a,w,h);g=dual.createGraphics();g.setClip(new java.awt.geom.Ellipse2D.Double(210,588,32,32));
        // Simulated two-color projectile using the supplied orange/pink artwork.
        g.clipRect(226,588,16,32);g.drawImage(a,210,588,242,620,102,100,136,134,null);g.dispose();
        b=solver.detectLoadedSkill(px(dual),w,h,solver.detect(px(dual),w,h),old,'P',false);
        check(b!=null&&b.shooter=='P'&&Math.hypot(b.shooterX-226,b.shooterY-604)<10,"Synthetic half pink combo confirmed at launcher");
    }
    public static void main(String[] args)throws Exception{
        BufferedImage a=ImageIO.read(new File("tests/skills-full.png")),b=ImageIO.read(new File("tests/skills-combo.png"));
        double[][] expected={{113/447.,598/795.},{113/447.,668/795.},{113/447.,739/795.}};
        icons(a,expected);icons(scale(a,480,854),expected);icons(scale(a,320,569),expected);
        // Swap the X2 and gold slots, and shift the whole inventory left/up.
        BufferedImage moved=scale(a,447,795);Graphics2D g=moved.createGraphics();
        int[] yy={598,668,739};int[] src={1,0,2};
        for(int y:yy){g.setColor(new Color(35,110,132));g.fillRect(77,y-32,72,64);}
        for(int j=0;j<3;j++){int y=yy[src[j]];g.drawImage(a,75,yy[j]-36,139,yy[j]+28,81,y-32,145,y+32,null);}g.dispose();
        icons(moved,new double[][]{{107/447.,664/795.},{107/447.,594/795.},{107/447.,735/795.}});
        check(ComboVision.detect(px(a),a.getWidth(),a.getHeight())==null,"No false modal on regular board");
        combo(b,true);combo(scale(b,480,860),true);combo(scale(b,320,573),true);loaded(a);
        System.out.println("FULL SCREEN SKILLS PASS (three scales + translated/reordered slots + actual combo screenshot + synthetic loaded skills)");
    }
}
