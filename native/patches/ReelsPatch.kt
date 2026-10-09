package es.calma.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val REELS = "Les/calma/instagram/nativeapp/CalmaReels;"
private const val CONFIG = "Lcom/instagram/clips/intf/ClipsViewerConfig;"
private const val SESSION = "Lcom/instagram/common/session/UserSession;"

private fun MutableMethod.hasText(text: String): Boolean = implementation!!.instructions.any {
    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == text
}

/** Independently mapped from stock DEX, never imported from a third-party patch bundle. */
val calmaNativeReels = bytecodePatch(
    name = "Calma native Reels",
    description = "Native mutual-friend DM clips with one-clip playback and Reels navigation disabled"
) {
    compatibleWith("com.instagram.android"("439.0.0.37.89"))
    dependsOn(calmaExtensionPatch)
    extendWith(java.util.function.Supplier { blockedReelsDex() })
    execute {
        val config = mutableClassDefBy(CONFIG)
        val constructor = config.methods.single { it.name == "<init>" }
        for (fieldName in listOf("A2u", "A27", "A28", "A2o", "A2N", "A2O", "A20", "A3b", "A2t", "A24", "A25", "A2n", "A2r", "A3H")) {
            check(config.fields.any { it.name == fieldName && it.type == "Z" }) { "Stock Reels configuration changed: $fieldName" }
        }
        check(config.fields.any { it.name == "A07" && it.type == "I" })
        check(config.fields.any { it.name == "A1k" && it.type == "Ljava/lang/String;" })
        check(config.fields.any { it.name == "A1N" && it.type == "Ljava/lang/String;" })
        check(config.fields.any { it.name == "A0G" && it.type == "Lcom/google/common/collect/ImmutableList;" })
        check(config.fields.any { it.name == "A0N" && it.type == "Lcom/instagram/clips/intf/ClipsViewerDirectData;" })
        val directData = classDefBy("Lcom/instagram/clips/intf/ClipsViewerDirectData;")
        check(directData.fields.any { it.name == "A02" && it.type == "Ljava/lang/String;" })
        check(config.methods.single { it.name == "toString" }.hasText(", shouldForceDisableTailLoads="))
        constructor.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }.reversed().forEach {
            constructor.replaceInstruction(it, "invoke-static/range {p0 .. p0}, $REELS->configure(Ljava/lang/Object;)V")
            constructor.addInstructions(it + 1, "return-void")
        }
        val launchers = mutableClassDefBy("LX/03zs;")
        val targets = mapOf(
            "A06" to "android_purge_26_q2_ClipsPluginImpl_maybePrefetchClipsViewer",
            "A09" to "android_purge_26_q2_ClipsPluginImpl_launchClipsViewer_4",
            "A0A" to "android_purge_26_q2_ClipsPluginImpl_launchClipsViewerForResultFromFragment_2",
            "A0B" to "android_purge_26_q2_ClipsPluginImpl_launchClipsViewerFromDirect"
        )
        for ((name, fingerprint) in targets) {
            val method = launchers.methods.single { it.name == name && it.returnType == "V" }
            check(method.accessFlags and 8 != 0)
            check(method.hasText(fingerprint)) { "Stock Reels launcher changed: $name" }
            val configIndex = method.parameterTypes.indexOf(CONFIG)
            check(configIndex >= 0 && method.parameterTypes[configIndex + 1] == SESSION)
            check(method.implementation!!.registerCount > method.parameterTypes.size) { "Launcher needs a local register" }
            val guard = if (name == "A06") {
                "invoke-static/range {p$configIndex .. p${configIndex + 1}}, $REELS->allowLaunch(Ljava/lang/Object;Ljava/lang/Object;)Z"
            } else {
                check(method.implementation!!.registerCount - method.parameterTypes.size >= 3)
                buildString {
                    appendLine("const/16 v0, ${method.parameterTypes.size}")
                    appendLine("new-array v0, v0, [Ljava/lang/Object;")
                    method.parameterTypes.forEachIndexed { index, type ->
                        appendLine("const/16 v1, $index")
                        when (type.toString()) {
                            "Z" -> {
                                appendLine("invoke-static/range {p$index .. p$index}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;")
                                appendLine("move-result-object v2")
                                appendLine("aput-object v2, v0, v1")
                            }
                            "I" -> {
                                appendLine("invoke-static/range {p$index .. p$index}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;")
                                appendLine("move-result-object v2")
                                appendLine("aput-object v2, v0, v1")
                            }
                            else -> appendLine("aput-object p$index, v0, v1")
                        }
                    }
                    appendLine("const-string v1, \"$name\"")
                    appendLine("invoke-static {v1, v0}, $REELS->launchOrDefer(Ljava/lang/String;[Ljava/lang/Object;)Z")
                }
            }
            method.addInstructionsWithLabels(0, guard + "\n" + """

                move-result v0
                if-nez v0, :calma_original
                return-void
            """.trimIndent(), ExternalLabel("calma_original", method.implementation!!.instructions.first()))
        }
        val sessionHelpers = classDefBy("LX/00Nv;")
        check(sessionHelpers.methods.any { it.name == "A02" && it.parameterTypes.map { it.toString() } == listOf("Landroidx/fragment/app/Fragment;") && it.returnType == "LX/0C8T;" })
        check(sessionHelpers.methods.any { it.name == "A03" && it.parameterTypes.map { it.toString() } == listOf("Landroid/os/Bundle;", "LX/02rJ;") && it.returnType == "V" })
        check(classDefBy("LX/03z9;").methods.any { it.name == "<init>" && it.parameterTypes.isEmpty() && it.accessFlags and 1 != 0 })
        check(classDefBy("LX/0C8T;").methods.any { it.name == "getValue" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;" })
        // All explicit native viewer and tab factories return a valid native fallback when denied.
        val factory = mutableClassDefBy("LX/05Bx;").methods.single { it.name == "A0A" }
        check(factory.hasText("android_purge_26_q2_ClipsFragmentFactoryImpl_newClipsViewerFragment"))
        check(factory.parameterTypes.map { it.toString() } == listOf("Landroid/os/Bundle;", SESSION))
        factory.addInstructionsWithLabels(0, """
            invoke-static/range {p1 .. p2}, $REELS->allowBundle(Landroid/os/Bundle;Ljava/lang/Object;)Z
            move-result v0
            if-nez v0, :calma_original
            move-object/from16 v1, p1
            move-object/from16 v2, p2
            invoke-static {v1, v2}, LX/00Nv;->A03(Landroid/os/Bundle;LX/02rJ;)V
            new-instance v0, $BLOCKED_REELS
            invoke-direct {v0}, $BLOCKED_REELS-><init>()V
            invoke-virtual {v0, v1}, Landroidx/fragment/app/Fragment;->setArguments(Landroid/os/Bundle;)V
            return-object v0
        """.trimIndent(), ExternalLabel("calma_original", factory.implementation!!.instructions.first()))
        val tabFactory = mutableClassDefBy("LX/01xt;").methods.single { it.name == "A0C" && it.parameterTypes.map { it.toString() } == listOf(SESSION) }
        check(tabFactory.hasText("android_purge_26_q2_ClipsFragmentFactoryImpl_newClipsTabFragment"))
        tabFactory.addInstructionsWithLabels(0, """
            invoke-static {}, $REELS->locked()Z
            move-result v0
            if-eqz v0, :calma_original
            new-instance v1, Landroid/os/Bundle;
            invoke-direct {v1}, Landroid/os/Bundle;-><init>()V
            move-object/from16 v2, p0
            invoke-static {v1, v2}, LX/00Nv;->A03(Landroid/os/Bundle;LX/02rJ;)V
            new-instance v0, $BLOCKED_REELS
            invoke-direct {v0}, $BLOCKED_REELS-><init>()V
            invoke-virtual {v0, v1}, Landroidx/fragment/app/Fragment;->setArguments(Landroid/os/Bundle;)V
            return-object v0
        """.trimIndent(), ExternalLabel("calma_original", tabFactory.implementation!!.instructions.first()))
        // Fragment restoration can bypass both factories. Its class loader resolves a safe Fragment.
        val restoreLoader = mutableClassDefBy("LX/00dl;").methods.single { it.name == "A00" }
        check(restoreLoader.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;", "Ljava/lang/ClassLoader;"))
        check(restoreLoader.hasText("Unable to instantiate fragment "))
        restoreLoader.addInstructions(0, """
            invoke-static/range {p0 .. p0}, $REELS->restoredClass(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p0
        """.trimIndent())
        val pager = mutableClassDefBy("LX/019Z;")
        check(pager.fields.any { it.name == "A0P" && it.type == CONFIG })
        val setup = pager.methods.single { it.name == "A0W" }
        check(setup.hasText("android_purge_26_q3_ClipsViewPagerImpl_setupView"))
        setup.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }.reversed().forEach {
            setup.replaceInstruction(it, "invoke-static/range {p0 .. p0}, $REELS->lockPager(Ljava/lang/Object;)V")
            setup.addInstructions(it + 1, "return-void")
        }
        val scrollMethods = mapOf("A0O" to "enableScrolling", "A0N" to "enableForwardScrolling", "A0R" to "smoothScrollToNextItem", "A0S" to "scrollForPeekBounce")
        for ((name, operation) in scrollMethods) {
            val method = pager.methods.single { it.name == name && it.returnType == "V" }
            check(method.hasText("android_purge_26_q3_ClipsViewPagerImpl_$operation"))
            check(method.implementation!!.registerCount > method.parameterTypes.size + 1)
            method.addInstructionsWithLabels(0, """
                invoke-static {}, $REELS->locked()Z
                move-result v0
                if-eqz v0, :calma_original
                invoke-static/range {p0 .. p0}, $REELS->lockPager(Ljava/lang/Object;)V
                return-void
            """.trimIndent(), ExternalLabel("calma_original", method.implementation!!.instructions.first()))
        }
        val select = pager.methods.single { it.name == "A03" }
        check(select.accessFlags and 8 != 0 && select.parameterTypes.map { it.toString() } == listOf("LX/019Z;", "I", "Z"))
        check(select.hasText("android_purge_26_q3_ClipsViewPagerImpl_setCurrentItemInternal"))
        select.addInstructionsWithLabels(0, """
            invoke-static/range {p1 .. p1}, $REELS->maySelect(I)Z
            move-result v0
            if-nez v0, :calma_original
            return-void
        """.trimIndent(), ExternalLabel("calma_original", select.implementation!!.instructions.first()))
    }
}
