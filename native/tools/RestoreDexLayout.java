package es.calma.tools;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restore Instagram's dex/canary membership after the generic patch writer. */
public final class RestoreDexLayout {
    private static int ordinal(String name) {
        if (name.equals("classes.dex")) return 0;
        if (!name.matches("classes[2-9][0-9]*\\.dex|classes1[0-9]+\\.dex")) {
            throw new IllegalArgumentException("Unexpected DEX entry: " + name);
        }
        return Integer.parseInt(name.substring(7, name.length() - 4)) - 1;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("RestoreDexLayout stock.apk patched.apk output-directory");
        var stock = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        var patched = DexFileFactory.loadDexContainer(new File(args[1]), Opcodes.getDefault());
        Map<String, ClassDef> definitions = new LinkedHashMap<>();
        for (String name : patched.getDexEntryNames()) {
            for (ClassDef type : patched.getEntry(name).getDexFile().getClasses()) {
                if (definitions.put(type.getType(), type) != null) {
                    throw new IllegalArgumentException("Duplicate class: " + type.getType());
                }
            }
        }
        File output = new File(args[2]);
        if (!output.isDirectory()) throw new IllegalArgumentException("Output directory does not exist");
        List<String> entries = new ArrayList<>(stock.getDexEntryNames());
        entries.sort(Comparator.comparingInt(RestoreDexLayout::ordinal));
        for (int index = 0; index < entries.size(); index++) {
            String name = entries.get(index);
            if (ordinal(name) != index) throw new IllegalArgumentException("Non-contiguous stock DEX sequence");
            String canary = String.format("Lsecondary/dex%02d/Canary;", index);
            List<ClassDef> classes = new ArrayList<>();
            boolean foundCanary = false;
            for (ClassDef original : stock.getEntry(name).getDexFile().getClasses()) {
                ClassDef replacement = definitions.remove(original.getType());
                if (replacement == null) throw new IllegalArgumentException("Missing stock class: " + original.getType());
                classes.add(replacement);
                foundCanary |= replacement.getType().equals(canary);
            }
            if (!foundCanary) throw new IllegalArgumentException("Missing stock canary " + canary + " in " + name);
            // DexFileFactory fails if a patched stock partition exceeds DEX limits.
            // Never silently repartition it and invalidate the startup metadata again.
            DexFileFactory.writeDexFile(new File(output, name).getPath(), new ImmutableDexFile(Opcodes.getDefault(), classes));
            System.out.println(name + ": " + classes.size() + " original classes");
        }
        if (definitions.isEmpty()) throw new IllegalArgumentException("Calma extension is missing");
        for (String type : definitions.keySet()) {
            if (!type.startsWith("Les/calma/")) throw new IllegalArgumentException("Unshaded extension class: " + type);
        }
        String canary = String.format("Lsecondary/dex%02d/Canary;", entries.size());
        List<ClassDef> extension = new ArrayList<>(definitions.values());
        extension.add(new ImmutableClassDef(canary, 0x401, "Ljava/lang/Object;", List.of(), null, List.of(), List.of(), List.of()));
        String name = "classes" + (entries.size() + 1) + ".dex";
        DexFileFactory.writeDexFile(new File(output, name).getPath(), new ImmutableDexFile(Opcodes.getDefault(), extension));
        System.out.println(name + ": " + extension.size() + " Calma classes and canary");
    }
}
