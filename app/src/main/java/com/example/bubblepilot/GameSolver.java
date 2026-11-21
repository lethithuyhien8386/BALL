package com.example.bubblepilot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java bubble-shooter vision and trajectory planner. No cloud/API calls.
 *
 * Coordinates are calibrated against the supplied 422x757 reference image.
 * Each captured image is sampled at pixel level; candidate shooting angles
 * is ray-traced, with side-wall rebounds and first-visible-ball occlusion.
 * Snap positions follow a staggered hexagonal grid. A shot is scored by balls
 * removed in >=3-color clusters plus disconnected balls that subsequently drop.
 *
 * Assumptions: three ball colors, fixed staggered grid, tap-to-aim, ordinary
 * match-3 bubble-shooter rules. Game variants need calibration/testing.
 */
public final class GameSolver {
    public static final int ROWS = 19;
    public static final int COLS = 10;

    private static final double[][] SAMPLE = {
        {0,0},{.22,0},{-.22,0},{0,.22},{0,-.22},
        {.45,0},{-.45,0},{0,.45},{0,-.45},
        {.35,.35},{-.35,.35},{.35,-.35},{-.35,-.35}
    };
    private static final double[] EDGEX = {1,0,-1,0,.7071,-.7071,-.7071,.7071};
    private static final double[] EDGEY = {0,1,0,-1,.7071,.7071,-.7071,-.7071};

    public static final class Configuration {
        public double topY = .1136;
        public double launchY = .7586;
        public double launchX = .490;
        public double boardLeft = .058;
        public double boardRight = .918;
        public double oddX = .1066;
        public double colPitch = .0845;
        public double rowPitch = .0408;
        public double radiusX = .038;
    }
    public static final class Ball {
        public final int row, col;
        public final char color;
        public final double x, y;
        public final int visiblePixels;
        Ball(int row, int col, char color, double x, double y, int visiblePixels) {
            this.row=row; this.col=col; this.color=color; this.x=x; this.y=y;
            this.visiblePixels=visiblePixels;
        }
    }
    public static final class Board {
        public final Ball[][] balls = new Ball[ROWS][COLS];
        public final int width, height;
        public final char shooter;
        public final double radius, topY, shooterX, shooterY, leftWall, rightWall;
        public final int count;
        public final long signature;
        private final Configuration cfg;
        Board(int w,int h, Configuration cfg, Ball[][] found, char shot, int n, long hash) {
            width=w; height=h; this.cfg=cfg; shooter=shot;count=n;signature=hash;
            radius=w*cfg.radiusX; topY=h*cfg.topY;
            shooterX=w*cfg.launchX; shooterY=h*cfg.launchY;
            leftWall=w*cfg.boardLeft+radius;rightWall=w*cfg.boardRight-radius;
            for (int r=0;r<ROWS;r++) System.arraycopy(found[r],0,balls[r],0,COLS);
        }
        double gridX(int r,int c) {
            return width*(cfg.oddX+cfg.colPitch*c+((r&1)==0?cfg.colPitch*.5:0));
        }
        double gridY(int r) {return height*(cfg.topY+r*cfg.rowPitch);}
        boolean exists(int r,int c) {return r>=0 && r<ROWS && c>=0 && c<((r&1)==0?9:10);}
    }
    public static final class Shot {
        public final int tapX, tapY;
        public final double angle, score;
        public final int matched, dropped;
        public final long stateSignature;
        Shot(int x,int y,double angle,double score,int matched,int dropped,long sig) {
            tapX=x;tapY=y;this.angle=angle;this.score=score;
            this.matched=matched;this.dropped=dropped;stateSignature=sig;
        }
    }
    private static final class Contact {
        double x,y, traveled;
        Ball target;
        Contact(double x,double y,double d,Ball target) {
            this.x=x;this.y=y;traveled=d;this.target=target;
        }
    }
    private static final class Placement {
        int row,col;
        double distance;
        Placement(int row,int col,double d) {this.row=row;this.col=col;distance=d;}
    }
    private static final class Score {
        double score;
        int matched,dropped;
        Score(double s,int m,int d) {score=s;matched=m;dropped=d;}
    }

    private final Configuration cfg;
    public GameSolver(Configuration cfg) { this.cfg=cfg; }

