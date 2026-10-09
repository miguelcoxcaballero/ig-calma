package es.calma.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel

private const val SELECTOR = "Les/calma/instagram/nativeapp/NativeFeedSelector;"
private const val BUDGET = "Les/calma/instagram/nativeapp/NativeFeedBudget;"

/** Independently mapped stock popup, native refresh delegate and Home lifecycle. */
val calmaFeedSelector = bytecodePatch(
    name = "Calma four feed modes",
    description = "Native Friends/Following selector and expiring hourly For you time"
) {
    compatibleWith("com.instagram.android"("439.0.0.37.89"))
    dependsOn(calmaNativeFeed)
    execute {
        val enum = mutableClassDefBy("LX/06yP;")
        check(enum.fields.filter { it.type == "LX/06yP;" }.size == 9)
        check(enum.fields.any { it.name == "A01" && it.type == "I" })
        val option = mutableClassDefBy("LX/06yQ;")
        check(option.methods.any { it.name == "<init>" && it.parameterTypes == listOf("LX/06yP;") })
        val state = mutableClassDefBy("LX/06yR;")
        val constructor = state.methods.single { it.name == "<init>" }
        check(constructor.parameterTypes == listOf("Landroid/content/Context;", "Lcom/instagram/common/session/UserSession;", "LX/06yQ;", "Ljava/util/List;"))
        check(constructor.implementation!!.registerCount <= 16 && constructor.implementation!!.registerCount >= 7)
        constructor.addInstructions(0, """
            move-object v0, p2
            move-object v1, p4
            invoke-static {v0, v1}, $SELECTOR->options(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;
            move-result-object p4
            move-object v1, p4
            invoke-static {v0, v1}, $SELECTOR->initial(Ljava/lang/Object;Ljava/util/List;)Ljava/lang/Object;
            move-result-object p3
            check-cast p3, LX/06yQ;
        """.trimIndent())
        val row = mutableClassDefBy("LX/0VTM;")
        check(row.methods.single { it.name == "<init>" }.parameterTypes == listOf(
            "Landroid/graphics/drawable/Drawable;", "Landroid/graphics/drawable/Drawable;", "LX/0nQi;",
            "Ljava/lang/Integer;", "Ljava/lang/String;", "Ljava/lang/String;", "Z", "Z", "Z", "Z", "Z"))
        check(mutableClassDefBy("LX/0LUb;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("I", "Ljava/lang/Object;", "Ljava/lang/Object;") })
        check(mutableClassDefBy("LX/0E4c;").methods.any { it.name == "A09" && it.parameterTypes == listOf("Ljava/util/List;") })
        val click = mutableClassDefBy("LX/0488;").methods.single { it.name == "onClick" }
        check(click.parameterTypes == listOf("Landroid/view/View;"))
        check(click.implementation!!.registerCount - 2 >= 1)
        click.addInstructionsWithLabels(0, """
            invoke-static/range {p0 .. p1}, $SELECTOR->openPicker(Ljava/lang/Object;Landroid/view/View;)Z
            move-result v0
            if-eqz v0, :calma_original_picker
            return-void
        """.trimIndent(), ExternalLabel("calma_original_picker", click.implementation!!.instructions.first()))
        val select = mutableClassDefBy("LX/06yW;").methods.single { it.name == "A00" }
        check(select.parameterTypes == listOf("LX/06yP;"))
        select.addInstructionsWithLabels(0, """
            invoke-static/range {p0 .. p1}, $SELECTOR->select(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :calma_original_selection
            return-void
        """.trimIndent(), ExternalLabel("calma_original_selection", select.implementation!!.instructions.first()))
        val pref = mutableClassDefBy("LX/05qL;").methods.single { it.name == "A01" && it.parameterTypes.isEmpty() }
        check(pref.returnType == "Ljava/lang/String;")
        pref.addInstructions(0, """
            invoke-static/range {p0 .. p0}, $SELECTOR->savedType(Ljava/lang/Object;)Ljava/lang/String;
            move-result-object v0
            return-object v0
        """.trimIndent())
        val header = mutableClassDefBy("LX/06nZ;").methods.single { it.name == "A06" }
        check(header.parameterTypes == listOf("Z"))
        header.addInstructionsWithLabels(0, """
            invoke-static/range {p0 .. p0}, $SELECTOR->header(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :calma_original_header
            return-void
        """.trimIndent(), ExternalLabel("calma_original_header", header.implementation!!.instructions.first()))
        val home = mutableClassDefBy("LX/05pB;")
        val modern = mutableClassDefBy("Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;")
        for (name in listOf("A01", "A03")) {
            val update = modern.methods.single { it.name == name }
            check(update.parameterTypes.take(2) == listOf("Lcom/instagram/common/session/UserSession;", modern.type))
            update.implementation!!.instructions.withIndex().filter { it.value.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID }
                .map { it.index }.reversed().forEach { index ->
                    update.addInstructions(index,"invoke-static/range {p1 .. p1}, $SELECTOR->topBar(Ljava/lang/Object;)V")
                }
        }
        for ((name, helper) in mapOf("onResume" to "homeResumed", "onPause" to "homePaused")) {
            val lifecycle = home.methods.single { it.name == name && it.parameterTypes.isEmpty() }
            // Run after the stock method has applied its Fragment resumed state.
            lifecycle.implementation!!.instructions.withIndex().filter { it.value.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID }
                .map { it.index }.reversed().forEach { index ->
                    lifecycle.replaceInstruction(index, "invoke-static/range {p0 .. p0}, $BUDGET->$helper(Ljava/lang/Object;)V")
                    lifecycle.addInstructions(index+1, "return-void")
                }
        }
        home.methods.single { it.name == "onHiddenChanged" }.addInstructions(0,
            "invoke-static/range {p0 .. p1}, $BUDGET->homeHidden(Ljava/lang/Object;Z)V")
        // Reflection contracts for the stock in-place feed switch are release fingerprints.
        check(mutableClassDefBy("LX/05tI;").methods.any { it.name == "A13" && it.parameterTypes.isEmpty() })
        check(mutableClassDefBy("LX/06yS;").methods.any { it.name == "A01" && it.parameterTypes == listOf("LX/02pk;", "Ljava/util/Map;") })
    }
}
