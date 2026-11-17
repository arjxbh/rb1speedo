import club.midlife.rb1speed.DrivePeak;
public final class DrivePeakTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static void parked(DrivePeak p,long from,long through){for(long t=from;t<=through;t+=1000)p.observe(0,true,t);}
 public static void main(String[] args){
  DrivePeak p=new DrivePeak();p.observe(50,true,0);check(!p.active());
  p.power(true);p.observe(1,true,1000);check(!p.active());p.observe(24,true,2000);check(p.active());
  p.observe(62.5,true,3000);p.observe(20,true,4000);p.observe(100,false,5000);p.observe(Double.NaN,true,6000);check(p.maximumMph()==62.5);
  parked(p,7000,200000);check(p.active());p.power(false);p.observe(12,true,201000);check(p.active());
  parked(p,202000,381000);check(p.active());p.observe(0,true,382000);check(!p.active()&&p.maximumMph()<0);
  p.observe(60,true,383000);check(!p.active());p.power(true);p.observe(30,true,384000);check(p.maximumMph()==30);
  p.power(false);parked(p,385000,500000);p.observe(0,false,501000);parked(p,502000,650000);check(p.active());
  p.power(true);parked(p,651000,900000);check(p.active());p.power(false);parked(p,901000,1000000);
  p.observe(0,true,1010000);parked(p,1011000,1189000);check(p.active());p.observe(0,true,1190000);check(!p.active());
  p.restore(72,true,false);p.observe(0,true,1200000);p.restore(72,true,false);parked(p,1300000,1479000);check(p.active()&&p.maximumMph()==72);p.observe(0,true,1480000);check(!p.active());
  p.restore(Double.NaN,true,false);check(!p.active());
  System.out.println("DrivePeak: all checks passed");
 }
}
