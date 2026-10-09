import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.util.*;

/** Verify the actual signed APK's control flow, including switch and catch targets. */
public final class DiscoverDexCheck {
    static final String HELPER="Les/calma/instagram/nativeapp/NativeDiscover;";
    static final Map<String,String> TARGETS=new LinkedHashMap<>();
    static {
        TARGETS.put("LX/094e;->unsafeParseFromJson","explore");
        TARGETS.put("LX/0XEv;->unsafeParseFromJson","searchGrid");

    }
    static void check(boolean valid,String detail){if(!valid)throw new AssertionError(detail);}
    static boolean calls(Instruction instruction,String name){
        if(!(instruction instanceof ReferenceInstruction))return false;
        Object reference=((ReferenceInstruction)instruction).getReference();
        return reference instanceof MethodReference && HELPER.equals(((MethodReference)reference).getDefiningClass())
            && name.equals(((MethodReference)reference).getName());
    }
    static void verify(Method method,String hook){
        MethodImplementation body=method.getImplementation();check(body!=null,"missing body "+method);
        List<Instruction> instructions=new ArrayList<>();List<Integer> offsets=new ArrayList<>();Map<Integer,Instruction> at=new HashMap<>();
        int offset=0;
        for(Instruction instruction:body.getInstructions()){
            instructions.add(instruction);offsets.add(offset);at.put(offset,instruction);offset+=instruction.getCodeUnits();
        }
        Set<Integer> bypass=new HashSet<>();int returns=0;
        for(int i=0;i<instructions.size();i++){
            Instruction instruction=instructions.get(i);
            if(instruction.getOpcode()!=Opcode.RETURN_OBJECT)continue;
            returns++;int result=((OneRegisterInstruction)instruction).getRegisterA();bypass.add(offsets.get(i));
            check(i>0 && calls(instructions.get(i-1),hook),"unfiltered return "+method+" @"+offsets.get(i));
            FiveRegisterInstruction filter=(FiveRegisterInstruction)instructions.get(i-1);
            check(filter.getRegisterCount()==2 && filter.getRegisterC()==result
                && filter.getRegisterD()==body.getRegisterCount()-1,"wrong parser/account registers "+method);

        }
        check(returns==2,"unexpected return count "+method);
        for(int i=0;i<instructions.size();i++){
            Instruction instruction=instructions.get(i);int source=offsets.get(i);
            if(instruction instanceof OffsetInstruction){
                int target=source+((OffsetInstruction)instruction).getCodeOffset();
                check(!bypass.contains(target),"branch bypasses hook "+method+" @"+source);
                if(instruction.getOpcode()==Opcode.PACKED_SWITCH || instruction.getOpcode()==Opcode.SPARSE_SWITCH){
                    SwitchPayload payload=(SwitchPayload)at.get(target);
                    for(SwitchElement element:payload.getSwitchElements())
                        check(!bypass.contains(source+element.getOffset()),"switch bypasses hook "+method);
                }
            }
        }
        for(TryBlock<? extends ExceptionHandler> block:body.getTryBlocks())for(ExceptionHandler handler:block.getExceptionHandlers())
            check(!bypass.contains(handler.getHandlerCodeAddress()),"catch bypasses hook "+method);
    }
    static boolean emptyFactory(Instruction instruction){
        if(instruction.getOpcode()!=Opcode.INVOKE_STATIC)return false;
        MethodReference ref=(MethodReference)((ReferenceInstruction)instruction).getReference();
        return ref.getDefiningClass().equals("LX/0I2T;") && ref.getName().equals("A01")
            && ref.getParameterTypes().isEmpty() && ref.getReturnType().equals("LX/0I2T;");
    }
    static void searchEntry(Method method,boolean typed){
        MethodImplementation body=method.getImplementation();
        List<Instruction> ins=new ArrayList<>();for(Instruction i:body.getInstructions())ins.add(i);
        int first=0;
        if(typed){
            check(calls(ins.get(0),"hasQuery"),"typed entry must inspect its own query");
            RegisterRangeInstruction query=(RegisterRangeInstruction)ins.get(0);
            check(query.getRegisterCount()==1 && query.getStartRegister()==body.getRegisterCount()-2,"wrong query parameter register");
            check(ins.get(1).getOpcode()==Opcode.MOVE_RESULT && ((OneRegisterInstruction)ins.get(1)).getRegisterA()==0,"guard result must use local v0");
            check(ins.get(2).getOpcode()==Opcode.IF_NEZ && ((OneRegisterInstruction)ins.get(2)).getRegisterA()==0,"typed queries must retain stock provider");
            int offset=ins.get(0).getCodeUnits()+ins.get(1).getCodeUnits();
            int continuation=0;for(int i=0;i<6;i++)continuation+=ins.get(i).getCodeUnits();
            check(offset+((OffsetInstruction)ins.get(2)).getCodeOffset()==continuation,"typed branch must skip only empty-state return");
            check(ins.get(6).getOpcode()==Opcode.CONST_4 && ((OneRegisterInstruction)ins.get(6)).getRegisterA()==4,"original typed-query body missing");
            first=3;
        }
        check(emptyFactory(ins.get(first)),"blank search must use valid stock empty-state factory");
        check(ins.get(first+1).getOpcode()==Opcode.MOVE_RESULT_OBJECT && ((OneRegisterInstruction)ins.get(first+1)).getRegisterA()==0,"empty state register");
        check(ins.get(first+2).getOpcode()==Opcode.RETURN_OBJECT && ((OneRegisterInstruction)ins.get(first+2)).getRegisterA()==0,"empty search must return before fetching/rendering suggestions");
        for(Instruction i:ins)check(!calls(i,"searchOwner") && !calls(i,"searchState"),"typed account results still have relationship filter");
    }
    public static void main(String[] args)throws Exception{
        MultiDexContainer<? extends DexFile> container=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());
        Set<String> verified=new HashSet<>();boolean runtime=false;int searchEntries=0;int accountParsers=0;
        for(String name:container.getDexEntryNames())for(ClassDef type:container.getEntry(name).getDexFile().getClasses()){
            if(HELPER.equals(type.getType()))runtime=true;
            for(Method method:type.getMethods()){
                if(type.getType().equals("LX/0I4B;") && (method.getName().equals("GDb") || method.getName().equals("GDc"))){
                    searchEntry(method,method.getName().equals("GDb"));searchEntries++;
                }
                if(Arrays.asList("LX/0XFB;","LX/0XFD;","LX/0XFE;","LX/0XEu;","LX/0XEw;").contains(type.getType()) && method.getName().equals("unsafeParseFromJson")){
                    accountParsers++;
                    for(Instruction ins:method.getImplementation().getInstructions())check(!calls(ins,"searchResponse") && !calls(ins,"serpEntities"),"explicit search parser still filters unknown accounts");
                }
                String key=type.getType()+"->"+method.getName();String hook=TARGETS.get(key);
                if(hook!=null){check(verified.add(key),"ambiguous hook method "+key);verify(method,hook);}
            }
        }
        check(runtime,"native Discover implementation absent");
        check(searchEntries==2 && accountParsers==5,"search integration targets missing");
        Set<String> missing=new HashSet<>(TARGETS.keySet());missing.removeAll(verified);
        check(missing.isEmpty(),"missing native Discover hooks: "+missing);
        System.out.println("Native Discover DEX: Explore/search grids, query guards, empty state and unrestricted account parsers verified");
    }
}
