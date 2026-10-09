package es.calma.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.ByteArrayInputStream
import java.io.InputStream

internal const val BLOCKED_REELS = "Les/calma/instagram/nativeapp/BlockedReelsFragment;"

/** A normal Instagram Fragment with its native session/lifecycle, never a null factory result. */
internal fun blockedReelsDex(): InputStream {
    fun method(name: String, parameters: List<String>, returns: String, registers: Int, code: String): MutableMethod {
        val method = MutableMethod(ImmutableMethod(BLOCKED_REELS, name,
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
            if (name == "<init>") 0x10001 else 1, emptySet(), emptySet(),
            ImmutableMethodImplementation(registers, emptyList(), emptyList(), emptyList())))
        method.addInstructions(0, code.trimIndent())
        return method
    }
    val methods = listOf(
        method("<init>", emptyList(), "V", 1, """
            invoke-direct {p0}, LX/03z9;-><init>()V
            return-void
        """),
        method("getSession", emptyList(), "LX/02rJ;", 2, """
            invoke-static {p0}, LX/00Nv;->A02(Landroidx/fragment/app/Fragment;)LX/0C8T;
            move-result-object v0
            invoke-interface {v0}, LX/0C8T;->getValue()Ljava/lang/Object;
            move-result-object v0
            check-cast v0, LX/02rJ;
            return-object v0
        """),
        method("getModuleName", emptyList(), "Ljava/lang/String;", 2, """
            const-string v0, "calma_reels_blocked"
            return-object v0
        """),
        method("onCreateView", listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;"), "Landroid/view/View;", 5, """
            invoke-virtual {p1}, Landroid/view/LayoutInflater;->getContext()Landroid/content/Context;
            move-result-object v0
            invoke-static {v0}, Les/calma/instagram/nativeapp/CalmaReels;->blockedView(Landroid/content/Context;)Landroid/view/View;
            move-result-object v0
            return-object v0
        """)
    )
    val definition = ImmutableClassDef(BLOCKED_REELS, 17, "LX/03z9;", emptyList(),
        "ReelsBlockedFragment.kt", emptySet(), emptyList(), methods)
    val store = MemoryDataStore()
    DexPool.writeTo(store, ImmutableDexFile(Opcodes.getDefault(), listOf(definition)))
    return ByteArrayInputStream(store.data)
}
