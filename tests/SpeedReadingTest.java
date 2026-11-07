import club.midlife.rb1speed.SpeedReading;
public class SpeedReadingTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        SpeedReading r=new SpeedReading();check(!r.fresh(0));
        check(r.accept(26.8224,true,5,true,.2f,1000,1000));check(Math.abs(r.mph()-60)<.00001);
        check(r.fresh(4999));check(!r.fresh(5000));
        check(!r.accept(1,false,5,false,0,5100,5100));
        check(!r.accept(1,true,50,false,0,5100,5100));
        check(!r.accept(1,true,5,true,4,5100,5100));
        check(!r.accept(Double.NaN,true,5,false,0,5100,5100));
        check(!r.accept(-1,true,5,false,0,5100,5100));
        check(!r.accept(10,true,5,false,0,1000,6000));
        check(!r.accept(10,true,5,false,0,7000,6000));
        check(r.accept(0,true,5,false,0,6000,6000));check(r.mph()==0);
        check(!r.accept(10,true,5,false,0,5900,6000));
        r.clear();check(!r.fresh(6000));System.out.println("SpeedReading: all checks passed");
    }
}
