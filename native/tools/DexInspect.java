package es.calma.tools;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import java.io.File;
import java.util.regex.Pattern;

/** Prints precise instruction indices and registers for a previously narrowed DEX. */
public final class DexInspect {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("DexInspect <input.dex> [method regex]");
        Pattern selected = Pattern.compile(args.length > 1 ? args[1] : ".*");
        MultiDexContainer<? extends DexFile> container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexFile> entry = container.getEntry(entryName);
            if (entry == null) continue;
            for (ClassDef type : entry.getDexFile().getClasses()) {
                System.out.println("CLASS " + type.getType() + " EXTENDS " + type.getSuperclass());
                for (Field field : type.getFields()) System.out.println("FIELD " + field);
                for (Method method : type.getMethods()) {
                    if (!selected.matcher(method.getName()).find()) continue;
                    MethodImplementation impl = method.getImplementation();
                    System.out.println("METHOD " + method + " FLAGS " + method.getAccessFlags() + " REGISTERS " + (impl == null ? 0 : impl.getRegisterCount()));
                    if (impl == null) continue;
                    int index = 0, offset = 0;
                    for (Instruction ins : impl.getInstructions()) {
                        StringBuilder line = new StringBuilder(String.format("  %4d @%04x %s", index++, offset, ins.getOpcode().name));
                        if (ins instanceof FiveRegisterInstruction) {
                            FiveRegisterInstruction r = (FiveRegisterInstruction) ins;
                            int[] regs = {r.getRegisterC(), r.getRegisterD(), r.getRegisterE(), r.getRegisterF(), r.getRegisterG()};
                            line.append(" {");
                            for (int i=0;i<r.getRegisterCount();i++) line.append(i == 0 ? "v" : ", v").append(regs[i]);
                            line.append("}");
                        } else if (ins instanceof RegisterRangeInstruction) {
                            RegisterRangeInstruction r = (RegisterRangeInstruction) ins;
                            line.append(" {v").append(r.getStartRegister()).append(" .. v").append(r.getStartRegister()+r.getRegisterCount()-1).append("}");
                        } else if (ins instanceof OneRegisterInstruction) {
                            line.append(" v").append(((OneRegisterInstruction) ins).getRegisterA());
                            if (ins instanceof TwoRegisterInstruction) line.append(", v").append(((TwoRegisterInstruction) ins).getRegisterB());
                            if (ins instanceof ThreeRegisterInstruction) line.append(", v").append(((ThreeRegisterInstruction) ins).getRegisterC());
                        }
                        if (ins instanceof ReferenceInstruction) line.append(" ").append(((ReferenceInstruction) ins).getReference());
                        if (ins instanceof DualReferenceInstruction) line.append(" ").append(((DualReferenceInstruction) ins).getReference2());
                        if (ins instanceof WideLiteralInstruction) line.append(" #").append(((WideLiteralInstruction) ins).getWideLiteral());
                        if (ins instanceof OffsetInstruction) line.append(" -> @").append(Integer.toHexString(offset+((OffsetInstruction) ins).getCodeOffset()));
                        System.out.println(line);
                        offset += ins.getCodeUnits();
                    }
                }
            }
        }
    }
}
