import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.analysis.ClassPath;
import com.android.tools.smali.dexlib2.analysis.ClassProvider;
import com.android.tools.smali.dexlib2.analysis.DexClassProvider;
import com.android.tools.smali.dexlib2.analysis.MethodAnalyzer;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Analyze register data flow in the shipped DEX, across the complete APK class path.
 * This is a dexlib analysis, not an ART verifier or an Android boot test. In particular,
 * Android boot classes and native loaders are outside this check's scope.
 */
public final class HookDexAnalysis {
    private static final String OWN = "Les/calma/instagram/nativeapp/";
    private static final String BLOCKED = OWN + "BlockedReelsFragment;";
    private static final String SETTINGS = OWN + "CalmaSettings;";

    private static void check(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }

    private static boolean sameMethod(MethodReference a, MethodReference b) {
        return a.getName().equals(b.getName())
                && a.getReturnType().equals(b.getReturnType())
                && a.getParameterTypes().equals(b.getParameterTypes());
    }

    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        var providers = new ArrayList<ClassProvider>();
        var classes = new HashMap<String, ClassDef>();
        for (String name : container.getDexEntryNames()) {
            var dex = container.getEntry(name).getDexFile();
            providers.add(new DexClassProvider(dex));
            for (ClassDef type : dex.getClasses()) {
                check(classes.put(type.getType(), type) == null, "Duplicate class: " + type.getType());
            }
        }
        var selected = new LinkedHashSet<Method>();
        var helpers = new HashSet<String>();
        int stockMethods = 0;
        int generatedMethods = 0;
        for (ClassDef type : classes.values()) {
            for (Method method : type.getMethods()) {
                if (method.getImplementation() == null) continue;
                if (BLOCKED.equals(type.getType())
                        || (SETTINGS.equals(type.getType()) && "newSection".equals(method.getName()))
                        || ((OWN + "NativeFeedEnd;").equals(type.getType()) && "newModel".equals(method.getName()))) {
                    selected.add(method);
                    generatedMethods++;
                }
                if (type.getType().startsWith("Les/calma/")) continue;
                boolean hooked = false;
                for (var instruction : method.getImplementation().getInstructions()) {
                    if (!(instruction instanceof ReferenceInstruction reference)
                            || !(reference.getReference() instanceof MethodReference call)
                            || !call.getDefiningClass().startsWith(OWN)) continue;
                    hooked = true;
                    ClassDef target = classes.get(call.getDefiningClass());
                    check(target != null, "Missing extension class: " + call);
                    check((target.getAccessFlags() & 1) != 0, "Extension class is inaccessible: " + call);
                    Method resolved = null;
                    for (Method candidate : target.getMethods()) {
                        if (sameMethod(candidate, call)) { resolved = candidate; break; }
                    }
                    check(resolved != null, "Missing extension method: " + call + " from " + method);
                    check((resolved.getAccessFlags() & 1) != 0, "Extension method is inaccessible: " + call);
                    boolean staticCall = instruction.getOpcode().name.startsWith("invoke-static");
                    check(staticCall == ((resolved.getAccessFlags() & 8) != 0),
                            "Static/instance invocation mismatch: " + call + " from " + method);
                    if (!BLOCKED.equals(call.getDefiningClass())) helpers.add(call.getDefiningClass());
                }
                if (hooked) {
                    selected.add(method); stockMethods++;
                    if (type.getType().equals("Lcom/instagram/actionbar/ActionBarTitleViewSwitcher;")
                            || (type.getType().equals("LX/06nZ;") && method.getName().equals("A01"))) {
                        // A stock branch to return-void must not jump past our final title repair.
                        var offsets = new HashMap<Integer, com.android.tools.smali.dexlib2.iface.instruction.Instruction>();
                        int offset = 0;
                        for (var instruction : method.getImplementation().getInstructions()) {
                            offsets.put(offset, instruction); offset += instruction.getCodeUnits();
                        }
                        offset = 0;
                        for (var instruction : method.getImplementation().getInstructions()) {
                            if (instruction instanceof com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction branch) {
                                var target = offsets.get(offset + branch.getCodeOffset());
                                if (type.getType().equals("Lcom/instagram/actionbar/ActionBarTitleViewSwitcher;"))
                                    check(target == null || target.getOpcode() != com.android.tools.smali.dexlib2.Opcode.RETURN_VOID,
                                        "Stock branch bypasses Home header repair: " + method);
                                check(!(target instanceof ReferenceInstruction callTarget)
                                    || !callTarget.getReference().toString().equals("LX/004C;->A0b(III)Landroid/view/View;"),
                                    "Stock branch bypasses Home layout selection: " + method);
                            }
                            offset += instruction.getCodeUnits();
                        }
                    }
                }
            }
        }
        Set<String> expected = Set.of(OWN + "NativeFeed;", OWN + "NativeRelations;",
                OWN + "NativeDiscover;", OWN + "CalmaReels;", OWN + "NativeFeedHeader;", OWN + "NativeFeedSelector;", OWN + "NativeFeedBudget;", SETTINGS);
        check(helpers.containsAll(expected), "Missing hook families: " + expected + "; found " + helpers);
        check(generatedMethods == 6, "Missing generated fragment/factory methods: " + generatedMethods);
        // ART-style field layout for dexlib; this does not select an Android device runtime.
        var classPath = new ClassPath(providers, false, 87);
        var failures = new ArrayList<String>();
        for (Method method : selected) {
            try {
                var analyzer = new MethodAnalyzer(classPath, method, null, false);
                var failure = analyzer.getAnalysisException();
                if (failure != null) failures.add(method + ": " + failure);
            } catch (RuntimeException failure) {
                failures.add(method + ": " + failure);
            }
        }
        check(failures.isEmpty(), "DEX register analysis failed:\n" + String.join("\n", failures));
        System.out.println("DEX register data flow and extension linkage: " + stockMethods
                + " hooked stock methods, " + generatedMethods + " generated methods; "
                + container.getDexEntryNames().size() + " DEX files on APK class path."
                + " This check does not execute Android.");
    }
}
