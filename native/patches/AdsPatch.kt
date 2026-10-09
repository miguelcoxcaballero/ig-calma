package es.calma.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/** Independently mapped stock sponsored-Story insertion boundary. */
val calmaNativeAds = bytecodePatch(
    name = "Calma native ads",
    description = "Reject sponsored Story insertions while preserving the original Story player"
) {
    compatibleWith("com.instagram.android"("439.0.0.37.89"))
    dependsOn(calmaExtensionPatch)
    execute {
        val reel = mutableClassDefBy("LX/03sn;")
        val sponsored = reel.methods.single { it.name == "EKS" && it.parameterTypes.isEmpty() && it.returnType == "Z" }
        check(sponsored.implementation!!.instructions.any {
            val ref = (it as? ReferenceInstruction)?.reference as? FieldReference
            ref?.definingClass == "LX/03st;" && ref.name == "A04"
        }) { "Stock sponsored Story predicate changed" }
        val kindStrings = mutableClassDefBy("LX/03st;").methods.single { it.name == "<clinit>" }
            .implementation!!.instructions.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }.toSet()
        check(kindStrings.containsAll(setOf("ADS_REEL", "ads_reel")))
        val insertion = mutableClassDefBy("LX/08S0;").methods.single {
            it.name == "E1e" && it.parameterTypes == listOf("LX/0AO1;", "LX/02mF;", "LX/04ae;", "I") && it.returnType == "Ljava/lang/Integer;"
        }
        val instructions = insertion.implementation!!.instructions.toList()
        check(insertion.implementation!!.registerCount == 16)
        check(instructions.any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "Inserted ad/netego at position %d" })
        val index = instructions.indexOfFirst {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            it.opcode == Opcode.INVOKE_VIRTUAL && ref?.definingClass == "LX/03sn;" && ref.name == "A14" && ref.parameterTypes.isEmpty()
        }
        check(index > 0 && instructions[index - 1].opcode == Opcode.IGET_OBJECT)
        val loaded = (instructions[index - 1] as ReferenceInstruction).reference as FieldReference
        check(loaded.definingClass == "LX/04sN;" && loaded.name == "A0U" && loaded.type == "LX/03sn;")
        val target = (instructions[index] as FiveRegisterInstruction).registerC
        check(instructions[index + 1].opcode == Opcode.MOVE_RESULT)
        val scratch = (instructions[index + 1] as OneRegisterInstruction).registerA
        check(target != scratch && scratch < 11)
        // Replace the original call so every existing incoming label reaches the
        // guard. Its original move-result is the continuation after A14 below.
        insertion.replaceInstruction(index, "invoke-static/range {v$target .. v$target}, Les/calma/instagram/nativeapp/NativeAds;->story(Ljava/lang/Object;)Z")
        insertion.addInstructionsWithLabels(index + 1, """
            move-result v$scratch
            if-eqz v$scratch, :calma_organic_story
            const/16 v$scratch, 0x8
            invoke-static {v$scratch}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
            move-result-object v$scratch
            return-object v$scratch
            :calma_organic_story
            invoke-virtual {v$target}, LX/03sn;->A14()Z
        """.trimIndent())
    }
}