    // Pixel classifier deliberately avoids HSV float math for per-frame speed.
    private static char pixelClass(int rgb) {
        int r=(rgb>>>16)&255, g=(rgb>>>8)&255, b=rgb&255;
        int max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
        int diff=max-min;
        if(max<70 || diff*100<max*71 || diff==0) return ' ';
        // Integer HSV hue (0..360 degrees) is robust to shaded circles.
        int h;
        if(max==r) h=(60*(g-b))/diff;
        else if(max==g)h=120+(60*(b-r))/diff;
        else h=240+(60*(r-g))/diff;
        if(h<0)h+=360;
        if((h<18 || h>343) && r*100>g*135 && r*100>b*132)return 'R';
        if(h>=18 && h<53 && r*100>g*113 && r*100>b*140)return 'O';
        if(h>=70 && h<=145 && g*100>r*120 && g*100>b*120)return 'G';
        return ' ';
    }
    private static int pix(int[] data,int w,int h,double x,double y) {
        int ix=(int)Math.round(x), iy=(int)Math.round(y);
        if (ix<0 || ix>=w || iy<0 || iy>=h) return 0;
        return data[iy*w+ix];
    }
    private static int difference(int a,int b) {
        return Math.abs(((a>>>16)&255)-((b>>>16)&255))
                +Math.abs(((a>>>8)&255)-((b>>>8)&255))
                +Math.abs((a&255)-(b&255));
    }
    private static char patchColor(int[] data,int w,int h,double x,double y,double r) {
        int red=0,orange=0,green=0;
        for (double[] pos : SAMPLE) {
            char c=pixelClass(pix(data,w,h,x+pos[0]*r,y+pos[1]*r));
            if(c=='R') red++; else if(c=='O')orange++; else if(c=='G')green++;
        }
        int max=Math.max(red,Math.max(orange,green));
        if(max<7) return ' ';
        return red==max?'R':orange==max?'O':'G';
    }
    private static boolean hasCircularEdge(int[] data,int w,int h,double x,double y,double r) {
        int contrast=0;
        for(int i=0;i<EDGEX.length;i++) {
            int inside=pix(data,w,h,x+EDGEX[i]*r*.58,y+EDGEY[i]*r*.58);
            int outside=pix(data,w,h,x+EDGEX[i]*r*1.20,y+EDGEY[i]*r*1.20);
            contrast+=difference(inside,outside);
        }
        return contrast>EDGEX.length*105;
    }
    private static int visiblePixels(int[] data,int w,int h,double cx,double cy,double r,char color,
                                     Ball[][] board,int row,int col) {
        int count=0;
        int minX=(int)Math.max(0,cx-r*.84),maxX=(int)Math.min(w-1,cx+r*.84);
        int minY=(int)Math.max(0,cy-r*.84),maxY=(int)Math.min(h-1,cy+r*.84);
        double rr=r*r*.84*.84;
        for(int y=minY;y<=maxY;y++) {
            double dy=y-cy;
            int off=y*w;
            for(int x=minX;x<=maxX;x++) {
                double dx=x-cx;
                if(dx*dx+dy*dy>rr || pixelClass(data[off+x])!=color) continue;
                // Do not double-credit parts of circles overlapped by a neighbor.
                boolean occluded=false;
                if (board!=null) {
                    for(int r2=Math.max(0,row-1);r2<=Math.min(ROWS-1,row+1) && !occluded;r2++) {
                        for(int c2=Math.max(0,col-1);c2<=Math.min(COLS-1,col+1);c2++) {
                            Ball other=board[r2][c2];
                            if(other==null || (r2==row && c2==col))continue;
                            double ox=x-other.x,oy=y-other.y;
                            if(ox*ox+oy*oy < r*r*.72) {occluded=true;break;}
                        }
                    }
                }
                if(!occluded)count++;
            }
        }
        return count;
    }

    public Board detect(int[] pixels,int w,int h) {
        if(pixels==null || w<180 || h<350 || pixels.length!=w*h) return null;
        double radius=w*cfg.radiusX;
        Ball[][] found=new Ball[ROWS][COLS];
        int count=0, emptyRows=0;
        long hash=0xcbf29ce484222325L;
        for(int row=0;row<ROWS;row++) {
            double y=h*(cfg.topY+row*cfg.rowPitch);
            if(y > h*cfg.launchY-radius*4.0)break;
            int rowCount=0;
            int cols=(row&1)==0?9:10;
            for(int col=0;col<cols;col++) {
                double x=w*(cfg.oddX+col*cfg.colPitch+((row&1)==0?cfg.colPitch*.5:0));
                char color=patchColor(pixels,w,h,x,y,radius);
                if(color!=' ' && hasCircularEdge(pixels,w,h,x,y,radius)) {
                    int exposed=visiblePixels(pixels,w,h,x,y,radius,color,null,row,col);
                    if(exposed>=55) {
                        found[row][col]=new Ball(row,col,color,x,y,exposed);
                        count++;rowCount++;
                    }
                }
                char cell=found[row][col]==null?'_':found[row][col].color;
                hash^=(cell+31L*row+col*131L);hash*=0x100000001b3L;
            }
            if(rowCount==0) emptyRows++; else emptyRows=0;
            if(emptyRows>=2 && row>=3)break; // Discard background below the board.
        }
        // Recalculate scored visible area using the full detected grid.
        // Overlay pixels from other bubbles never contribute to a target's score.
        for(int row=0;row<ROWS;row++)for(int col=0;col<COLS;col++) {
            Ball b=found[row][col];
            if(b!=null) {
                int exposed=visiblePixels(pixels,w,h,b.x,b.y,radius,b.color,found,row,col);
                found[row][col]=new Ball(row,col,b.color,b.x,b.y,exposed);
            }
        }
        char shooter=patchColor(pixels,w,h,w*cfg.launchX,h*cfg.launchY,radius);
        hash^=shooter;hash*=0x100000001b3L;
        if(count<1 || shooter==' ') return null;
        return new Board(w,h,cfg,found,shooter,count,hash);
    }

