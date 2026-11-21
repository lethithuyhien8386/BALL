package com.example.bubblepilot;

import java.util.ArrayList;
import java.util.List;

/** Recognize the wooden color-selection modal independently of the launcher. */
public final class ComboVision {
    public static final class Option {
        public final int x,y; public final char color;
        Option(int x,int y,char color){this.x=x;this.y=y;this.color=color;}
    }
    public static final class Panel {
        public final int top; public final List<Option> options;
        Panel(int top,List<Option> options){this.top=top;this.options=options;}
    }
    public static final class Choice {
        public final Option option; public final GameSolver.Shot shot;
        Choice(Option option,GameSolver.Shot shot){this.option=option;this.shot=shot;}
    }
    private static boolean brown(int rgb){int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;return r>=65&&r<=120&&g>=27&&g<=70&&b<=42&&r>g*1.45&&g>b*1.4;}
    private static boolean cream(int rgb){return (rgb>>16&255)>205&&(rgb>>8&255)>175&&(rgb&255)>125;}
    private static boolean darkRow(int[] px,int w,int y){
        int n=0;for(double x:new double[]{.145,.31,.43,.89})if(brown(px[y*w+(int)(w*x)]))n++;
        return n>=3;
    }
    public static Panel detect(int[] px,int w,int h){
        if(px==null||px.length!=w*h||w<180||h<350)return null;
        int top=-1,bottom=-1,step=Math.max(1,w/240);
        for(int y=(int)(h*.5);y<h*.89;y+=step){
            // Both edges of the dialog interior must be dark wood.
            boolean ok=brown(px[y*w+(int)(w*.105)])&&brown(px[y*w+(int)(w*.89)])&&darkRow(px,w,y);
            if(ok){if(top<0)top=y;bottom=y;}else if(top>=0){if(bottom-top>h*.15)break;top=bottom=-1;}
        }
        if(top<0||bottom-top<h*.15||bottom-top>h*.26)return null;
        int header=0;for(int y=Math.max(0,top-(int)(w*.072));y<top-(int)(w*.014);y+=step)
            for(int x=(int)(w*.23);x<w*.79;x+=step)if(cream(px[y*w+x]))header++;
        if(header<Math.max(25,(int)(w*w*.0007/(step*step))))return null;
        // Find the actual choice balls, not their assumed colors or order.
        List<Option> options=new ArrayList<>();
        List<double[]> candidates=new ArrayList<>();
        int stride=Math.max(2,w/100);double radius=w*.069;
        for(int y=top+(int)(radius*.8);y<bottom-radius*.7;y+=stride)
            for(int x=(int)(w*.45);x<w*.88;x+=stride){
                int[] counts=new int[128];int hits=0;
                for(int j=-2;j<=2;j++)for(int i=-2;i<=2;i++)if(i*i+j*j<=4){
                    int xx=(int)Math.round(x+i*radius*.26),yy=(int)Math.round(y+j*radius*.26);
                    int rgb=px[yy*w+xx];char c=GameSolver.skillPixelColor(rgb);
                    if(!brown(rgb)&&"ROYGBVP".indexOf(c)>=0&&(Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))>105))counts[c]++;
                    hits++;
                }
                char best=' ';int n=0;for(char c:"ROYGBVP".toCharArray())if(counts[c]>n){best=c;n=counts[c];}
                if(n<hits-3)continue;
                int outside=0,inside=0;
                for(int j=0;j<12;j++){
                    double a=j*Math.PI/6;
                    int xx=(int)Math.round(x+radius*1.16*Math.cos(a)),yy=(int)Math.round(y+radius*1.16*Math.sin(a));
                    if(xx>=0&&xx<w&&yy>=0&&yy<h&&brown(px[yy*w+xx]))outside++;
                    xx=(int)Math.round(x+radius*.76*Math.cos(a));yy=(int)Math.round(y+radius*.76*Math.sin(a));
                    int rgb=px[yy*w+xx];if(!brown(rgb)&&GameSolver.skillPixelColor(rgb)==best)inside++;
                }
                if(outside>=8&&inside>=7)candidates.add(new double[]{x,y,best,n+outside*2+inside*2});
            }
        candidates.sort((a,b)->Double.compare(b[3],a[3]));
        for(double[] a:candidates){boolean near=false;for(Option o:options)if(Math.hypot(o.x-a[0],o.y-a[1])<radius*1.6){near=true;break;}
            if(!near)options.add(new Option((int)a[0],(int)a[1],(char)(int)a[2]));}
        // Return the modal even if its options are temporarily unreadable.
        return new Panel(Math.max(0,top-(int)(w*.15)),options);
    }
    public static Choice choose(Panel panel,GameSolver solver,GameSolver.Board board){
        if(panel==null||panel.options.size()!=3||board==null)return null;
        Choice best=null;
        for(Option option:panel.options){
            GameSolver.Shot shot=solver.bestSkillColor(board,option.color);
            if(shot==null)continue;
            if(best==null || (shot.matched>=3 && best.shot.matched<3) || ((shot.matched>=3)==(best.shot.matched>=3)&&shot.score>best.shot.score))best=new Choice(option,shot);
        }
        return best;
    }
}
