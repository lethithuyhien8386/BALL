package com.example.bubblepilot;
import java.util.*;
public final class SkillControllerTest {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static GameSolver.Shot shot(int pop,int bounces){return new GameSolver.Shot(220,400,0,400,pop,0,42,bounces,0,0,1,0,1);}
    static SkillVision.Icon icon(SkillVision.Kind k,int y){return new SkillVision.Icon(k,130,y,0);}
    public static void main(String[] a){
        SkillController c=new SkillController();
        SkillVision.Icon time=icon(SkillVision.Kind.TIME,760),rain=icon(SkillVision.Kind.RAINBOW,625),gold=icon(SkillVision.Kind.GOLD,695),blue=icon(SkillVision.Kind.IGNORE,760);
        List<SkillVision.Icon> all=Arrays.asList(time,rain,gold);
        check(c.choose(all,shot(0,0),shot(3,0),0)==null && c.confirming(),"Need two skill frames");
        check(c.choose(all,shot(0,0),shot(3,0),200)==time,"Time first");
        c.dispatched(time,200);c.observe(all,300);check(c.waiting(),"Retained icon is not confirmation");
        c.observe(Arrays.asList(rain,gold),400);check(c.waiting(),"Single missing frame is not confirmation");
        c.observe(Arrays.asList(rain,gold),500);check(!c.waiting() && c.armed()==null,"Time confirmed without changing projectile");
        check(c.choose(Arrays.asList(rain,gold),shot(0,0),shot(5,0),600)==null,"Rainbow first observation");
        check(c.choose(Arrays.asList(rain,gold),shot(0,0),shot(5,0),800)==rain,"Reserve rainbow for a useful alternative");
        c.dispatched(rain,800);c.observe(Arrays.asList(gold,blue),1000);c.observe(Arrays.asList(gold,blue),1200);
        check(c.armed()==SkillVision.Kind.RAINBOW,"Rainbow armed only after confirmation");
        check(c.choose(Arrays.asList(gold,blue),shot(5,0),null,1300)==null,"One skill at a time");c.fired();
        c.reset();check(c.choose(Arrays.asList(blue),shot(5,0),null,0)==null && c.choose(Arrays.asList(blue),shot(5,0),null,200)==null,"Ignore blue always");
        check(c.choose(Arrays.asList(gold),shot(3,1),null,300)==null,"Gold requires direct shot");
        check(c.choose(Arrays.asList(gold),shot(3,0),null,400)==null && c.choose(Arrays.asList(gold),shot(3,0),null,600)==gold,"Direct gold activation");
        c.dispatched(gold,600);c.observe(Arrays.asList(gold),2300);check(!c.waiting() && c.armed()==null,"Unconfirmed activation times out");
        check(c.choose(Arrays.asList(gold),shot(3,0),null,2400)==null,"Back off after timeout");
        System.out.println("SKILL CONTROLLER PASS (stable, time priority, rainbow, gold, ignore, timeout)");
    }
}
