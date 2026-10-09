package es.calma.patches

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.StringWriter
import org.w3c.dom.Element

fun main() {
    // The actual generic Morphe parser leaves namespaceAware=false.
    val factory = DocumentBuilderFactory.newInstance()
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream("""
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.instagram.android" android:versionCode="384510827">
          <permission android:name="com.instagram.android.permission.SYSTEM_ONLY"/>
          <uses-permission android:name="com.instagram.android.permission.SYSTEM_ONLY"/>
          <application android:name=".InstagramApp" android:backupAgent="BackupAgent" android:appComponentFactory="com.instagram.process.instagram.Ig4aAppComponentFactory">
            <meta-data android:name="com.instagram.android.channel" android:value="playstore"/>
            <activity android:name=".MainActivity" android:parentActivityName=".Parent" android:taskAffinity="com.instagram.android.RtcCallActivity"/>
            <activity-alias android:name="com.instagram.android.activity.MainTabActivity" android:targetActivity=".MainActivity"/>
            <provider android:name="com.instagram.contentprovider.DeferredDeeplinkProvider" android:authorities="com.instagram.contentprovider.DeferredDeeplinkProvider;com.instagram.android.baselcontext" android:permission="com.instagram.android.permission.SYSTEM_ONLY"/>
            <service android:name="NativeService" android:process=":mqtt"/>
            <service android:name="com.instagram.NativeService" android:process="com.instagram.android.worker"/>
            <receiver android:name=".NativeReceiver"><intent-filter><action android:name="com.instagram.android.LOCAL_NOTIFICATION_EVENT"/></intent-filter></receiver>
          </application>
        </manifest>
    """.trimIndent().toByteArray()))
    val root = document.documentElement
    val providers = linkedMapOf<String,String>()
    val exact = linkedMapOf<String,String>()
    cloneManifestNames(root,"com.instagram.android","es.calma.instagram",providers,exact)
    exact["com.instagram.android"]="es.calma.instagram"
    fun node(tag:String,index:Int=0) = root.getElementsByTagName(tag).item(index) as Element
    fun checkValue(tag:String,attribute:String,wanted:String,index:Int=0) {
        check(node(tag,index).getAttribute("android:$attribute")==wanted) { "$tag.$attribute" }
    }
    checkValue("application","name","com.instagram.android.InstagramApp")
    checkValue("application","backupAgent","com.instagram.android.BackupAgent")
    checkValue("application","appComponentFactory","com.instagram.process.instagram.Ig4aAppComponentFactory")
    checkValue("activity","name","com.instagram.android.MainActivity")
    checkValue("activity","parentActivityName","com.instagram.android.Parent")
    checkValue("activity","taskAffinity","es.calma.instagram.RtcCallActivity")
    checkValue("activity-alias","name","com.instagram.android.activity.MainTabActivity")
    checkValue("activity-alias","targetActivity","com.instagram.android.MainActivity")
    checkValue("meta-data","name","com.instagram.android.channel")
    checkValue("provider","name","com.instagram.contentprovider.DeferredDeeplinkProvider")
    checkValue("provider","authorities","es.calma.instagram.com.instagram.contentprovider.DeferredDeeplinkProvider;es.calma.instagram.baselcontext")
    checkValue("provider","permission","es.calma.instagram.permission.SYSTEM_ONLY")
    checkValue("permission","name","es.calma.instagram.permission.SYSTEM_ONLY")
    checkValue("uses-permission","name","es.calma.instagram.permission.SYSTEM_ONLY")
    checkValue("service","process",":mqtt")
    checkValue("service","process","es.calma.instagram.worker",1)
    checkValue("action","name","es.calma.instagram.LOCAL_NOTIFICATION_EVENT")
    for ((old,changed) in exact) check(cloneIdentifier(old,exact,providers)==changed)
    for ((old,changed) in providers) {
        check(cloneIdentifier(old,exact,providers)==changed)
        for (suffix in listOf("","/","/message/123","?page=2","#fragment")) {
            check(cloneIdentifier("content://$old$suffix",exact,providers)=="content://$changed$suffix")
        }
    }
    // Actual stock aliases, intent payload keys and class diagnostic strings.
    for (preserved in listOf(
        "com.instagram.android.activity.MainTabActivity",
        "com.instagram.android.activity.MainTabActivity.kpop",
        "com.instagram.android.InternalLauncher",
        "com.instagram.android.igns.logging.push_id",
        "com.instagram.android.fragment.ARGUMENTS_KEY_EXTRA_MEDIA_ID",
        "com.instagram.android.channel",
        "com.instagram.android.",
        "com.instagram.contentprovider.DeferredDeeplinkProvider\$Impl",
        "com.instagram.creation.drafts.contentprovider.ClipsDraftProvider\$Impl\$getClipsDraftPreviewItems\$1",
        "content://com.instagram.android.baselcontext.evil/posts",
        "https://example.test/content://com.instagram.android.baselcontext"
    )) check(cloneIdentifier(preserved,exact,providers)==null) { "Unexpected rewrite: $preserved" }
    root.setAttribute("android:versionCode","384510828")
    val serialized=StringWriter()
    TransformerFactory.newInstance().newTransformer().transform(DOMSource(document),StreamResult(serialized))
    val roundtrip=factory.newDocumentBuilder().parse(ByteArrayInputStream(serialized.toString().toByteArray()))
    check(roundtrip.documentElement.getAttribute("android:versionCode")=="384510828")
    println("Native clone manifest, component aliases, URI boundaries and payload preservation checks passed")
}
