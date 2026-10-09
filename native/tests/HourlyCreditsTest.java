package es.calma.instagram.nativeapp;

public final class HourlyCreditsTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    public static void main(String[] args) {
        long h = HourlyCredits.HOUR, d = HourlyCredits.DAY, start = 100 * h + 1000;
        HourlyCredits c = new HourlyCredits(start, 1);
        check(c.balance(start) == 0, "Initially locked without retroactive credits");
        check(c.balance(101*h - 1) == 0, "No grant before next clock hour");
        check(c.balance(101*h) == 60000, "One minute at clock hour");
        check(c.balance(104*h) == 240000, "Closed app earns missed hours");
        c.spend(104*h,104*h+30500,30500);
        check(c.balance(104*h+30500)==209500,"Only active duration is charged");
        HourlyCredits restored=HourlyCredits.restore(c.save(),104*h+30500,1);
        check(restored.balance(104*h+30500)==209500,"Exact remaining milliseconds persist");
        restored.rate(104*h+30500,0);
        check(restored.balance(125*h)==180000,"First partially spent bucket expires exactly 24 hours after earning");
        check(restored.balance(128*h)==0,"All earned buckets expire individually");
        HourlyCredits rate=new HourlyCredits(start,1);
        rate.rate(103*h,3);
        check(rate.balance(103*h)==180000,"Rate change cannot retroactively increase earnings");
        check(rate.balance(104*h)==360000,"New rate applies to subsequent hours");
        check(rate.balance(102*h)==360000,"Backwards clock does not mint credits");
        check(rate.balance(104*h)==360000,"Returning to previous hour does not mint twice");
        HourlyCredits gap=new HourlyCredits(start,1);
        check(gap.balance(start+10000*d)==24*60000,"Long absence only keeps unexpired hours and has bounded work");
        HourlyCredits boundary=new HourlyCredits(100*h,1);
        boundary.spend(100*h,101*h+10000,h+10000);
        check(boundary.balance(101*h+10000)==50000,"Next-hour earnings do not pay for locked previous hour");
        boundary.spend(101*h+10000,101*h+200000,190000);
        check(boundary.balance(101*h+200000)==0,"Overspending cannot make balance negative");
        HourlyCredits zero=new HourlyCredits(start,0);
        check(zero.balance(150*h)==0,"Zero allowance keeps For you locked");
        check(HourlyCredits.restore("broken",start,1).balance(start)==0,"Corrupt preferences fail closed");
        check(HourlyCredits.clamp(-1)==0 && HourlyCredits.clamp(500)==60,"Configuration bounded");
        HourlyCredits idle=new HourlyCredits(start,1);
        long before=idle.balance(105*h); idle.balance(105*h+50000);
        check(idle.balance(105*h+50000)==before,"Reading selector or using DMs does not spend time");
        System.out.println("Hourly credits: "+checks+" assertions passed");
    }
}
