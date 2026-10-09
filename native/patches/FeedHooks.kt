package es.calma.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val FEED = "Les/calma/instagram/nativeapp/NativeFeed;"

/** Original Calma integration, mapped independently against the pinned stock APK. */
val calmaNativeFeed = bytecodePatch(
    name = "Calma native feed",
    description = "Follow and mutual filters plus a chronological 48-hour native feed"
) {
    compatibleWith("com.instagram.android"("439.0.0.37.89"))
    dependsOn(calmaExtensionPatch)
    execute {
        val responseClass = mutableClassDefBy("LX/07do;")
        for ((name, type) in mapOf("A0S" to "Ljava/util/List;", "A0E" to "Ljava/lang/Boolean;", "A0J" to "Ljava/lang/Integer;", "A08" to "LX/012Y;", "A0U" to "Ljava/util/List;", "A0a" to "Z", "A0W" to "Z", "A0N" to "Ljava/lang/String;", "A0O" to "Ljava/lang/String;", "A0P" to "Ljava/lang/String;")) {
            check(responseClass.fields.any { it.name == name && it.type == type }) { "Stock feed response changed: $name" }
        }
        val parser = mutableClassDefBy("LX/02px;").methods.single { it.name == "unsafeParseFromJson" }
        val text = parser.implementation!!.instructions.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }.toSet()
        check(text.containsAll(listOf("feed_items", "pagination_source", "more_available", "next_max_id", "disable_client_insertions", "max_num_possible_ad_insertions")))
        // All return paths are checked. Null responses are deliberately left alone by the extension.
        parser.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }.toList().reversed().forEach {
            val result = (it.value as OneRegisterInstruction).registerA
            check(parser.implementation!!.registerCount <= 16)
            parser.replaceInstruction(it.index, "invoke-static {v$result, p1}, $FEED->response(Ljava/lang/Object;Ljava/lang/Object;)V")
            parser.addInstructions(it.index + 1, "return-object v$result")
        }
        val params = mutableClassDefBy("LX/02qb;").methods.single { it.name == "A01" && it.parameterTypes.size == 6 }
        check(params.parameterTypes[3] == "Lcom/instagram/common/session/UserSession;")
        val reads = params.implementation!!.instructions.withIndex().filter {
            val field = ((it.value as? ReferenceInstruction)?.reference as? FieldReference)
            it.value.opcode == Opcode.IGET_OBJECT && field?.definingClass == "LX/02pp;" && field.name == "A0L" && field.type == "Ljava/util/Map;"
        }.toList()
        check(reads.size == 1) { "Stock feed request map must have one read" }
        // Correlate a response with its requesting account and head/tail request.
        // Insert after finding original instruction offsets, then update the map offset.
        params.addInstructions(0, "invoke-static/range {p0 .. p5}, $FEED->context(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")
        val read = reads.single()
        val register = (read.value as TwoRegisterInstruction).registerA
        params.addInstructions(read.index + 2, """
            invoke-static/range {v$register .. v$register}, $FEED->parameters(Ljava/util/Map;)Ljava/util/Map;
            move-result-object v$register
        """.trimIndent())
        // Reject a changed native transport/codec rather than silently shipping a broken cache.
        check(mutableClassDefBy("LX/02pp;").methods.any { it.name == "<init>" && it.parameterTypes.size == 25 })
        check(mutableClassDefBy("LX/02dy;").methods.any { it.name == "A01" && it.parameterTypes.size == 5 && it.returnType == "LX/03Da;" })
        check(mutableClassDefBy("LX/02pk;").fields.any { it.name == "A0R" && it.type == "LX/02pk;" })
        check(mutableClassDefBy("LX/0AMQ;").methods.none { it.name == "<init>" })
        check(mutableClassDefBy("LX/0AMQ;").superclass == "LX/03u8;")
        check(mutableClassDefBy("LX/03u8;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("Ljava/lang/String;") })
        val endFactory = mutableClassDefBy("Les/calma/instagram/nativeapp/NativeFeedEnd;").methods.single { it.name == "newModel" }
        check(endFactory.implementation!!.registerCount >= 2)
        endFactory.addInstructions(0, """
            new-instance v0, LX/0AMQ;
            const-string v1, "XDTEndOfFeedDemarcator"
            invoke-direct {v0, v1}, LX/03u8;-><init>(Ljava/lang/String;)V
            return-object v0
        """.trimIndent())
        check(mutableClassDefBy("LX/06qT;").methods.any { it.name == "<init>" && it.parameterTypes.map { t -> t.toString() } == listOf("LX/0AMQ;", "LX/04a3;") })
        check(mutableClassDefBy("Lcom/instagram/feed/media/MediaExtKt;").methods.any { it.name == "A1h" && it.parameterTypes.map { t -> t.toString() } == listOf("Lcom/instagram/feed/media/Media;") && it.returnType == "[B" })
        check(mutableClassDefBy("LX/04ve;").methods.any { it.name == "A00" && it.parameterTypes.map { t -> t.toString() } == listOf("Lcom/instagram/common/session/UserSession;", "[B") })
        val endBinder = mutableClassDefBy("LX/06gK;").methods.single { it.name == "bindView" }
        check(endBinder.parameterTypes.map { it.toString() } == listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"))
        val originalBind = endBinder.implementation!!.instructions.first()
        // v0 is a local before the original body; p2/p3 are the original row and content.
        check(endBinder.implementation!!.registerCount - 5 >= 1)
        endBinder.addInstructionsWithLabels(0, """
            invoke-static/range {p2 .. p3}, Les/calma/instagram/nativeapp/NativeFeedEnd;->bind(Landroid/view/View;Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :calma_original_end_row
            return-void
        """.trimIndent(), ExternalLabel("calma_original_end_row", originalBind))
        val wrapper = mutableClassDefBy("LX/05qw;")
        check(wrapper.methods.any { it.name == "A0A" && it.returnType == "Lcom/instagram/feed/media/Media;" })
        check(wrapper.fields.any { it.name == "A0x" && it.type == "Lcom/instagram/model/reels/netego/Ad4adDictImpl;" })
        check(wrapper.fields.any { it.name == "A0y" && it.type == "LX/02lY;" })
        val sponsored = mutableClassDefBy("Lcom/instagram/feed/media/Media;").methods.single { it.name == "EKS" && it.parameterTypes.isEmpty() && it.returnType == "Z" }
        check(sponsored.implementation!!.instructions.any {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "LX/05ul;" && ref.name == "A00"
        }) { "Stock sponsored-media predicate changed" }
        val media = mutableClassDefBy("Lcom/instagram/feed/media/LiveTreeMediaDict;")
        check(media.methods.any { it.name == "A33" && it.returnType == "Lcom/instagram/user/model/User;" })
        check(media.methods.any { it.name == "A6X" && it.returnType == "Ljava/lang/Long;" })
        check(media.methods.any { it.name == "A7W" && it.returnType == "Ljava/lang/String;" })
        // Preserve verified batch friendship facts even when the stock cache has no
        // User object or intentionally leaves its FriendshipStatus object null.
        val batch = mutableClassDefBy("LX/0ICa;").methods.single { it.name == "A00" }
        val instructions = batch.implementation!!.instructions.toList()
        val lookupIndex = instructions.indexOfFirst {
            val ref = ((it as? ReferenceInstruction)?.reference as? MethodReference)
            ref?.definingClass == "LX/0223;" && ref.name == "A0e" && ref.returnType == "Lcom/instagram/user/model/User;"
        }
        val parseIndex = instructions.indexOfFirst {
            val ref = ((it as? ReferenceInstruction)?.reference as? MethodReference)
            ref?.definingClass == "LX/0BnS;" && ref.name == "A00"
        }
        check(lookupIndex >= 0 && parseIndex > lookupIndex)
        val lookup = instructions[lookupIndex] as FiveRegisterInstruction
        val parse = instructions[parseIndex] as FiveRegisterInstruction
        val statusRegister = parse.registerD
        check(instructions[parseIndex + 1].opcode == Opcode.GOTO)
        check(instructions[parseIndex + 2].opcode == Opcode.IF_EQZ)
        val relations = "Les/calma/instagram/nativeapp/NativeRelations;"
        val guard = instructions[parseIndex + 2] as BuilderOffsetInstruction
        val guardRegister = (guard as OneRegisterInstruction).registerA
        val guardTarget = instructions[guard.target.location.index]
        batch.replaceInstruction(parseIndex + 2, "invoke-static/range {v$statusRegister .. v$statusRegister}, $relations->endStatus(Ljava/lang/Object;)V")
        batch.addInstructionsWithLabels(parseIndex + 3, "if-eqz v$guardRegister, :calma_status_next", ExternalLabel("calma_status_next", guardTarget))
        batch.addInstructions(lookupIndex, "invoke-static {v${lookup.registerC}, v${lookup.registerD}, v$statusRegister}, $relations->beginStatus(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V")
        val status = mutableClassDefBy("LX/0BnY;")
        check(status.fields.any { it.name == "A0H" && it.type == "Z" })
        check(status.fields.any { it.name == "A02" && it.type == "Ljava/lang/Boolean;" })
        val user = mutableClassDefBy("Lcom/instagram/user/model/LiveTreeUserDict;")
        check(user.methods.any { it.name == "C8H" && it.returnType == "Lcom/instagram/user/model/FriendshipStatus;" })
    }
}
