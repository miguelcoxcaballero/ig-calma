import es.calma.instagram.DmRules;
public class DmRulesTest {
    private static void check(boolean condition) { if(!condition)throw new AssertionError(); }
    public static void main(String[] args) {
        check(DmRules.isDm("com.instagram.android","msg","other",false));
        check(DmRules.isDm("com.instagram.android",null,"ig_direct",false));
        check(DmRules.isDm("com.instagram.android",null,"other",true));
        check(!DmRules.isDm("com.instagram.android",null,"likes",false));
        check(!DmRules.isDm("com.instagram.android",null,"followers",false));
        check(!DmRules.isDm("es.calma.instagram","msg","direct",true));
        check(!DmRules.isDm("another.app","msg","direct",true));
        System.out.println("PASS: DM classification, exclusion of likes/follows, other apps and mirrored notifications.");
    }
}
