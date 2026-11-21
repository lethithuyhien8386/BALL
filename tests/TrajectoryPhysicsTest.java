package com.example.bubblepilot;

/** Deterministic regression: nearest obstacle, left/right bank and automatic route choice. */
public final class TrajectoryPhysicsTest {
    static GameSolver.Configuration cfg() {return new GameSolver.Configuration();}
    static GameSolver.Board scene(boolean blocked) {
        GameSolver.Configuration c=cfg();
        int w=441,h=791;
        GameSolver.Ball[][] cells=new GameSolver.Ball[GameSolver.ROWS][GameSolver.COLS];
        add(cells,w,h,c,0,4,'G');
        add(cells,w,h,c,1,4,'G');
        if(blocked)add(cells,w,h,c,5,4,'W');
        return new GameSolver.Board(w,h,c,cells,'G',blocked?3:2,blocked?17:18);
    }
    static void add(GameSolver.Ball[][] cells,int w,int h,GameSolver.Configuration c,
                    int row,int col,char color) {
        double x=w*(c.oddX+c.colPitch*col+((row&1)==0?c.colPitch*.5:0));
        double y=h*(c.topY+row*c.rowPitch);
        cells[row][col]=new GameSolver.Ball(row,col,color,x,y,420);
    }
    static GameSolver.Shot aim(GameSolver s,GameSolver.Board b,double angle) {
        double span=b.width*.4265;
        int x=(int)Math.round(b.shooterX+Math.sin(Math.toRadians(angle))*span);
        int y=(int)Math.round(b.shooterY-Math.cos(Math.toRadians(angle))*span);
        return s.shotAtTap(b,x,y);
    }
    static void check(boolean valid,String why) {if(!valid)throw new AssertionError(why);}
    static void print(String name,GameSolver.Shot s) {
        if(s==null){System.out.println(name+": INVALID");return;}
        System.out.printf("%s: angle=%.2f bank=%d contact=%d,%d snap=%d,%d match=%d drop=%d score=%.1f conf=%.2f (%d,%d)%n",
                name,s.angle,s.bounces,s.hitRow,s.hitCol,s.snapRow,s.snapCol,
                s.matched,s.dropped,s.score,s.reliability,s.tapX,s.tapY);
    }
    public static void main(String[] args) {
        GameSolver solver=new GameSolver(cfg());
        GameSolver.Board free=scene(false),blocked=scene(true);
        double x=free.gridX(1,4),y=free.gridY(1);
        double angle=Math.toDegrees(Math.atan2(x-free.shooterX,free.shooterY-y));
        GameSolver.Shot direct=aim(solver,free,angle);
        GameSolver.Shot stopped=aim(solver,blocked,angle);
        check(direct!=null && direct.matched>=3,
              "Unobstructed direct shot should clear its connected color group");
        check(stopped!=null && stopped.hitRow==5 && stopped.hitCol==4 &&
              stopped.matched==0 && stopped.bounces==0,
              "Blocked direct trajectory must stop at the FIRST white obstacle");
        print("unobstructed",direct);print("first obstacle",stopped);
        for(int tile:new int[]{-2,-1,1,2}) {
            double span=blocked.rightWall-blocked.leftWall;
            double local=x-blocked.leftWall;
            double vx=blocked.leftWall+((tile&1)==0?tile*span+local:
                                       (tile+1)*span-local);
            double indirect=Math.toDegrees(Math.atan2(vx-blocked.shooterX,
                                                       blocked.shooterY-y));
            GameSolver.Shot hit=aim(solver,blocked,indirect);
            check(hit!=null && hit.bounces==Math.abs(tile) &&
                  hit.hitRow==1 && hit.hitCol==4 && hit.matched>=3,
                  "Bank #"+tile+" must reach the same group AFTER true reflections");
            print("wall tile "+tile,hit);
        }
        // Add a colorless ball that makes the bank line superior to the
        // apparent high-scoring straight route. Planner must choose bank.
        GameSolver.Configuration c=cfg();int w=441,h=791;
        GameSolver.Ball[][] guarded=new GameSolver.Ball[GameSolver.ROWS][GameSolver.COLS];
        add(guarded,w,h,c,0,4,'G');add(guarded,w,h,c,1,4,'G');
        add(guarded,w,h,c,5,4,'W');add(guarded,w,h,c,1,5,'W');
        GameSolver.Board wallPreferred=new GameSolver.Board(w,h,c,guarded,'G',4,991);
        GameSolver.Shot best=solver.bestShot(wallPreferred);
        check(best!=null && best.bounces>=1 && best.matched>=3 &&
              best.reliability>.50,
              "Planner should select a robust bank when the direct line is blocked");
        print("automatic bank",best);
        System.out.println("TrajectoryPhysicsTest PASS (direct blocker, both walls, two banks, robust bank choice)");
    }
}
