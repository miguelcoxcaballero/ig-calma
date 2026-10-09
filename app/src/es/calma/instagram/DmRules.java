package es.calma.instagram;

import java.util.Locale;

/** Conservative recognition: never copy all Instagram activity as if it were a DM. */
public final class DmRules {
    public static boolean isDm(String pkg,String category,String channel,boolean messagingStyle) {
        if(!"com.instagram.android".equals(pkg))return false;
        if(messagingStyle || "msg".equals(category))return true;
        String c=channel==null?"":channel.toLowerCase(Locale.ROOT);
        return c.contains("direct") || c.contains("message") || c.contains("mensaje");
    }
}
