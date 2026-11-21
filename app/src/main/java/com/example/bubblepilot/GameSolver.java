package com.example.bubblepilot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure Java bubble-shooter vision and trajectory planner. No cloud/API calls.
 *
 * Coordinates are calibrated against the supplied 422x757 reference image.
 * Each captured image is sampled at pixel level; candidate shooting angles
 * is ray-traced, with side-wall rebounds and first-ball collision.
 * Snap positions follow a staggered hexagonal grid. A shot is scored by balls
 * removed in >=3-color clusters plus disconnected balls that subsequently drop.
 *
 * Assumptions: a staggered hexagonal grid and tap-to-aim rules. It recognizes
 * red, orange, green, blue/cyan, purple, plus neutral special/obstacle balls.
 * Grid placement is estimated from each screenshot when it changes.
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
        // Filled from screen pixels when distinct arena edges are visible.
        boolean detectedArena = false;
        // The game shifts its checkerboard by one row during play. In some
        // frames the ceiling row has 10 bubbles, in others only 9.
        boolean narrowTop = true;
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
        public final double shooterConfidence;
        public final int count;
        public final long signature;
        private final Configuration cfg;
        public final int ceilingRow;
        Board(int w,int h, Configuration cfg, Ball[][] found, char shot, int n, long hash) {
            this(w,h,cfg,found,shot,n,hash,w*cfg.launchX,h*cfg.launchY,1.0);
        }
        Board(int w,int h, Configuration cfg, Ball[][] found, char shot, int n, long hash,
              double launchX,double launchY,double confidence) {
            width=w; height=h; this.cfg=cfg; shooter=shot;count=n;signature=hash;
            radius=w*cfg.radiusX; topY=h*cfg.topY;
            shooterX=launchX; shooterY=launchY;shooterConfidence=confidence;
            // The arena shifts between game scenes. A fixed fraction of screen
            // width placed the left rebound wall about 13 px too far left in
            // the second supplied screenshot. Estimate ball-center wall limits
            // from the calibrated staggered grid; keep a safe fallback.
            double gridLeft=w*cfg.oddX-radius*.24;
            double gridRight=w*(cfg.oddX+9*cfg.colPitch)+radius*.24;
            double fallbackLeft=w*cfg.boardLeft+radius;
            double fallbackRight=w*cfg.boardRight-radius;
            if(cfg.detectedArena && fallbackLeft>radius && fallbackRight<w-radius &&
               fallbackRight-fallbackLeft>w*.62) {
                // Exact translucent arena edge measured across thousands of
                // screen pixels, then inset by the projectile radius.
                leftWall=fallbackLeft;rightWall=fallbackRight;
            } else if(gridRight-gridLeft>w*.62 && gridRight-gridLeft<w*.94 &&
                      gridLeft>radius*.6 && gridRight<w-radius*.6) {
                leftWall=gridLeft;rightWall=gridRight;
            } else {leftWall=fallbackLeft;rightWall=fallbackRight;}
            for (int r=0;r<ROWS;r++) System.arraycopy(found[r],0,balls[r],0,COLS);
            int first=0;
            while(first<ROWS) {
                boolean nonEmpty=false;
                for(Ball ball:found[first])if(ball!=null){nonEmpty=true;break;}
                if(nonEmpty)break;
                first++;
            }
            ceilingRow=first==ROWS?0:first;
        }
        double gridX(int r,int c) {
            return width*(cfg.oddX+cfg.colPitch*c+((r&1)==(cfg.narrowTop?0:1)?cfg.colPitch*.5:0));
        }
        double gridY(int r) {return height*(cfg.topY+r*cfg.rowPitch);}
        boolean exists(int r,int c) {return r>=0 && r<ROWS && c>=0 &&
                        c<((r&1)==(cfg.narrowTop?0:1)?9:10);}
    }
    public static final class Shot {
        public final int tapX,tapY;
        public final double angle,score;
        public final int matched,dropped;
        public final long stateSignature;
        public final int bounces,hitRow,hitCol,snapRow,snapCol;
        /** 0 to 1: portion of simulated small tap/wall perturbations retaining the landing cell. */
        public final double reliability;
        Shot(int x,int y,double angle,double score,int matched,int dropped,long sig,
             int bounces,int hitRow,int hitCol,int snapRow,int snapCol,double reliability) {
            tapX=x;tapY=y;this.angle=angle;this.score=score;
            this.matched=matched;this.dropped=dropped;stateSignature=sig;
            this.bounces=bounces;this.hitRow=hitRow;this.hitCol=hitCol;
            this.snapRow=snapRow;this.snapCol=snapCol;this.reliability=reliability;
        }
        Shot rerank(double newScore,double stability) {
            return new Shot(tapX,tapY,angle,newScore,matched,dropped,stateSignature,
                    bounces,hitRow,hitCol,snapRow,snapCol,stability);
        }
    }
    private static final class Contact {
        final double x,y,traveled,dx,dy;
        final int bounces;
        final Ball target;
        Contact(double x,double y,double d,Ball target,int bounces,double dx,double dy) {
            this.x=x;this.y=y;traveled=d;this.target=target;
            this.bounces=bounces;this.dx=dx;this.dy=dy;
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
    private boolean allowSpecialLauncher=false;
    public GameSolver specialLauncher(boolean allow){allowSpecialLauncher=allow;return this;}
    private String diagnosis = "CHỜ KHUNG HÌNH";
    private static volatile SavedLayout savedLayout;
    private static final class SavedLayout {
        final int w,h;
        final double topY,oddX,colPitch,rowPitch,radiusX;
        final boolean narrowTop;
        final int previousCount;
        SavedLayout(int w,int h,Configuration c,int count) {
            previousCount=count;
            this.w=w;this.h=h;topY=c.topY;oddX=c.oddX;
            colPitch=c.colPitch;rowPitch=c.rowPitch;radiusX=c.radiusX;
            narrowTop=c.narrowTop;
        }
        Configuration apply(Configuration input) {
            Configuration result=copy(input);
            result.topY=topY;result.oddX=oddX;
            result.colPitch=colPitch;result.rowPitch=rowPitch;result.radiusX=radiusX;
            result.narrowTop=narrowTop;
            return result;
        }
    }
    public GameSolver(Configuration cfg) { this.cfg=cfg; }
    public String diagnosis() {return diagnosis;}
    public static void resetCalibration() {savedLayout=null;}
    private static Configuration copy(Configuration a) {
        Configuration b=new Configuration();
        b.topY=a.topY;b.launchY=a.launchY;b.launchX=a.launchX;
        b.boardLeft=a.boardLeft;b.boardRight=a.boardRight;
        b.oddX=a.oddX;b.colPitch=a.colPitch;b.rowPitch=a.rowPitch;
        b.radiusX=a.radiusX;b.detectedArena=a.detectedArena;b.narrowTop=a.narrowTop;
        return b;
    }

    // Integer HSV classifier. Includes newly observed violet and cyan bubbles.
    // A pale circle with low saturation is treated as a non-matching obstacle.
    private static char pixelClass(int rgb) {
        int r=(rgb>>>16)&255, g=(rgb>>>8)&255, b=rgb&255;
        int max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
        int diff=max-min;
        if (max>150 && diff*100<max*18) return 'W';
        if (max<70 || diff*100<max*30 || diff==0) return ' ';
        int h;
        if(max==r)h=60*(g-b)/diff;
        else if(max==g)h=120+60*(b-r)/diff;
        else h=240+60*(r-g)/diff;
        if(h<0)h+=360;
        if (h<18 || h>=345) return 'R';
        if (h>=18 && h<41) return 'O';
        if (h>=41 && h<68) return 'Y';
        if (h>=68 && h<164) return 'G';
        if (h>=164 && h<252 && b*100>g*107) return 'B';
        if (h>=252 && h<286) return 'V';
        if (h>=286 && h<345) return 'P';
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
        final char[] palette={'R','O','Y','G','B','V','P','W'};
        int[] counts=new int[palette.length];
        for(double[] pos:SAMPLE) {
            char c=pixelClass(pix(data,w,h,x+pos[0]*r,y+pos[1]*r));
            for(int i=0;i<palette.length;i++)if(c==palette[i]){counts[i]++;break;}
        }
        int best=-1,second=0;
        for(int i=0;i<counts.length;i++) {
            if(best<0 || counts[i]>counts[best]) {second=best<0?0:counts[best];best=i;}
            else if(counts[i]>second)second=counts[i];
        }
        if(best<0 || counts[best]<(palette[best]=='W'?5:6) || counts[best]<second+2)return ' ';
        return palette[best];
    }
    private static int circularQuality(int[] data,int w,int h,double x,double y,double r) {
        int contrast=0,strongEdges=0;
        boolean leftEdge=false,rightEdge=false,verticalEdge=false;
        for(int i=0;i<EDGEX.length;i++) {
            int inside=pix(data,w,h,x+EDGEX[i]*r*.58,y+EDGEY[i]*r*.58);
            int outside=pix(data,w,h,x+EDGEX[i]*r*1.20,y+EDGEY[i]*r*1.20);
            int delta=difference(inside,outside);
            contrast+=delta;
            if(delta>70) {
                strongEdges++;
                if(i==0)rightEdge=true;
                if(i==2)leftEdge=true;
                if(i==1 || i==3)verticalEdge=true;
            }
        }
        // A horizontal border in the scenery can create a huge contrast sum,
        // but a real circular bubble has visible edges in most directions.
        return strongEdges>=5 && leftEdge && rightEdge && verticalEdge &&
               contrast>EDGEX.length*77 ? strongEdges : 0;
    }
    private static boolean hasCircularEdge(int[] data,int w,int h,double x,double y,double r) {
        return circularQuality(data,w,h,x,y,r)>0;
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

    private static final class LayoutCandidate {
        final Configuration layout;
        final double points;
        LayoutCandidate(Configuration layout,double points) {this.layout=layout;this.points=points;}
    }
    // Template fit is performed only on the top section where bubbles may exist;
    // green grass and water beneath the board must not be mistaken for bubbles.
    private static double fit(int[] px,int w,int h,Configuration c,int maxRows) {
        double radius=w*c.radiusX;
        int found=0,top=0;
        double score=0;
        for(int row=0;row<maxRows;row++) {
            double y=h*(c.topY+row*c.rowPitch);
            if(y>h*.52)break;
            for(int col=0;col<((row&1)==(c.narrowTop?0:1)?9:10);col++) {
                double x=w*(c.oddX+c.colPitch*col+
                        ((row&1)==(c.narrowTop?0:1)?c.colPitch*.5:0));
                if(x<radius*1.1 || x>w-radius*1.1)continue;
                if(patchColor(px,w,h,x,y,radius)!=' ') {
                    int quality=circularQuality(px,w,h,x,y,radius);
                    if(quality>=5) {
                        found++;if(row<3)top++;
                        score+=1.0+(maxRows-row)*.03+(quality-5)*.19;
                    }
                }
            }
        }
        // At the beginning of a game most rows are filled. Give the upper rows
        // greater importance to reject misleading colored scenery.
        return score+top*.08+(found>=4?1:0);
    }
    private static Configuration findLayoutLegacy(int[] px,int w,int h,Configuration original) {
        LayoutCandidate winner=null;
        // Geometry is relative to the screen size and game artwork. Shifted HUDs
        // move the whole grid even though the launcher stays near the bottom.
        double[] xPitches={.0805,.0830,.0855};
        double[] yPitches={.0394,.0410};
        for(double pitch:xPitches)for(double yp:yPitches)
            for(double x=.140;x<=.1951;x+=.005)
                for(double top=.102;top<=.1711;top+=.005) {
                    Configuration c=copy(original);
                    c.narrowTop=true;
                    c.colPitch=pitch;c.rowPitch=yp;c.oddX=x-pitch*.5;
                    c.topY=top;
                    double score=fit(px,w,h,c,7);
                    if(winner==null || score>winner.points)
                        winner=new LayoutCandidate(c,score);
                }
        if(winner==null || winner.points<5)return null;
        Configuration c=winner.layout;
        double best=winner.points;
        final double seedX=c.oddX+c.colPitch*.5;
        final double seedTop=c.topY;
        final double seedColPitch=c.colPitch;
        final double seedRowPitch=c.rowPitch;
        // Pixel-level refinement finds a center more precisely than the coarse
        // scan; this matters for placing a projected ball beside a blocker.
        for(double pxPitch=seedColPitch-.002;pxPitch<=seedColPitch+.0021;pxPitch+=.001)
            for(double pyPitch=seedRowPitch-.0014;pyPitch<=seedRowPitch+.00141;pyPitch+=.0007)
                for(double xx=seedX-.006;xx<=seedX+.0061;xx+=.002)
                    for(double yy=seedTop-.006;yy<=seedTop+.0061;yy+=.002) {
                        Configuration ref=copy(original);
                        ref.narrowTop=true;
                        ref.colPitch=pxPitch;ref.rowPitch=pyPitch;ref.topY=yy;
                        ref.oddX=xx-pxPitch*.5;
                        double score=fit(px,w,h,ref,8);
                        if(score>best){best=score;c=ref;}
                    }
        return c;
    }
    private Configuration findLayout(int[] px,int w,int h,Configuration original) {
        // Video regression: actual arena in the 720x1292 recording is narrower
        // than the screenshot-only fixtures. Derive pitch from VISIBLE walls,
        // not a fixed fraction of screen width. The 10-column row spans one
        // arena width, and staggered centers alternate by half a pitch.
        double[] edges=detectArenaEdges(px,w,h,w*.023);
        // Preserve the proven original artwork calibration when the arena
        // occupies most of the image; use the adaptive model for recordings
        // with a narrower inset arena and dynamic 9/10-row orientation.
        if(edges!=null && (edges[1]-edges[0])/w>.79) {
            Configuration old=findLayoutLegacy(px,w,h,original);
            if(old!=null && detectAt(px,w,h,old).count>=55)return old;
        }
        LayoutCandidate winner=null;
        if(edges!=null) {
            double span=edges[1]-edges[0];
            for(double pitchScale:new double[]{.975,.99,1.005,1.02}) {
                double pitch=span/10.0*pitchScale;
                for(boolean narrow:new boolean[]{true,false})
                    for(double offset:new double[]{.47,.54,.61,.68,.75})
                        for(double rowFactor:new double[]{.84,.88,.92})
                            for(double top=.105;top<=.207;top+=.004) {
                            Configuration c=copy(original);
                            c.narrowTop=narrow;
                            c.colPitch=pitch/w;c.rowPitch=pitch*rowFactor/h;
                            c.oddX=(edges[0]+pitch*offset)/w;
                            c.radiusX=pitch*.465/w;c.topY=top;
                            c.boardLeft=edges[0]/w;c.boardRight=edges[1]/w;
                            c.detectedArena=true;
                            double score=fit(px,w,h,c,9);
                            if(winner==null || score>winner.points)
                                winner=new LayoutCandidate(c,score);
                        }
            }
        }
        // When arena edges are hidden, search an expanded set of dimensions.
        // Do not assume screenshots all use the same artwork scale.
        if(winner==null || winner.points<13) {
            double[] pitches={.073,.078,.083,.088};
            double[] rowPitches={.034,.038,.042};
            for(double pitch:pitches)for(double yp:rowPitches)
                for(boolean narrow:new boolean[]{true,false})
                    for(double x=.12;x<=.2351;x+=.009)
                        for(double top=.105;top<=.2051;top+=.008) {
                        Configuration c=copy(original);
                        c.narrowTop=narrow;
                        c.colPitch=pitch;c.rowPitch=yp;c.oddX=x-pitch*.5;
                        c.radiusX=pitch*.465;c.topY=top;
                        double score=fit(px,w,h,c,9);
                        if(winner==null || score>winner.points)
                            winner=new LayoutCandidate(c,score);
                    }
        }
        if(winner==null || winner.points<8)return null;
        Configuration c=winner.layout;
        double best=winner.points;
        double seedX=c.oddX,seedTop=c.topY;
        double seedCP=c.colPitch,seedRP=c.rowPitch;
        for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)
            for(int dc=-1;dc<=1;dc++)for(int dr=-1;dr<=1;dr++) {
                Configuration ref=copy(c);
                ref.oddX=seedX+dx*1.1/w;
                ref.topY=seedTop+dy*1.1/h;
                ref.colPitch=seedCP+dc*.45/w;
                ref.rowPitch=seedRP+dr*.45/h;
                double score=fit(px,w,h,ref,10);
                if(score>best){best=score;c=ref;}
            }
        return c;
    }
    /**
     * Detect the actual side walls from their vertical pixel edges in the
     * empty middle of the playfield. In the supplied screenshots the visible
     * edge is x=23/386 (original) and x=38/401 (shifted scene). This is much
     * more accurate for bank shots than assuming the board always occupies a
     * fixed percentage of the display width.
     */
    private static double[] detectArenaEdges(int[] pixels,int w,int h,double radius) {
        int y0=(int)(h*.46),y1=(int)(h*.66),step=Math.max(2,h/220);
        int[] limits={Math.max(1,(int)(w*.02)),Math.min(w-3,(int)(w*.25)),
                      Math.max(1,(int)(w*.74)),Math.min(w-3,(int)(w*.99))};
        double[][] peaks=new double[2][w];
        for(int side=0;side<2;side++)for(int x=limits[side*2];x<=limits[side*2+1];x++) {
            long total=0;int n=0;
            for(int y=y0;y<y1;y+=step) {
                total+=difference(pixels[y*w+x],pixels[y*w+x+1]);n++;
            }
            peaks[side][x]=total/(3.0*Math.max(1,n));
        }
        // Some screen recordings have an OUTER frame around the actual arena.
        // The largest isolated edge is then NOT the wall! Pair candidate lines
        // symmetrically around the launcher and reject implausible widths.
        double best=-1;int left=-1,right=-1;
        for(int l=limits[0];l<=limits[1];l++) {
            if(peaks[0][l]<11)continue;
            for(int r=limits[2];r<=limits[3];r++) {
                if(peaks[1][r]<11)continue;
                double span=r-l,center=(l+r)*.5;
                if(span<w*.68 || span>w*.89 || Math.abs(center-w*.495)>w*.038 ||
                   span<radius*7 || l+radius>=r-radius)continue;
                double quality=Math.min(peaks[0][l],peaks[1][r])
                               +.35*(peaks[0][l]+peaks[1][r]);
                quality-=Math.abs(center-w*.495)*.09;
                if(quality>best) {best=quality;left=l;right=r;}
            }
        }
        return left<0?null:new double[]{left,right};
    }
    private static final class LauncherBall {
        final double x,y,confidence;
        final char color;
        LauncherBall(double x,double y,char color,double confidence) {
            this.x=x;this.y=y;this.color=color;this.confidence=confidence;
        }
    }
    private LauncherBall findLauncher(int[] pixels,int w,int h,Configuration cfg,
                                             Ball[][] visibleGrid) {
        double r=w*cfg.radiusX;
        // The video shows the loaded ball 20-30px ABOVE the old launchY.
        // Search the narrow central gun channel, ignoring the next-ball slot
        // to the right and the blue dragon sprite below the actual launcher.
        double cx=w*cfg.launchX;
        LauncherBall best=null;double qualityBest=-1;
        int[] colorCount=new int[128];
        for(Ball[] row:visibleGrid)for(Ball ball:row)
            if(ball!=null && ball.color<128)colorCount[ball.color]++;
        // The cannon rotates: the *loaded* projectile may sit 1-2 radii
        // left/right of the dragon's center. The previous +/-0.45 radius
        // corridor missed those frames and picked cyan parts of the sprite.
        // The next-ball preview is lower-right; do not search that region.
        int x0=(int)Math.max(r,cx-r*2.15),x1=(int)Math.min(w-r,cx+r*2.15);
        int y0=(int)(h*(cfg.launchY-.054)),y1=(int)(h*(cfg.launchY+.016));
        for(int y=y0;y<=y1;y+=2)for(int x=x0;x<=x1;x+=2) {
            char color=patchColor(pixels,w,h,x,y,r);
            if(color==' ' || (color=='W' && !allowSpecialLauncher))continue;
            int match=0,second=0;
            for(double[] p:SAMPLE) {
                char cls=pixelClass(pix(pixels,w,h,x+p[0]*r,y+p[1]*r));
                if(cls==color)match++;
                else if(cls!=' ')second++;
            }
            int circular=circularQuality(pixels,w,h,x,y,r);
            if(match<9 || circular<6)continue;
            // Prefer the upper loaded ball, not the multicolor dragon's face.
            // Strongly prefer the actual round projectile in the upper
            // launcher ring, not the character's cyan neck below. The top
            // sphere may move sideways when the cannon points left/right.
            double q=match*1.3+circular*1.1-second*.7
                    -Math.abs(x-w*.50)*.025
                    -Math.abs(y-h*(cfg.launchY-.017))*.040;
            if(colorCount[color]>0)q+=3.5;
            else if(color=='B')q-=2.0; // animated blue cannon neck
            if(q>qualityBest) {
                qualityBest=q;
                best=new LauncherBall(x,y,color,
                        Math.min(1.0,(match/13.0*.55+circular/8.0*.45)));
            }
        }
        if(best==null && allowSpecialLauncher){
            for(int y=y0;y<=y1;y+=2)for(int x=x0;x<=x1;x+=2){
                int circ=circularQuality(pixels,w,h,x,y,r);
                if(circ<7)continue;
                int colored=0;java.util.HashSet<Character> palette=new java.util.HashSet<>();
                for(double[] off:SAMPLE){char c=pixelClass(pix(pixels,w,h,x+off[0]*r,y+off[1]*r));if(c!=' '){colored++;palette.add(c);}}
                if(colored<11 || palette.size()<3)continue;
                double q=circ*2-Math.abs(x-cx)*.04-Math.abs(y-h*(cfg.launchY-.017))*.08;
                if(q>qualityBest){qualityBest=q;best=new LauncherBall(x,y,'W',.86);}
            }
        }
        return best;
    }
    private Board detectAt(int[] pixels,int w,int h,Configuration c) {
        double radius=w*c.radiusX;
        Configuration measured=copy(c);
        double[] walls=detectArenaEdges(pixels,w,h,radius);
        if(walls!=null) {
            // Validate detected boundaries against the calibrated ball grid;
            // avoid locking onto a bright vertical decorative stripe.
            double leftCell=w*c.oddX;
            double rightCell=w*(c.oddX+9*c.colPitch);
            if(Math.abs(walls[0]+radius-leftCell)<radius*2.4 &&
               Math.abs(walls[1]-radius-rightCell)<radius*2.4) {
                measured.boardLeft=walls[0]/w;
                measured.boardRight=walls[1]/w;
                measured.detectedArena=true;
            }
        }
        Ball[][] found=new Ball[ROWS][COLS];
        int count=0, emptyRows=0;
        long hash=0xcbf29ce484222325L;
        for(int row=0;row<ROWS;row++) {
            double y=h*(c.topY+row*c.rowPitch);
            if(y>h*c.launchY-radius*4.0)break;
            int rowCount=0;
            for(int col=0;col<((row&1)==(c.narrowTop?0:1)?9:10);col++) {
                double x=w*(c.oddX+c.colPitch*col+
                        ((row&1)==(c.narrowTop?0:1)?c.colPitch*.5:0));
                char color=patchColor(pixels,w,h,x,y,radius);
                if(color!=' ' && hasCircularEdge(pixels,w,h,x,y,radius)) {
                    int exposed=visiblePixels(pixels,w,h,x,y,radius,color,null,row,col);
                    if(exposed>=Math.max(15,(int)(w*w*.0003))) {
                        found[row][col]=new Ball(row,col,color,x,y,exposed);
                        count++;rowCount++;
                    }
                }
                char cell=found[row][col]==null?'_':found[row][col].color;
                hash^=(cell+31L*row+col*131L);hash*=0x100000001b3L;
            }
            if(rowCount==0)emptyRows++;else emptyRows=0;
            if(emptyRows>=2 && row>=3)break;
        }
        for(int row=0;row<ROWS;row++)for(int col=0;col<COLS;col++) {
            Ball b=found[row][col];
            if(b!=null) {
                int exposed=visiblePixels(pixels,w,h,b.x,b.y,radius,b.color,found,row,col);
                found[row][col]=new Ball(row,col,b.color,b.x,b.y,exposed);
            }
        }
        LauncherBall launcher=findLauncher(pixels,w,h,c,found);
        char shooter=launcher==null?' ':launcher.color;
        // Never mistake the dragon's cyan body for a loaded blue ball when
        // the entire visible board has no blue bubbles.
        if(shooter!=' ') {
            int same=0;
            for(Ball[] line:found)for(Ball b:line)
                if(b!=null && b.color==shooter)same++;
            // An unmatched color is a legitimate next ball, especially in
            // sparse boards. Do not erase it; the planner will use a setup or
            // advance shot. Circular + launcher-position checks protect from
            // the dragon/preview being recognized as a loaded projectile.
            if(same==0 && launcher.confidence<.92 && !allowSpecialLauncher) {shooter=' ';launcher=null;}
        }
        hash^=shooter;hash*=0x100000001b3L;
        // The preview distinguishes consecutive turns with the same loaded color.
        // Use the preview only if it is a real circular colored bubble.
        double nx=w*.59,ny=h*.782;
        char next=patchColor(pixels,w,h,nx,ny,radius);
        if(next!=' ' && next!='W' && circularQuality(pixels,w,h,nx,ny,radius)>=6) {
            hash^=next;hash*=0x100000001b3L;
        }
        return new Board(w,h,measured,found,shooter,count,hash,
                launcher==null?w*c.launchX:launcher.x,
                launcher==null?h*c.launchY:launcher.y,
                launcher==null?0:launcher.confidence);
    }
    private static boolean pauseBadge(int[] px,int w,int h,double x,double y,double r) {
        int gold=0,dark=0;
        // Yellow circular button with two dark brown vertical pause strokes.
        for(double[] pos:new double[][]{{0,-.72},{0,.72},{-.72,0},{.72,0},{0,0}}) {
            int rgb=pix(px,w,h,x+r*pos[0],y+r*pos[1]);
            int a=rgb>>16&255,b=rgb>>8&255,c=rgb&255;
            if(a>160 && b>125 && a-c>65 && b-c>35)gold++;
        }
        for(double side:new double[]{-.25,.25})for(double v:new double[]{-.25,0,.25}){
            int rgb=pix(px,w,h,x+r*side,y+r*v);
            if((rgb>>16&255)<160 && (rgb>>8&255)<130)dark++;
        }
        return gold>=4 && dark>=3;
    }
    static boolean recordedHud(int[] px,int w,int h){
        for(double x:new double[]{.119,.123,.127,.131})for(double y:new double[]{.112,.115,.118})
            if(pauseBadge(px,w,h,w*x,h*y,w*.046))return true;
        return false;
    }
    private static Configuration recordingLayout(int[] px,int w,int h,Configuration cfg){
        Configuration best=null;double score=-1;
        // Known viewport visible in all five videos; narrow local fitting keeps
        // false circular scenery from shifting the grid several columns.
        for(boolean narrow:new boolean[]{true,false})for(double dy:new double[]{-2,0,2})
        for(double dx:new double[]{-4,-2,0,2})for(double dp:new double[]{-.4,0,.4}){
            Configuration c=copy(cfg);
            c.oddX=(83*w/480.0+dx*w/480.0)/w;
            c.colPitch=(35.2+dp)/480.0;c.radiusX=16.3/480.0;
            c.topY=.175+dy/h;c.rowPitch=.0354;c.narrowTop=narrow;
            c.boardLeft=.130;c.boardRight=.875;c.detectedArena=true;
            double fit=fit(px,w,h,c,10);
            if(fit>score){score=fit;best=c;}
        }
        return best;
    }
    public Board detect(int[] pixels,int w,int h) {
        return detectInternal(pixels,w,h,false);
    }
    /** Combo dialog hides the launcher; only the special-skill path uses this. */
    public Board detectSkillPreview(int[] pixels,int w,int h,int panelTop,Board previous) {
        int[] masked=pixels.clone();
        java.util.Arrays.fill(masked,Math.max(0,panelTop)*w,masked.length,0xff285058);
        Board b=detectInternal(masked,w,h,true);
        if(b==null)return null;
        double x=previous==null?w*.50:previous.shooterX;
        double y=previous==null?h*.76:previous.shooterY;
        return new Board(w,h,b.cfg,b.balls,' ',b.count,b.signature,x,y,1);
    }
    /** Special projectiles may display two colors, which the normal single-
     * color launcher classifier deliberately rejects. No change to normal play. */
    public Board detectLoadedSkill(int[] pixels,int w,int h,Board detected,Board previous,char expected,boolean gold){
        if("ROYGBVP".indexOf(expected)<0)return null;
        Board b=detected!=null?detected:detectInternal(pixels,w,h,true);
        if(b==null)return null;
        double r=b.radius;
        double cx=previous==null?w*.50:previous.shooterX;
        double cy=previous==null?h*cfg.launchY:previous.shooterY;
        int x0=(int)Math.max(w*.36,cx-r*2.5),x1=(int)Math.min(w*.64,cx+r*2.5);
        int y0=(int)Math.max(h*(cfg.launchY-.080),cy-r*2.0),y1=(int)Math.min(h*(cfg.launchY+.025),cy+r*1.5);
        double qBest=-1,bx=0,by=0;
        for(int y=y0;y<=y1;y+=2)for(int x=x0;x<=x1;x+=2){
            int count=0;
            for(double[] pos:SAMPLE)if(pixelClass(pix(pixels,w,h,x+pos[0]*r,y+pos[1]*r))==expected)count++;
            if(count<(gold?2:4))continue;
            int circ=circularQuality(pixels,w,h,x,y,r);
            if(circ<6)continue;
            double goldError=gold?SkillVision.goldError(pixels,w,h,x,y,r):Double.POSITIVE_INFINITY;
            if(gold && count<6 && goldError>=24)continue;
            double q=circ*3+count-Math.abs(x-cx)*.08-Math.abs(y-cy)*.12;
            if(goldError<24)q+=25-goldError;
            if(q>qBest){qBest=q;bx=x;by=y;}
        }
        if(qBest<0)return null;
        return new Board(w,h,b.cfg,b.balls,expected,b.count,b.signature,bx,by,.85);
    }
    /** Reuse the unchanged normal route planner for one explicitly chosen color. */
    public Shot bestSkillColor(Board b,char color){
        if(b==null || "ROYGBVP".indexOf(color)<0)return null;
        return bestShot(new Board(b.width,b.height,b.cfg,b.balls,color,b.count,b.signature,b.shooterX,b.shooterY,b.shooterConfidence));
    }
    static char skillPixelColor(int rgb){return pixelClass(rgb);}
    private Board detectInternal(int[] pixels,int w,int h,boolean preview) {
        if(pixels==null || w<180 || h<350 || pixels.length!=w*h) {
            diagnosis="ẢNH KHÔNG HỢP LỆ";return null;
        }
        boolean hud=recordedHud(pixels,w,h);
        // Full screen artwork has a different pause badge position. Reject
        // score/menu screens before any grid search can hallucinate a board.
        boolean fullHud=false;
        for(double x:new double[]{.052,.055,.075,.085,.095,.105})for(double y:new double[]{.045,.075,.082,.09})
            if(pauseBadge(pixels,w,h,x*w,y*h,w*.05))fullHud=true;
        // Zoomed/cropped view can clip the pause badge, but retains the
        // purple top band, cyan arena sides and green SCORE label.
        boolean croppedHud=false;
        int bandRows=0,labelRows=0;
        for(double yy:new double[]{.066,.075,.085,.095,.105,.115}){
            int purple=0,green=0;
            for(double xx=.105;xx<=.44;xx+=.012){
                int rgb=pix(pixels,w,h,w*xx,h*yy);
                char color=pixelClass(rgb);
                if(color=='P' || color=='V')purple++;
                if(color=='G' || color=='Y')green++;
            }
            if(purple>=20)bandRows++;
            if(green>=3 && purple>=3)labelRows++;
        }
        croppedHud=bandRows>=2 && labelRows>=1;
        if(!hud && !fullHud && !croppedHud){diagnosis="KHÔNG TRONG VÁN";return null;}
        Configuration calibrated=null;
        SavedLayout previous=savedLayout;
        if(previous!=null && previous.w==w && previous.h==h) calibrated=previous.apply(cfg);
        Board result=calibrated==null?null:detectAt(pixels,w,h,calibrated);
        if(hud && (result==null || result.count<9 || (previous!=null && result.count<previous.previousCount*.85))){
            Configuration local=recordingLayout(pixels,w,h,cfg);
            Board candidate=detectAt(pixels,w,h,local);
            // The recording viewport is known; do not shift to unrelated
            // scenery just because a wider search counts more false circles.
            if(result==null || candidate.count>=result.count-2){calibrated=local;result=candidate;}
        }
        // After a new ceiling row, the game can reverse the 9/10 bubble
        // stagger WITHOUT moving the arena edges. Do not reuse the previous
        // parity just because it still finds some coincidental colored pixels.
        if(calibrated!=null && result!=null) {
            Configuration flipped=copy(calibrated);
            flipped.narrowTop=!flipped.narrowTop;
            Board alternative=detectAt(pixels,w,h,flipped);
            if(alternative.count>result.count+4) {
                result=alternative;calibrated=flipped;
            }
        }
        // Refit if the HUD/grid moved, or if no longer detecting a useful board.
        // Retain the last good calibration for sparse endgame boards.
        if(!hud && (result==null || result.count<9 ||
                (previous!=null && result.count<previous.previousCount*.85 &&
                 previous.previousCount>=22))) {
            Configuration found=findLayout(pixels,w,h,cfg);
            if(found!=null) {
                Board candidate=detectAt(pixels,w,h,found);
                if(result==null || candidate.count>result.count) {
                    result=candidate;calibrated=found;
                }
            }
        }
        if(result==null || result.count<1) {diagnosis="KHÔNG THẤY LƯỚI";return null;}
        // A few isolated detected circles far below the ceiling indicate
        // a shifted lattice or a mid-transition frame, not a playable board.
        if(result.count<7 && result.ceilingRow>1) {
            diagnosis="ĐANG CẬP NHẬT LƯỚI";return null;
        }
        if(preview)return result;
        if(result.shooter==' ') {diagnosis="CHƯA CÓ BÓNG SẴN SÀNG";return null;}
        if(result.shooter=='W' && !allowSpecialLauncher) {diagnosis="BÓNG ĐẶC BIỆT";return null;}
        if(result.shooterConfidence<.80) {
            diagnosis="MÀU BÓNG CHƯA CHẮC";return null;
        }
        diagnosis="OK "+result.count+" BÓNG "+result.shooter;
        if(calibrated!=null && result.count>=3)savedLayout=new SavedLayout(w,h,calibrated,result.count);
        return result;
    }

    /**
     * First-contact billiard physics: test EVERY bubble on each unobstructed
     * ray segment before the next wall. Bounce only if the wall wins; never
     * credit a cluster behind a nearer blocker, including white special balls.
     * Radius and wall shifts are varied in finalist stability checks.
     */
    private static Contact trace(Board board,List<Ball> all,double angle,
                                 double wallShift,double radiusShift) {
        double dx=Math.sin(Math.toRadians(angle)),dy=-Math.cos(Math.toRadians(angle));
        if(dy>=-1e-6)return null;
        double x=board.shooterX,y=board.shooterY,total=0;
        double left=board.leftWall+wallShift, right=board.rightWall-wallShift;
        if(right-left<board.radius*7 || x<=left || x>=right)return null;
        double effectiveR=board.radius*2.0+radiusShift;
        if(effectiveR<=board.radius)return null;
        for(int bounced=0;bounced<=3;bounced++) {
            double wallT=Double.POSITIVE_INFINITY;
            if(dx>1e-8)wallT=(right-x)/dx;
            else if(dx< -1e-8)wallT=(left-x)/dx;
            double topT=(board.topY-y)/dy;
            if(topT< -1e-6 || wallT< -1e-6)return null;
            double boundaryT=Math.min(topT,wallT);
            Ball first=null;
            double hitT=boundaryT;
            for(Ball ball:all) {
                double qx=x-ball.x,qy=y-ball.y;
                double b=qx*dx+qy*dy;
                double c=qx*qx+qy*qy-effectiveR*effectiveR;
                double det=b*b-c;
                if(det<0)continue;
                // When starting inside a collision radius, stop immediately.
                // Otherwise take the ENTRY intersection, never the exit.
                double t=c<=0?0:-b-Math.sqrt(det);
                if(t>=-1e-7 && t<hitT-1e-8) {hitT=Math.max(0,t);first=ball;}
            }
            double hitX=x+dx*hitT,hitY=y+dy*hitT;
            if(first!=null)
                return new Contact(hitX,hitY,total+hitT,first,bounced,dx,dy);
            if(topT<=wallT+1e-7)
                return new Contact(hitX,board.topY,total+topT,null,bounced,dx,dy);
            if(bounced>=3)return null;
            total+=wallT;
            // Move inside the reflecting wall to avoid numeric zero-time loops.
            x=hitX; y=hitY; dx=-dx;
            x=Math.max(left+1e-5,Math.min(right-1e-5,x));
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
        // The old 0.90*pitch tolerance could attach behind the blocker.
        // A real snap site is within roughly 0.5 pitch of the collision point.
        final double reach=b.width*b.cfg.colPitch*.56;
        for(int r=0;r<ROWS;r++) {
            if(b.gridY(r)>b.shooterY-b.radius*3)break;
            for(int c=0;c<COLS;c++) {
                if(!b.exists(r,c) || b.balls[r][c]!=null)continue;
                double x=b.gridX(r,c),y=b.gridY(r);
                double dist=Math.hypot(x-contact.x,y-contact.y);
                if(dist>reach)continue;
                boolean connected=(r==b.ceilingRow);
                if(!connected)for(int rr=Math.max(0,r-1);rr<=Math.min(ROWS-1,r+1) && !connected;rr++) {
                    for(int cc=Math.max(0,c-1);cc<=Math.min(COLS-1,c+1);cc++) {
                        if(b.balls[rr][cc]!=null && adjacent(b,r,c,rr,cc)) {
                            connected=true;break;
                        }
                    }
                }
                if(!connected)continue;
                if(contact.target!=null) {
                    if(!adjacent(b,r,c,contact.target.row,contact.target.col))continue;
                    // Attach on the incoming side of the first-hit ball, not
                    // somewhere past it or on the opposite side.
                    double rx=x-contact.target.x,ry=y-contact.target.y;
                    if(rx*(contact.x-contact.target.x)+
                       ry*(contact.y-contact.target.y)<0.20*b.radius*b.radius)continue;
                }
                if(best==null || dist<best.distance)best=new Placement(r,c,dist);
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
    private static boolean[][] connectedToCeiling(Board b,char[][] cells) {
        boolean[][] connected=new boolean[ROWS][COLS];
        ArrayDeque<int[]> queue=new ArrayDeque<>();
        for(int col=0;col<COLS;col++)if(b.exists(b.ceilingRow,col) &&
                cells[b.ceilingRow][col]!=' ') {
            connected[b.ceilingRow][col]=true;
            queue.add(new int[]{b.ceilingRow,col});
        }
        while(!queue.isEmpty()) {
            int[] at=queue.removeFirst();
            for(int[] next:neighbors(b,at[0],at[1])) {
                int r=next[0],c=next[1];
                if(cells[r][c]!=' ' && !connected[r][c]) {
                    connected[r][c]=true;queue.add(next);
                }
            }
        }
        return connected;
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
            // Count dropped bubbles only if they were connected to the ceiling
            // BEFORE this pop. Otherwise detached animation frames or detector
            // noise could be credited as a fictitious bonus.
            boolean[][] before=connectedToCeiling(b,cells);
            for(int[] c:matching)cells[c[0]][c[1]]=' ';
            seen=new boolean[ROWS][COLS];
            queue.clear();
            for(int c=0;c<COLS;c++)if(b.exists(b.ceilingRow,c) &&
                    cells[b.ceilingRow][c]!=' ') {
                queue.add(new int[]{b.ceilingRow,c});
                seen[b.ceilingRow][c]=true;
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
                if(cells[r][c]!=' ' && before[r][c] && !seen[r][c]) {
                    dropped++;
                    Ball bb=b.balls[r][c]; if(bb!=null)dropPixels+=bb.visiblePixels;
                }
            }
            return new Score(matched*110+dropped*170+visible*.10+dropPixels*.10,matched,dropped);
        }
        // A zero-pop shot still advances the game. Prefer a setup that touches
        // the most already-connected same-color bubbles; avoid attaching low
        // where it would endanger the board. No random fallback direction.
        int same=matching.size()-1;
        int depth=p.row-b.ceilingRow;
        // Matching one neighbor puts the next same-colored shot one away from
        // a 3-pop. The ordering is deliberately BELOW any genuine pop.
        return new Score(same*65 - Math.max(0,depth)*2.0 - 38,0,0);
    }
    private static Shot shotAtAngle(Board b,List<Ball> all,double angle) {
        return shotAtAngle(b,all,angle,0,0);
    }
    private static Shot shotAtAngle(Board b,List<Ball> all,double angle,
                                    double wallShift,double radiusShift) {
        if(Math.abs(angle)>77.5)return null;
        // DispatchGesture uses whole screen pixels. Quantize FIRST, then
        // trace the actual resulting angle, not the ideal sub-pixel angle.
        double aiming=b.width*.4265;
        int x=(int)Math.round(b.shooterX+Math.sin(Math.toRadians(angle))*aiming);
        int y=(int)Math.round(b.shooterY-Math.cos(Math.toRadians(angle))*aiming);
        x=Math.max(2,Math.min(b.width-3,x));
        y=Math.max((int)(b.height*.10),Math.min(b.height-3,y));
        double realAngle=Math.toDegrees(Math.atan2(x-b.shooterX,b.shooterY-y));
        if(Math.abs(realAngle)>77.5)return null;
        Contact contact=trace(b,all,realAngle,wallShift,radiusShift);
        if(contact==null)return null;
        Placement place=snap(b,contact);
        if(place==null)return null;
        Score score=evaluate(b,place);
        // Score only the actual FIRST contact, with a small risk penalty for
        // rebounds, high-angle shots, and imprecise snap distances.
        double finalScore=score.score-contact.traveled*.001-
                contact.bounces*2.2-Math.abs(realAngle)*.007-place.distance*.22;
        return new Shot(x,y,realAngle,finalScore,score.matched,score.dropped,
                b.signature,contact.bounces,
                contact.target==null?-1:contact.target.row,
                contact.target==null?-1:contact.target.col,
                place.row,place.col,1);
    }
    /** Recheck proposed targets with identical collision, wall and snap physics. */
    public Shot shotAtTap(Board b,int x,int y) {
        if(b==null || x<2 || x>=b.width-2 || y<2 || y>=b.shooterY-15)return null;
        double angle=Math.toDegrees(Math.atan2(x-b.shooterX,b.shooterY-y));
        return shotAtAngle(b,asList(b),angle);
    }

    /** Only consider a remote API suggestion when it has a verified pop. */
    public Shot safeShotAtTap(Board b,int x,int y) {
        Shot candidate=shotAtTap(b,x,y);
        if(candidate==null || candidate.matched<3)return null;
        Shot checked=stabilityRank(b,asList(b),candidate);
        return checked.reliability>=.80?checked:null;
    }

    /**
     * A route has to survive small perturbations in aim, wall position and
     * actual bubble size. A pixel-perfect but fragile theoretical pop must
     * lose to a nearly as valuable clear, repeatable shot.
     */
    private static Shot stabilityRank(Board b,List<Ball> balls,Shot candidate) {
        final double[] offsets={-.34,-.17,.17,.34};
        double worst=candidate.score;
        int sameCell=0,total=0;
        for(double offset:offsets) {
            Shot neighbor=shotAtAngle(b,balls,candidate.angle+offset);
            total++;
            if(neighbor!=null && (
                    (neighbor.snapRow==candidate.snapRow && neighbor.snapCol==candidate.snapCol) ||
                    (candidate.matched>=3 && neighbor.matched>=3)))sameCell++;
            double sample=neighbor==null?-75:neighbor.score;
            worst=Math.min(worst,sample);
        }
        // A few pixels of wall placement uncertainty matters most for banks.
        if(candidate.bounces>0)for(double shift:new double[]{-2,2}) {
            Shot variant=shotAtAngle(b,balls,candidate.angle,shift,0);
            total++;
            if(variant!=null && (
                    (variant.snapRow==candidate.snapRow && variant.snapCol==candidate.snapCol) ||
                    (candidate.matched>=3 && variant.matched>=3)))sameCell++;
            worst=Math.min(worst,variant==null?-75:variant.score);
        }
        // Bubble radii can be slightly different from their image halo.
        for(double shift:new double[]{-.9,.9}) {
            Shot variant=shotAtAngle(b,balls,candidate.angle,0,shift);
            total++;
            if(variant!=null && (
                    (variant.snapRow==candidate.snapRow && variant.snapCol==candidate.snapCol) ||
                    (candidate.matched>=3 && variant.matched>=3)))sameCell++;
            worst=Math.min(worst,variant==null?-75:variant.score);
        }
        double confidence=sameCell/(double)Math.max(1,total);
        // Trust the central path while strongly downgrading brittle shots.
        double robustScore=.60*candidate.score+.40*worst;
        robustScore-=(1-confidence)*Math.max(8,Math.max(0,candidate.score)*.06);
        return candidate.rerank(robustScore,confidence);
    }
    private static void keepTop(List<Shot> finalists,Shot shot,int max) {
        if(shot==null)return;
        // Keep different trajectories, not 24 near-identical angles.
        for(int i=0;i<finalists.size();i++) {
            Shot old=finalists.get(i);
            if(old.snapRow==shot.snapRow && old.snapCol==shot.snapCol &&
               old.bounces==shot.bounces &&
               Math.signum(old.angle)==Math.signum(shot.angle) &&
               Math.abs(old.angle-shot.angle)<.9) {
                if(shot.score>old.score)finalists.set(i,shot);
                return;
            }
        }
        if(finalists.size()<max) {finalists.add(shot);return;}
        int worstAt=0;
        for(int i=1;i<finalists.size();i++)
            if(finalists.get(i).score<finalists.get(worstAt).score)worstAt=i;
        if(shot.score>finalists.get(worstAt).score)finalists.set(worstAt,shot);
    }
    private static void consider(Board b,List<Ball> all,double angle,
                                 Set<Integer> used,List<Shot> finalists) {
        if(Math.abs(angle)>77.5)return;
        double aim=b.width*.4265;
        int x=(int)Math.round(b.shooterX+Math.sin(Math.toRadians(angle))*aim);
        int y=(int)Math.round(b.shooterY-Math.cos(Math.toRadians(angle))*aim);
        x=Math.max(2,Math.min(b.width-3,x));
        y=Math.max((int)(b.height*.10),Math.min(b.height-3,y));
        int key=x*65536+y;
        if(!used.add(key))return;
        keepTop(finalists,shotAtAngle(b,all,angle),96);
    }
    /**
     * Mirror each empty reachable cell across 1, 2, or 3 reflected wall
     * segments. These image-space directions include slim bank-shot corridors
     * that a coarse uniform scan can miss. EVERY candidate is still ray-traced
     * from the launcher and is rejected if an earlier bubble obstructs it.
     */
    /** A clearly separated colored round object in the gap can indicate an
     * in-flight projectile. Never treat a missing screenshot as flight. */
    public boolean hasAirborneBubble(int[] px,Board b) {
        if(b==null)return false;
        double bottom=b.topY;
        for(Ball[] row:b.balls)for(Ball ball:row)if(ball!=null)bottom=Math.max(bottom,ball.y);
        double rr=b.radius,step=Math.max(3,rr*.45);
        for(double y=bottom+rr*2.0;y<b.shooterY-rr*2.2;y+=step)
            for(double x=b.leftWall;x<=b.rightWall;x+=step){
                char col=patchColor(px,b.width,b.height,x,y,rr);
                if(col==' ' || col=='W' || circularQuality(px,b.width,b.height,x,y,rr)<7)continue;
                int n=0;for(double[] off:SAMPLE)if(pixelClass(pix(px,b.width,b.height,x+off[0]*rr,y+off[1]*rr))==col)n++;
                if(n>=11)return true;
            }
        return false;
    }
    /** Candidate for a wildcard projectile: choose a reachable cluster of any
     * known normal color. No extra blast radius or score rules are assumed. */
    public Shot bestWildcardRoute(Board b) {
        if(b==null)return null;
        Shot best=null;
        boolean[] colors=new boolean[128];
        for(Ball[] row:b.balls)for(Ball ball:row)if(ball!=null && ball.color<128 && ball.color!='W')colors[ball.color]=true;
        for(char c:new char[]{'R','O','Y','G','B','V','P'})if(colors[c]) {
            Board proxy=new Board(b.width,b.height,b.cfg,b.balls,c,b.count,b.signature,b.shooterX,b.shooterY,b.shooterConfidence);
            Shot next=bestShot(proxy);
            if(next!=null && next.matched>=3 && next.reliability>=.5 && (best==null || next.score>best.score))best=next;
        }
        return best;
    }
    public Shot bestShot(Board b) {
        if(b==null)return null;
        List<Ball> all=asList(b);
        List<Shot> finalists=new ArrayList<>(96);
        Set<Integer> used=new HashSet<>();
        for(int a=-155;a<=155;a++)
            consider(b,all,a*.5,used,finalists);
        final double left=b.leftWall,span=b.rightWall-b.leftWall;
        for(int r=0;r<ROWS;r++) {
            double cy=b.gridY(r);
            if(cy>b.shooterY-3*b.radius)break;
            for(int c=0;c<COLS;c++) {
                if(!b.exists(r,c) || b.balls[r][c]!=null)continue;
                boolean reachable=(r==b.ceilingRow);
                if(!reachable)for(int rr=Math.max(0,r-1);rr<=Math.min(ROWS-1,r+1) && !reachable;rr++)
                    for(int cc=Math.max(0,c-1);cc<=Math.min(COLS-1,c+1);cc++)
                        if(b.balls[rr][cc]!=null && adjacent(b,r,c,rr,cc)) {
                            reachable=true;break;
                        }
                if(!reachable)continue;
                double local=b.gridX(r,c)-left;
                for(int tile=-3;tile<=3;tile++) {
                    double virtualX=left+((tile&1)==0?tile*span+local:
                                         (tile+1)*span-local);
                    double angle=Math.toDegrees(Math.atan2(virtualX-b.shooterX,
                                                          b.shooterY-cy));
                    for(double delta:new double[]{-.33,0,.33})
                        consider(b,all,angle+delta,used,finalists);
                }
            }
        }
        if(finalists.isEmpty())return null;
        // Fine refinement around ALL promising distinct routes, not just the
        // first coarse winner. This includes reflections on either wall.
        ArrayList<Shot> initial=new ArrayList<>(finalists);
        for(Shot shot:initial)for(double d:new double[]{-.30,-.15,.15,.30})
            consider(b,all,shot.angle+d,used,finalists);
        Shot bestPop=null, bestAdvance=null;
        // Rank both kinds of trajectories. An actual predicted pop outranks
        // every setup/advance shot. Within the pop tier, reward the robust
        // expected score instead of discarding an entire route at an arbitrary
        // 80% snap-cell threshold. For setup shots require no precise target
        // (other than connecting to the first collider) but prefer stability.
        for(Shot candidate:finalists) {
            Shot ranked=stabilityRank(b,all,candidate);
            if(candidate.matched>=3) {
                // A path which holds under >= half of small aim perturbations
                // is usable. Higher reliability still greatly improves rank.
                if(ranked.reliability>=.50) {
                    double value=ranked.score + ranked.reliability*38;
                    if(bestPop==null || value>bestPop.score+bestPop.reliability*38)
                        bestPop=ranked;
                }
            } else {
                double value=ranked.score+ranked.reliability*12
                        -(ranked.bounces*8);
                if(bestAdvance==null || value>bestAdvance.score+
                        bestAdvance.reliability*12-bestAdvance.bounces*8)
                    bestAdvance=ranked;
            }
        }
        // Always fire once a genuine loaded projectile and a legal ray exist.
        // A setup / advance shot may cost points; it is chosen ONLY when no
        // credible pop is reachable. The caller must never turn null-pop into
        // an idle state when bestAdvance exists.
        if(bestPop!=null)return bestPop;
        if(bestAdvance!=null)return bestAdvance;
        // All retained candidates may be fragile pops. Still return the most
        // reliable legal route rather than freezing an otherwise valid turn.
        Shot fallback=null;
        for(Shot candidate:finalists) {
            Shot ranked=stabilityRank(b,all,candidate);
            if(fallback==null || ranked.reliability>fallback.reliability ||
                    (ranked.reliability==fallback.reliability && ranked.score>fallback.score))fallback=ranked;
        }
        return fallback;
    }
}
