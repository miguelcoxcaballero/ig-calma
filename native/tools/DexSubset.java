package es.calma.tools;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Extracts only named class definitions for lightweight, local bytecode analysis. */
public final class DexSubset {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("DexSubset <input.apk> <output.dex> <Lclass/type;> ...");
        Set<String> wanted = new LinkedHashSet<>();
        for (int i = 2; i < args.length; i++) {
            String type = args[i];
            if (!type.startsWith("L") || !type.endsWith(";")) type = "L" + type.replace('.', '/') + ";";
            wanted.add(type);
        }
        MultiDexContainer<? extends DexFile> container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        List<ClassDef> definitions = new ArrayList<>();
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
            if (entry == null) continue;
            for (ClassDef type : entry.getDexFile().getClasses()) {
                if (wanted.remove(type.getType())) {
                    definitions.add(type);
                    System.out.println(entryName + " " + type.getType());
                }
            }
            if (wanted.isEmpty()) break;
        }
        if (!wanted.isEmpty()) throw new IllegalArgumentException("Missing classes: " + wanted);
        DexFileFactory.writeDexFile(args[1], new ImmutableDexFile(Opcodes.getDefault(), definitions));
        System.out.println("Extracted " + definitions.size() + " classes to " + args[1]);
    }
}