    private static Contact trace(Board board, List<Ball> all, double angle) {
        double dx=Math.sin(Math.toRadians(angle)),dy=-Math.cos(Math.toRadians(angle));
        double x=board.shooterX,y=board.shooterY,total=0;
        double effectiveR=board.radius*2.06;
        for(int bounced=0;bounced<8;bounced++) {
            double tWall=Double.POSITIVE_INFINITY;
            if(dx>1e-7)tWall=(board.rightWall-x)/dx;
            else if(dx < -1e-7)tWall=(board.leftWall-x)/dx;
            double tTop=(board.topY-y)/dy;
            if(tTop<0) return null;
            double limit=Math.min(tTop,tWall);
            if(limit<0) return null;
            double best=limit;
            Ball target=null;
            for(Ball ball : all) {
                double qx=x-ball.x,qy=y-ball.y;
                double b=2*(qx*dx+qy*dy);
                double c=qx*qx+qy*qy-effectiveR*effectiveR;
                double disc=b*b-4*c;
                if(disc<0)continue;
                double t=(-b-Math.sqrt(disc))*.5;
                if(t>=-1e-6 && t<best) { best=Math.max(0,t);target=ball; }
            }
            double hitX=x+best*dx,hitY=y+best*dy;
            if(target!=null) return new Contact(hitX,hitY,total+best,target);
            total+=limit;
            if(tTop<=tWall) return new Contact(hitX,board.topY,total,null);
            x=hitX;y=hitY;
            dx=-dx;
            x=Math.max(board.leftWall+1e-4,Math.min(board.rightWall-1e-4,x));
        }
        return null;
    }

