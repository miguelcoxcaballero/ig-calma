package es.calma.patches

import org.w3c.dom.Element

/** Package identity is distinct from a component's original Java class name. */
internal fun cloneManifestNames(
    manifest: Element,
    stock: String,
    calma: String,
    providers: MutableMap<String, String>,
    renamedNames: MutableMap<String, String>
) {
    val components = setOf("application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation")
    val elements = manifest.getElementsByTagName("*")
    for (i in 0 until elements.length) {
        val node = elements.item(i) as Element
        // Morphe's DocumentBuilder is not namespace-aware: use qualified names.
        // Android resolves relative/unqualified classes against the old package.
        if (node.tagName in components) {
            for (name in listOf("name", "targetActivity", "parentActivityName", "backupAgent", "appComponentFactory")) {
                val value = node.getAttribute("android:$name")
                val qualified = when {
                    value.startsWith('.') -> stock + value
                    value.isNotEmpty() && !value.contains('.') -> "$stock.$value"
                    else -> value
                }
                if (qualified != value) node.setAttribute("android:$name", qualified)
            }
        }
        if (node.tagName == "provider") {
            val old = node.getAttribute("android:authorities")
            if (old.isNotEmpty()) {
                val renamed = old.split(';').joinToString(";") { authority ->
                    providers.getOrPut(authority) {
                        if (authority == stock || authority.startsWith("$stock.")) calma + authority.removePrefix(stock)
                        else "$calma.$authority"
                    }
                }
                node.setAttribute("android:authorities", renamed)
            }
        }
        for (name in listOf("name", "permission", "readPermission", "writePermission", "taskAffinity", "process", "targetPackage")) {
            // Component/alias names and metadata keys are not installation identities.
            if (name == "name" && (node.tagName in components || node.tagName == "meta-data")) continue
            val value = node.getAttribute("android:$name")
            if (value == stock || value.startsWith("$stock.")) {
                val replacement = calma + value.removePrefix(stock)
                node.setAttribute("android:$name", replacement)
                renamedNames[value] = replacement
            }
        }
    }
}

/** Rewrite only installation identifiers and the authority part of content URIs.
 * Never rename fragment/DM payload keys, class names, aliases, or nested classes
 * merely because their strings begin with the original package/authority.
 */
internal fun cloneIdentifier(
    text: String,
    exactNames: Map<String, String>,
    providers: Map<String, String>
): String? {
    exactNames[text]?.let { return it }
    providers[text]?.let { return it }
    if (text.startsWith("content://")) {
        val start = "content://".length
        val end = text.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) text.length else it }
        providers[text.substring(start, end)]?.let { authority ->
            return "content://" + authority + text.substring(end)
        }
    }
    return null
}
