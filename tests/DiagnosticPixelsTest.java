package club.midlife.rb1speed;
import java.util.Arrays;
public class DiagnosticPixelsTest {
    private static void equal(byte[] actual,byte[] expected) {
        if(!Arrays.equals(actual,expected)) throw new AssertionError(Arrays.toString(actual));
    }
    public static void main(String[] args) {
        byte[] left={1,2,3,4,21,11}, right={5,6,7,8,22,12};
        equal(DiagnosticPixels.join(left,right,2,2,true),new byte[]{1,2,5,6,3,4,7,8,11,12,21,22});
        equal(DiagnosticPixels.join(left,right,2,2,false),new byte[]{1,2,5,6,3,4,7,8,11,21,12,22});
        byte[] tallLeft={1,2,3,4,5,6,7,8,21,11,23,13};
        byte[] tallRight={9,10,11,12,13,14,15,16,22,12,24,14};
        equal(DiagnosticPixels.join(tallLeft,tallRight,2,4,true),new byte[]{1,2,9,10,3,4,11,12,5,6,13,14,7,8,15,16,11,12,13,14,21,22,23,24});
        equal(DiagnosticPixels.join(tallLeft,tallRight,2,4,false),new byte[]{1,2,9,10,3,4,11,12,5,6,13,14,7,8,15,16,11,21,12,22,13,23,14,24});
        boolean rejected=false;
        try { DiagnosticPixels.join(left,right,3,2,true); } catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Odd dimensions accepted");
        rejected=false;
        try { DiagnosticPixels.join(left,new byte[2],2,2,true); } catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Mismatched frame accepted");
        equal(left,new byte[]{1,2,3,4,21,11});
        System.out.println("Diagnostic pixels: all checks passed");
    }
}