    private static List<Ball> asList(Board b) {
        ArrayList<Ball> out=new ArrayList<>();
        for(Ball[] row:b.balls) for(Ball ball:row)if(ball!=null)out.add(ball);
        return out;
    }
    private static boolean adjacent(Board b,int r,int c,int r2,int c2) {
        if(Math.abs(r-r2)>1 || Math.abs(c-c2)>1)return false;
        double dx=b.gridX(r,c)-b.gridX(r2,c2),dy=b.gridY(r)-b.gridY(r2);
        return dx*dx+dy*dy < Math.pow(b.width*b.cfg.colPitch*1.11,2);
    }
    private static Placement snap(Board b,Contact contact) {
        Placement best=null;
        for(int r=0;r<ROWS;r++) {
            if(b.gridY(r)>b.shooterY-b.radius*3)break;
            for(int c=0;c<COLS;c++) {
                if(!b.exists(r,c) || b.balls[r][c]!=null)continue;
                double x=b.gridX(r,c),y=b.gridY(r);
                double dist=Math.hypot(x-contact.x,y-contact.y);
                if(dist>b.width*b.cfg.colPitch*.90)continue;
                boolean connected=(r==0);
                if(!connected) for(int rr=Math.max(0,r-1);rr<=Math.min(ROWS-1,r+1);rr++) {
                    for(int cc=Math.max(0,c-1);cc<=Math.min(COLS-1,c+1);cc++) {
                        if(b.balls[rr][cc]!=null && adjacent(b,r,c,rr,cc))connected=true;
                    }
                }
                if(!connected)continue;
                // Contact should be close to the collided visible ball.
                if(contact.target!=null && !adjacent(b,r,c,contact.target.row,contact.target.col)) continue;
                if(best==null || dist<best.distance) best=new Placement(r,c,dist);
            }
        }
        return best;
    }
    private static List<int[]> neighbors(Board b,int r,int c) {
        List<int[]> list=new ArrayList<>(6);
        for(int rr=Math.max(0,r-1);rr<=Math.min(ROWS-1,r+1);rr++) {
            for(int cc=Math.max(0,c-1);cc<=Math.min(COLS-1,c+1);cc++) {
                if((r!=rr || c!=cc) && b.exists(rr,cc) && adjacent(b,r,c,rr,cc))
                    list.add(new int[]{rr,cc});
            }
        }
        return list;
    }
    private static Score evaluate(Board b,Placement p) {
        char[][] cells=new char[ROWS][COLS];
        for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++) {
            cells[r][c]=b.balls[r][c]==null?' ':b.balls[r][c].color;
        }
        cells[p.row][p.col]=b.shooter;
        ArrayDeque<int[]> queue=new ArrayDeque<>();
        boolean[][] seen=new boolean[ROWS][COLS];
        ArrayList<int[]> matching=new ArrayList<>();
        queue.add(new int[]{p.row,p.col});seen[p.row][p.col]=true;
        int visible=0;
        while(!queue.isEmpty()) {
            int[] cur=queue.removeFirst();matching.add(cur);
            Ball bb=b.balls[cur[0]][cur[1]];
            if(bb!=null) visible+=bb.visiblePixels;
            for(int[] neighbor:neighbors(b,cur[0],cur[1])) {
                int r=neighbor[0],c=neighbor[1];
                if(!seen[r][c] && cells[r][c]==b.shooter) {
                    seen[r][c]=true;queue.add(neighbor);
                }
            }
        }
        int matched=matching.size();
        if(matched>=3) {
            for(int[] c:matching)cells[c[0]][c[1]]=' ';
            seen=new boolean[ROWS][COLS];
            queue.clear();
            for(int c=0;c<9;c++)if(cells[0][c]!=' ') {
                queue.add(new int[]{0,c});seen[0][c]=true;
            }
            while(!queue.isEmpty()) {
                int[] at=queue.removeFirst();
                for(int[] near:neighbors(b,at[0],at[1])) {
                    int r=near[0],c=near[1];
                    if(!seen[r][c] && cells[r][c]!=' ') {
                        seen[r][c]=true;queue.add(near);
                    }
                }
            }
            int dropped=0, dropPixels=0;
            for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++) {
                if(cells[r][c]!=' ' && !seen[r][c]) {
                    dropped++;
                    Ball bb=b.balls[r][c]; if(bb!=null)dropPixels+=bb.visiblePixels;
                }
            }
            return new Score(matched*110+dropped*170+visible*.10+dropPixels*.10,matched,dropped);
        }
        // If no cluster pops, prepare a future combo but avoid low hanging bubbles.
        int same=matching.size()-1;
        return new Score(same*20 - p.row*1.7 - 18,0,0);
    }
    private static Shot shotAtAngle(Board b,List<Ball> all,double angle) {
        if(Math.abs(angle)>77.5)return null;
        Contact contact=trace(b,all,angle);
        if(contact==null)return null;
        Placement place=snap(b,contact);
        if(place==null)return null;
        Score score=evaluate(b,place);
        // Prefer maximum points, then shorter and simpler trajectories on ties.
        double finalScore=score.score-contact.traveled*.001-Math.abs(angle)*.002;
        double aiming=b.width*.4265;
        int x=(int)Math.round(b.shooterX+Math.sin(Math.toRadians(angle))*aiming);
        int y=(int)Math.round(b.shooterY-Math.cos(Math.toRadians(angle))*aiming);
        x=Math.max(2,Math.min(b.width-3,x));
        y=Math.max((int)(b.height*.10),Math.min(b.height-3,y));
        return new Shot(x,y,angle,finalScore,score.matched,score.dropped,b.signature);
    }
    /** Validate and score an Astra suggestion using the same local collision physics. */
    public Shot shotAtTap(Board b,int x,int y) {
        if(b==null || x<2 || x>=b.width-2 || y<2 || y>=b.shooterY-15)return null;
        double angle=Math.toDegrees(Math.atan2(x-b.shooterX,b.shooterY-y));
        return shotAtAngle(b,asList(b),angle);
    }
    public Shot bestShot(Board b) {
        if(b==null)return null;
        List<Ball> all=asList(b);
        Shot best=null;
        for(int a=-155;a<=155;a++) {  // coarse search, half-degree steps
            Shot candidate=shotAtAngle(b,all,a*.5);
            if(candidate!=null && (best==null || candidate.score>best.score))best=candidate;
        }
        if(best==null)return null;
        // Subpixel aiming refinement around the best trajectory.
        double coarse=best.angle;
        for(int n=-5;n<=5;n++) {
            Shot candidate=shotAtAngle(b,all,coarse+n*.1);
            if(candidate!=null && candidate.score>best.score)best=candidate;
        }
        return best;
    }
}
