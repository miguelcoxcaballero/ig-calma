package es.calma.patches

import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element

private const val STOCK = "com.instagram.android"
private const val CALMA = "es.calma.instagram"
private val providerNames = linkedMapOf<String, String>()
private val manifestNames = linkedMapOf<String, String>()
private object BundledResources

/** Packaging and private extension only; all behavioral hooks live in our own patches. */
val calmaPackagePatch = resourcePatch(
    name = "Calma native package",
    description = "Package the original native app with Calma's settings and reused Inhouse updater"
) {
    compatibleWith(STOCK("439.0.0.37.89"))
    execute {
        check(packageMetadata.versionCode == "384510827") { "Unsupported stock version code" }
        providerNames.clear()
        manifestNames.clear()
        document("AndroidManifest.xml").use { xml ->
            val manifest = xml.documentElement
            check(manifest.getAttribute("package") == STOCK)
            cloneManifestNames(manifest, STOCK, CALMA, providerNames, manifestNames)
            manifest.setAttribute("package", CALMA)
            // Morphe parses this DOM without namespace awareness; update the existing qualified attribute.
            manifest.setAttribute("android:versionCode", "384510831")
            // Keep Instagram's original protocol version; CalmaBuild reports our updater version.
            val app = manifest.getElementsByTagName("application").item(0) as Element
            app.setAttribute("android:label", "Instagram Calma")
            fun add(parent: Element, tag: String, attributes: Map<String, String>): Element {
                val child = xml.createElement(tag)
                attributes.forEach { (name, value) -> child.setAttribute("android:$name", value) }
                parent.appendChild(child)
                return child
            }
            add(manifest, "uses-permission", mapOf("name" to "android.permission.REQUEST_INSTALL_PACKAGES"))
            add(app, "provider", mapOf("name" to "$CALMA.nativeapp.NativeInitProvider", "authorities" to "$CALMA.calma.init", "exported" to "false", "initOrder" to "1000"))
            add(app, "activity", mapOf("name" to "$CALMA.UpdateActivity", "exported" to "false", "theme" to "@style/CalmaUpdatePopupTheme"))
            add(app, "activity", mapOf("name" to "$CALMA.nativeapp.CalmaSettingsActivity", "exported" to "false", "theme" to "@style/CalmaSettingsTheme"))
            val provider = add(app, "provider", mapOf("name" to "es.calma.vendor.androidx.core.content.FileProvider", "authorities" to "$CALMA.fileprovider", "exported" to "false", "grantUriPermissions" to "true"))
            add(provider, "meta-data", mapOf("name" to "android.support.FILE_PROVIDER_PATHS", "resource" to "@xml/calma_update_file_paths"))
            add(app, "meta-data", mapOf("name" to "es.calma.version", "value" to "0.4.3"))
        }
        val files = listOf(
            "assets/updates/index.html", "assets/updates/updater.js", "assets/updates/android-update.css",
            "res/xml/calma_update_file_paths.xml"
        )
        for (path in files) {
            val input = BundledResources::class.java.getResourceAsStream("/calma-res/$path")
                ?: error("Missing bundled resource $path")
            val target = get(path)
            target.parentFile.mkdirs()
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        }
        // ARSCLib infers a values file's resource type from its filename. Merge
        // into styles.xml so both our additions and Instagram's styles survive.
        for (folder in listOf("values", "values-night")) {
            val path = "res/$folder/styles.xml"
            val input = BundledResources::class.java.getResourceAsStream("/calma-res/$path")
                ?: error("Missing bundled resource $path")
            val additions = input.use {
                javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
            }
            val target = get(path)
            if (!target.exists()) {
                target.parentFile.mkdirs()
                target.writeText("<resources/>")
            }
            document(path).use { xml ->
                val styles = additions.documentElement.childNodes
                for (index in 0 until styles.length) {
                    val style = styles.item(index) as? Element ?: continue
                    check(style.tagName == "style")
                    xml.documentElement.appendChild(xml.importNode(style, true))
                }
            }
        }
        // Explicit IDs also keep references from AndroidManifest.xml stable.
        app.morphe.patcher.resource.PublicXmlManager(get("res/values/public.xml")).use { ids ->
            ids.createPublicId("style", "CalmaSettingsTheme")
            ids.createPublicId("style", "CalmaUpdatePopupTheme")
            ids.createPublicId("xml", "calma_update_file_paths")
        }
    }
}

val calmaExtensionPatch = bytecodePatch(
    name = "Calma native extension",
    description = "Original Calma runtime, without a web feed or third-party feature patches"
) {
    compatibleWith(STOCK("439.0.0.37.89"))
    dependsOn(calmaPackagePatch)
    extendWith("calma-extension.dex")
    execute {
        check(packageMetadata.versionCode == "384510827")
        val replacements = manifestNames.toMutableMap().apply { put(STOCK, CALMA) }
        val candidates = linkedMapOf<String, com.android.tools.smali.dexlib2.iface.ClassDef>()
        for (text in replacements.keys + providerNames.keys) {
            for (type in classDefByStrings(text, StringComparisonType.CONTAINS)) candidates[type.type] = type
        }
        for (type in candidates.values) {
            val mutable by lazy { mutableClassDefBy(type) }
            for (source in type.methods) {
                var method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod? = null
                source.implementation?.instructions?.forEachIndexed { index, instruction ->
                    val text = ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ?: return@forEachIndexed
                    val changed = cloneIdentifier(text, replacements, providerNames) ?: return@forEachIndexed
                    if (changed == text) return@forEachIndexed
                    if (method == null) method = mutable.methods.single { it.name == source.name && it.parameterTypes == source.parameterTypes && it.returnType == source.returnType }
                    val register = (instruction as OneRegisterInstruction).registerA
                    val reference = ImmutableStringReference(changed)
                    val replacement = if (instruction.opcode == Opcode.CONST_STRING_JUMBO)
                        BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, register, reference)
                    else BuilderInstruction21c(Opcode.CONST_STRING, register, reference)
                    method!!.replaceInstruction(index, replacement)
                }
            }
        }
    }
}
