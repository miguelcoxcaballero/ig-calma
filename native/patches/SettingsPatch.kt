package es.calma.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SETTINGS = "Les/calma/instagram/nativeapp/CalmaSettings;"

/** Adds an original Calma row to the stock Settings2 models, without view overlays. */
val calmaNativeSettings = bytecodePatch(
    name = "Calma native settings",
    description = "Tu feed inside Instagram Settings, native controls and the existing Inhouse updater"
) {
    compatibleWith("com.instagram.android"("439.0.0.37.89"))
    dependsOn(calmaExtensionPatch)
    execute {
        // Fail the patch if any reflected model contract changes; never silently
        // ship a settings row that cannot be constructed on this stock build.
        val row = mutableClassDefBy("LX/0E6t;")
        check(row.methods.single { it.name == "<init>" }.parameterTypes.map { it.toString() } == listOf(
            "LX/0QnG;", "LX/0Qq1;", "LX/0vAE;", "LX/0R4S;",
            "Lcom/instagram/settings2/core/model/FbtModel;", "Lcom/instagram/settings2/core/model/FbtModel;",
            "Lcom/instagram/settings2/core/model/FbtModel;", "Lcom/instagram/settings2/core/model/FbtModel;",
            "LX/0K6G;", "Ljava/lang/Integer;", "Z", "Z", "Z"
        ))
        check(mutableClassDefBy("LX/0vAE;").accessFlags and 0x201 == 0x201)
        check(mutableClassDefBy("LX/00nM;").methods.any { it.name == "A00" &&
            it.parameterTypes == listOf("Ljava/lang/Iterable;") && it.returnType == "LX/03kU;" })
        check(mutableClassDefBy("LX/0E72;").methods.single { it.name == "<init>" }.parameterTypes == listOf("LX/03kU;", "Z"))
        check(mutableClassDefBy("LX/0R4P;").methods.single { it.name == "<init>" }.parameterTypes == listOf("Ljava/lang/Object;"))
        check(mutableClassDefBy("Lcom/instagram/settings2/core/model/FbtModel;").methods.single { it.name == "<init>" }.parameterTypes == listOf(
            "Lcom/instagram/settings2/core/model/FbtModelSource;", "LX/03kU;"
        ))
        check(mutableClassDefBy("Lcom/instagram/settings2/core/model/FbtModelSource\$Literal;").methods.single { it.name == "<init>" }.parameterTypes == listOf("Ljava/lang/String;"))

        // Redex removes this model's empty constructor. Match stock allocation instead
        // of reflective construction or hidden Android allocation APIs.
        val section = mutableClassDefBy("LX/0R5u;")
        check(section.methods.none { it.name == "<init>" })
        check(section.fields.map { it.name to it.type }.containsAll(listOf(
            "A00" to "LX/0vAF;", "A01" to "Lcom/instagram/settings2/core/model/FbtModel;",
            "A02" to "LX/03kU;", "A03" to "LX/03kU;"
        )))
        val factory = mutableClassDefBy(SETTINGS).methods.single { it.name == "newSection" }
        check(factory.implementation!!.registerCount >= 1)
        factory.addInstructions(0, """
            new-instance v0, LX/0R5u;
            invoke-direct {v0}, Ljava/lang/Object;-><init>()V
            return-object v0
        """.trimIndent())

        val state = mutableClassDefBy("LX/0E71;")
        check(state.methods.single { it.name == "toString" }.implementation!!.instructions.any {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "UiState(title="
        }) { "Stock settings state changed" }
        val constructor = state.methods.single { it.name == "<init>" }
        check(constructor.parameterTypes.map { it.toString() } == listOf(
            "LX/0OPj;", "Lcom/instagram/settings2/core/model/FbtModel;", "LX/0TH6;", "LX/0RJd;",
            "Ljava/lang/String;", "LX/03kU;", "Z", "Z", "Z", "Z"
        ))
        check(constructor.implementation!!.registerCount == 11)
        constructor.addInstructions(0, """
            invoke-static {p1, p4}, $SETTINGS->content(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object p4
            check-cast p4, LX/0RJd;
        """.trimIndent())

        val viewModel = mutableClassDefBy("Lcom/instagram/settings2/core/viewmodel/SettingsScreenViewModel;")
        val navigate = viewModel.methods.single { it.name == "A03" }
        check(navigate.parameterTypes.map { it.toString() } == listOf(
            "LX/0R4S;", "Lcom/instagram/settings2/core/viewmodel/SettingsScreenViewModel;", "LX/09oa;"
        ) && navigate.returnType == "Ljava/lang/Object;")
        check(navigate.implementation!!.registerCount > 3)
        navigate.addInstructionsWithLabels(0, """
            invoke-static/range {p0 .. p0}, $SETTINGS->open(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :calma_original
            sget-object v0, LX/04i9;->A00:LX/04i9;
            return-object v0
        """.trimIndent(), ExternalLabel("calma_original", navigate.implementation!!.instructions.first()))

        val fragment = mutableClassDefBy("LX/0E6Z;")
        check(fragment.fields.any { it.name == "__redex_internal_original_name" &&
            (it.initialValue as? com.android.tools.smali.dexlib2.iface.value.StringEncodedValue)?.value == "SettingsScreenFragment" })
        val created = fragment.methods.single { it.name == "onViewCreated" }
        check(created.parameterTypes.map { it.toString() } == listOf("Landroid/view/View;", "Landroid/os/Bundle;"))
        created.addInstructions(0, "invoke-static/range {p1 .. p1}, $SETTINGS->bind(Landroid/view/View;)V")
    }
}
