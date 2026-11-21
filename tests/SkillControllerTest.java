package com.example.bubblepilot;
import java.util.*;
public final class SkillControllerTest {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static GameSolver.Shot shot(int pop,int bounces){return new GameSolver.Shot(220,400,0,400,pop,0,42,bounces,0,0,1,0,1);}
    static SkillVision.Icon icon(SkillVision.Kind k,int y){return new SkillVision.Icon(k,130,y,0);}
    public static void main(String[] a){
        SkillController c=new SkillController();
        SkillVision.Icon time=icon(SkillVision.Kind.TIME,760),rain=icon(SkillVision.Kind.RAINBOW,625),gold=icon(SkillVision.Kind.GOLD,695),blue=icon(SkillVision.Kind.IGNORE,760),x2=icon(SkillVision.Kind.X2,695);
        List<SkillVision.Icon> all=Arrays.asList(time,rain,gold,x2);
        check(c.passive(all,0)==x2,"X2 immediate, even without normal shot / gate");
        c.dispatched(x2,0);check(c.passive(all,10)==null,"No duplicate in pending state");
        c.observe(all,100,true,false);check(c.waiting(),"Retained X2 not confirmation");
        c.observe(Arrays.asList(time,rain,gold),200,true,false);c.observe(Arrays.asList(time,rain,gold),300,true,false);
        check(c.passive(all,300)==time,"Time after X2 cooldown");
        c.reset();check(c.choose(all,shot(5,0),shot(9,0),0)==null,"Preserve normal pop, never spend gold/rainbow");
        check(c.choose(Arrays.asList(rain,gold),shot(0,0),shot(5,0),600)==null&&c.confirming(),"Rainbow first observation");
        check(c.choose(Arrays.asList(rain,gold),shot(0,0),shot(5,0),800)==rain,"Rainbow before gold if useful");
        c.dispatched(rain,800);c.observe(Arrays.asList(gold,blue),1000,true,false);c.observe(Arrays.asList(gold,blue),1200,true,false);
        check(c.waiting()&&c.armed()==null,"Disappearing rainbow alone does not arm");
        c.colorDispatched('V',1250);check(!c.canChooseColor(1300),"No repeat modal tap");
        c.observe(Arrays.asList(gold,blue),1400,false,false);check(c.waiting(),"Wait for actual selected ball");
        c.observe(Arrays.asList(gold,blue),1500,true,false);check(c.armed()==SkillVision.Kind.RAINBOW&&c.comboColor()=='V',"Confirmed selected color");
        check(c.choose(Arrays.asList(gold,blue),shot(0,0),null,1600)==null,"No overlapping projectile skill");c.fired();
        c.reset();check(c.passive(Arrays.asList(blue),0)==null&&c.choose(Arrays.asList(blue),shot(0,0),null,0)==null,"Ignore blue always");
        check(c.choose(Arrays.asList(gold),shot(3,0),null,300)==null,"No gold while pop exists");
        check(c.choose(Arrays.asList(gold),shot(0,0),null,400)==null&&c.choose(Arrays.asList(gold),shot(0,0),null,600)==gold,"Gold only when no normal pop");
        c.dispatched(gold,600);c.observe(Collections.emptyList(),700,true,false);c.observe(Collections.emptyList(),850,true,false);
        check(c.waiting()&&c.armed()==null,"Do not shoot old projectile after gold click");
        c.observe(Collections.emptyList(),1000,true,true);check(c.armed()==SkillVision.Kind.GOLD,"Gold loaded confirmed");
        c.reset();c.dispatched(gold,0);c.observe(Arrays.asList(gold),2600,true,false);check(!c.waiting()&&c.armed()==null,"Failed click times out");
        System.out.println("SKILL CONTROLLER PASS (immediate X2, TIME, rainbow modal, selected-color confirmation, gold fallback, ignore, timeout)");
    }
}
