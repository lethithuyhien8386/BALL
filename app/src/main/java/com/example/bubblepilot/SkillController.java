package com.example.bubblepilot;
import java.util.List;
import java.util.EnumMap;
/** One activation at a time; require two observations and disappearance to
 * confirm consumption. Never activate ignored or unknown icons. */
public final class SkillController {
    private SkillVision.Kind pending,armed,stableKind;
    private int stableX,stableY,stableCount,missing;
    private long pendingAt;
    private final EnumMap<SkillVision.Kind,Long> retryAfter=new EnumMap<>(SkillVision.Kind.class);
    public void reset(){pending=null;armed=null;stableKind=null;stableCount=0;missing=0;retryAfter.clear();}
    public SkillVision.Kind armed(){return armed;}
    public SkillVision.Kind pending(){return pending;}
    public void fired(){armed=null;stableCount=0;stableKind=null;}
    public boolean waiting(){return pending!=null;}
    public boolean confirming(){return stableCount==1;}
    public void observe(List<SkillVision.Icon> icons,long now){
        if(pending==null)return;
        if(SkillVision.find(icons,pending)==null)missing++;else missing=0;
        if(missing>=2){
            if(pending==SkillVision.Kind.GOLD || pending==SkillVision.Kind.RAINBOW)armed=pending;
            retryAfter.put(pending,now+1500);pending=null;stableKind=null;stableCount=0;
        }else if(now-pendingAt>1500){retryAfter.put(pending,now+5000);pending=null;stableCount=0;stableKind=null;}
    }
    private SkillVision.Icon available(List<SkillVision.Icon> icons,SkillVision.Kind k,long now){
        return retryAfter.getOrDefault(k,0L)>now?null:SkillVision.find(icons,k);
    }
    public SkillVision.Icon choose(List<SkillVision.Icon> icons,GameSolver.Shot normal,GameSolver.Shot wildcard,long now){
        if(pending!=null || armed!=null)return null;
        SkillVision.Icon candidate=available(icons,SkillVision.Kind.TIME,now);
        if(candidate==null)candidate=available(icons,SkillVision.Kind.X2,now);
        // Save rainbow for a useful visible cluster when the current color cannot pop.
        if(candidate==null && normal!=null && normal.matched<3 && wildcard!=null && wildcard.matched>=3)
            candidate=available(icons,SkillVision.Kind.RAINBOW,now);
        // Gold's blast radius is unknown; activate only when a credible direct
        // contact to a cluster exists. Do not invent additional blast points.
        if(candidate==null && normal!=null && normal.matched>=3 && normal.bounces==0 && normal.reliability>=.75)
            candidate=available(icons,SkillVision.Kind.GOLD,now);
        if(candidate==null){stableKind=null;stableCount=0;return null;}
        if(stableKind==candidate.kind && Math.abs(stableX-candidate.x)<=6 && Math.abs(stableY-candidate.y)<=6)stableCount++;
        else {stableKind=candidate.kind;stableX=candidate.x;stableY=candidate.y;stableCount=1;}
        return stableCount>=2?candidate:null;
    }
    public void dispatched(SkillVision.Icon icon,long now){pending=icon.kind;pendingAt=now;missing=0;stableCount=0;}
    public void cancelled(long now){if(pending!=null)retryAfter.put(pending,now+3000);pending=null;stableCount=0;}
}
