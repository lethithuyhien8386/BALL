package com.example.bubblepilot;
import java.util.List;
import java.util.EnumMap;
/** Main-thread skill state. A modal selection is an action, never a shot. */
public final class SkillController {
    private SkillVision.Kind pending,armed,stableKind;
    private int stableX,stableY,stableCount,missing;
    private long pendingAt,choiceAt;
    private char comboColor=' ';
    private final EnumMap<SkillVision.Kind,Long> retryAfter=new EnumMap<>(SkillVision.Kind.class);
    public void reset(){pending=null;armed=null;stableKind=null;stableCount=0;missing=0;comboColor=' ';choiceAt=0;retryAfter.clear();}
    public SkillVision.Kind armed(){return armed;}
    public SkillVision.Kind pending(){return pending;}
    public char comboColor(){return comboColor;}
    public void fired(){armed=null;comboColor=' ';stableCount=0;stableKind=null;}
    public boolean waiting(){return pending!=null;}
    public boolean confirming(){return stableCount==1;}
    public boolean canChooseColor(long now){return comboColor==' '||now-choiceAt>=900;}
    public void colorDispatched(char color,long now){pending=SkillVision.Kind.RAINBOW;pendingAt=choiceAt=now;comboColor=color;missing=0;stableKind=null;stableCount=0;}
    public void colorCancelled(){comboColor=' ';choiceAt=0;}
    /** Call only on a fresh screenshot with no modal. */
    public void observe(List<SkillVision.Icon> icons,long now,boolean launcherReady,boolean goldReady){
        if(pending==null)return;
        if(SkillVision.find(icons,pending)==null)missing++;else missing=0;
        if(pending==SkillVision.Kind.RAINBOW){
            // Never infer a selected color from the disappearing inventory icon.
            if(comboColor!=' '&&launcherReady&&now-choiceAt>=100){armed=pending;finish(now,1500);}
            else if(comboColor==' '&&now-pendingAt>2400)finish(now,3500);
            // Once the dialog has been clicked, await the actual launcher.
            // Do not fall through to an ordinary shot of an unknown combo color.
            return;
        }
        if(pending==SkillVision.Kind.GOLD){
            if(missing>=2&&goldReady){armed=pending;finish(now,1500);}
            else if(now-pendingAt>2400 && missing==0)finish(now,3500);
            // A consumed icon without a confirmed gold ball stays in WAIT.
            return;
        }
        if(missing>=2)finish(now,1500);
        else if(now-pendingAt>1500)finish(now,3500);
    }
    private void finish(long now,long cooldown){retryAfter.put(pending,now+cooldown);pending=null;stableKind=null;stableCount=0;missing=0;}
    private SkillVision.Icon available(List<SkillVision.Icon> icons,SkillVision.Kind k,long now){return retryAfter.getOrDefault(k,0L)>now?null:SkillVision.find(icons,k);}
    /** Immediate passive actions need no stable shot / board signature. */
    public SkillVision.Icon passive(List<SkillVision.Icon> icons,long now){
        if(pending!=null)return null;
        SkillVision.Icon x2=available(icons,SkillVision.Kind.X2,now);
        if(x2!=null)return x2;
        return available(icons,SkillVision.Kind.TIME,now);
    }
    /** Only spend a projectile skill if the regular planner has no pop. */
    public SkillVision.Icon choose(List<SkillVision.Icon> icons,GameSolver.Shot normal,GameSolver.Shot wildcard,long now){
        if(pending!=null||armed!=null)return null;
        SkillVision.Icon candidate=null;
        if(normal!=null&&normal.matched<3){
            if(wildcard!=null&&wildcard.matched>=3)candidate=available(icons,SkillVision.Kind.RAINBOW,now);
            if(candidate==null)candidate=available(icons,SkillVision.Kind.GOLD,now);
        }
        if(candidate==null){stableKind=null;stableCount=0;return null;}
        if(stableKind==candidate.kind && Math.abs(stableX-candidate.x)<=6 && Math.abs(stableY-candidate.y)<=6)stableCount++;
        else {stableKind=candidate.kind;stableX=candidate.x;stableY=candidate.y;stableCount=1;}
        return stableCount>=2?candidate:null;
    }
    public void dispatched(SkillVision.Icon icon,long now){pending=icon.kind;pendingAt=now;missing=0;stableCount=0;}
    public void cancelled(long now){if(pending!=null)retryAfter.put(pending,now+2500);pending=null;stableCount=0;missing=0;}
}
