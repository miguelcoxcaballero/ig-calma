package es.calma.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val DISCOVER = "Les/calma/instagram/nativeapp/NativeDiscover;"
private fun MutableMethod.discoverStrings(): Set<String> = implementation!!.instructions.mapNotNull {
    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
}.toSet()

/** Independently mapped native Explore and main-search filtering. */
val calmaNativeDiscover = bytecodePatch(
    name = "Calma native Discover",
    description = "Only followed accounts in Explore, main-search results and search history"
) {
    compatibleWith("com.instagram.android"("439.0.0.37.89"))
    dependsOn(calmaExtensionPatch)
    execute {
        fun fields(type: String, expected: Map<String, String>) {
            val definition = mutableClassDefBy(type)
            for ((name, value) in expected) check(definition.fields.any { it.name == name && it.type == value }) {
                "Stock Discover field changed: $type $name"
            }
        }
        fields("LX/02P3;", mapOf("A06" to "Ljava/util/List;", "A03" to "Ljava/lang/String;", "A04" to "Ljava/lang/String;", "A09" to "Z"))
        fields("LX/024Z;", mapOf("A01" to "LX/032B;", "A00" to "LX/031Y;", "A02" to "LX/02P5;"))
        fields("LX/032B;", mapOf("A0C" to "Ljava/util/List;", "A0D" to "Ljava/util/List;", "A0E" to "Ljava/util/List;", "A0F" to "Ljava/util/List;", "A09" to "LX/024Z;"))
        fields("LX/032D;", mapOf("A08" to "Lcom/instagram/feed/media/Media;", "A09" to "Lcom/instagram/feed/media/Media;", "A00" to "LX/031N;"))
        check(mutableClassDefBy("LX/032B;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("Ljava/util/List;") })
        check(mutableClassDefBy("LX/031Y;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("Ljava/lang/Boolean;", "Ljava/lang/Double;", "Ljava/lang/Integer;", "Ljava/lang/Integer;") })
        check(mutableClassDefBy("LX/024Z;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("LX/031Y;", "LX/032B;", "LX/02P5;") })
        check(mutableClassDefBy("LX/032D;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("LX/031N;", "LX/024L;", "Lcom/instagram/feed/media/Media;", "Lcom/instagram/feed/media/Media;", "LX/03G6;", "Z", "Z", "Z") })
        check("media_grid" in mutableClassDefBy("LX/02P5;").methods.single { it.name == "<clinit>" }.discoverStrings())

        fun parser(type: String, target: String, strings: Set<String>) {
            val method = mutableClassDefBy(type).methods.single { it.name == "unsafeParseFromJson" }
            check(method.discoverStrings().containsAll(strings)) { "Stock Discover parser changed: $type" }
            check(method.implementation!!.registerCount <= 16)
            val returns = method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }.toList()
            check(returns.size == 2)
            for ((index, instruction) in returns.asReversed()) {
                val result = (instruction as OneRegisterInstruction).registerA
                // Replace the return itself: existing branch labels must enter the hook.
                method.replaceInstruction(index, "invoke-static {v$result, p1}, $DISCOVER->$target(Ljava/lang/Object;Ljava/lang/Object;)V")
                method.addInstructions(index + 1, "return-object v$result")
            }
        }
        parser("LX/094e;", "explore", setOf("sectional_items", "more_available", "next_max_id"))
        parser("LX/0XFB;", "searchResponse", setOf("users", "list", "tags", "search_session_context"))
        fields("LX/0cnK;", mapOf("A05" to "Ljava/util/List;", "A02" to "Ljava/lang/String;", "A03" to "Ljava/lang/String;", "A06" to "Z"))
        fields("LX/0WDs;", mapOf("A0A" to "Ljava/util/List;", "A0C" to "Ljava/util/List;", "A01" to "LX/0HQV;", "A02" to "Lcom/instagram/api/schemas/TopSerpOtherResultsImpl;"))
        fields("LX/0YCr;", mapOf("A07" to "Lcom/instagram/user/model/User;"))
        parser("LX/0XEv;", "searchGrid", setOf("sections", "has_more", "next_max_id", "rank_token"))
        parser("LX/0XFD;", "serpEntities", setOf("list", "background_color"))
        parser("LX/0XFE;", "serpEntities", setOf("users", "upsell_cards"))
        parser("LX/0XEu;", "serpEntities", setOf("results"))
        parser("LX/0XEw;", "serpEntities", setOf("items"))

        // This provider belongs to Instagram's main Search, not the interfaces also
        // used for DM recipients, location pickers and other account selectors.
        val provider = mutableClassDefBy("LX/0I4B;")
        check(provider.methods.single { it.name == "GDd" }.discoverStrings().contains(
            "MainSearchResultsProvider no longer supports single-query population. Use populateResultsForMultipleQueries(...)."
        ))
        fields("LX/0I4B;", mapOf("A00" to "Lcom/instagram/common/session/UserSession;"))
        fields("LX/0I2T;", mapOf("A00" to "Ljava/util/List;", "A01" to "Ljava/util/List;"))
        fields("LX/0C9d;", mapOf("A01" to "Lcom/instagram/user/model/User;"))
        fields("LX/0I7G;", mapOf("A00" to "LX/0C9d;"))
        for (name in listOf("GDb", "GDc")) {
            val method = provider.methods.single { it.name == name && it.returnType == "LX/0I2T;" }
            val returns = method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }.toList()
            check(returns.size == 1)
            for ((index, instruction) in returns.asReversed()) {
                val result = (instruction as OneRegisterInstruction).registerA
                method.replaceInstruction(index, "invoke-static/range {p0 .. p0}, $DISCOVER->searchOwner(Ljava/lang/Object;)V")
                method.addInstructions(index + 1, """
                    invoke-static/range {v$result .. v$result}, $DISCOVER->searchState(Ljava/lang/Object;)V
                    return-object v$result
                """.trimIndent())
            }
        }
    }
}
