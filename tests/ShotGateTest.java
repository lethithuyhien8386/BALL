import com.example.bubblepilot.ShotGate;

public class ShotGateTest {
    private static void assertYes(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ShotGate gate=new ShotGate();
        assertYes(!gate.canShoot(111,0), "One frame is not stable enough");
        assertYes(gate.canShoot(111,140), "Second identical frame must be ready");
        gate.shotDispatched(111,150);
        assertYes(!gate.canShoot(111,600), "Never double fire unchanged board");
        assertYes(!gate.canShoot(222,650), "Changed board must stabilize");
        assertYes(gate.canShoot(222,800), "Second frame of next ball must be ready");
        gate.shotDispatched(222,810);
        gate.observedNoBoard();
        assertYes(!gate.canShoot(222,850), "Animation must not be considered ready");
        assertYes(!gate.canShoot(222,1120), "Transitioned board still needs stability");
        assertYes(!gate.canShoot(222,1250), "Never double fire same board even after a missing frame");
        gate.reset();
        assertYes(!gate.canShoot(333,0) && gate.canShoot(333,100), "Reset starts fresh");
        System.out.println("ShotGate tests PASS");
    }
}
