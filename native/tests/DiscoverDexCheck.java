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
        TARGETS.put("LX/0XFB;->unsafeParseFromJson","searchResponse");
        TARGETS.put("LX/0XEv;->unsafeParseFromJson","searchGrid");
        for(String type:new String[]{"0XFD","0XFE","0XEu","0XEw"})
            TARGETS.put("LX/"+type+";->unsafeParseFromJson","serpEntities");
        TARGETS.put("LX/0I4B;->GDb","searchState");
        TARGETS.put("LX/0I4B;->GDc","searchState");
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
            if("searchState".equals(hook)){
                check(i>1 && calls(instructions.get(i-2),"searchOwner"),"missing paired account binding "+method);
                RegisterRangeInstruction bind=(RegisterRangeInstruction)instructions.get(i-2);
                RegisterRangeInstruction filter=(RegisterRangeInstruction)instructions.get(i-1);
                check(bind.getStartRegister()==body.getRegisterCount()-method.getParameterTypes().size()-1,"wrong provider register");
                check(filter.getStartRegister()==result,"wrong result register");
                bypass.add(offsets.get(i-1));
            }else{
                FiveRegisterInstruction filter=(FiveRegisterInstruction)instructions.get(i-1);
                check(filter.getRegisterCount()==2 && filter.getRegisterC()==result
                    && filter.getRegisterD()==body.getRegisterCount()-1,"wrong parser/account registers "+method);
            }
        }
        check(returns==("searchState".equals(hook)?1:2),"unexpected return count "+method);
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
    public static void main(String[] args)throws Exception{
        MultiDexContainer<? extends DexFile> container=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());
        Set<String> verified=new HashSet<>();boolean runtime=false;
        for(String name:container.getDexEntryNames())for(ClassDef type:container.getEntry(name).getDexFile().getClasses()){
            if(HELPER.equals(type.getType()))runtime=true;
            for(Method method:type.getMethods()){
                String key=type.getType()+"->"+method.getName();String hook=TARGETS.get(key);
                if(hook!=null){check(verified.add(key),"ambiguous hook method "+key);verify(method,hook);}
            }
        }
        check(runtime,"native Discover implementation absent");
        Set<String> missing=new HashSet<>(TARGETS.keySet());missing.removeAll(verified);
        check(missing.isEmpty(),"missing native Discover hooks: "+missing);
        System.out.println("Native Discover DEX: all 9 Explore/search/SERP hooks preserve branch, switch, catch and account bindings");
    }
}
