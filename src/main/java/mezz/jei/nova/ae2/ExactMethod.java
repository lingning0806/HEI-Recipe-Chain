package mezz.jei.nova.ae2;

import java.lang.invoke.*;
import java.lang.reflect.InvocationTargetException;

/** Resolve a descriptor directly: Class.getMethod enumerates unrelated optional integration types. */
public final class ExactMethod {
    private ExactMethod() {}
    public static MethodHandle staticMethod(Class<?> owner,String name,Class<?> returns,Class<?>...parameters) throws ReflectiveOperationException {
        try {return MethodHandles.publicLookup().findStatic(owner,name,MethodType.methodType(returns,parameters));}
        catch(LinkageError error) {throw new ReflectiveOperationException("Could not link required method: "+owner.getName()+"."+name,error);}
    }
    public static Object invoke(MethodHandle method,Object...arguments) throws ReflectiveOperationException {
        try {return method.invokeWithArguments(arguments);}
        catch(VirtualMachineError | ThreadDeath fatal) {throw fatal;}
        catch(Throwable cause) {throw new InvocationTargetException(cause);}
    }
    private static volatile MethodHandle extraction;
    public static MethodHandle poweredExtraction() throws ReflectiveOperationException {
        MethodHandle result=extraction;
        if(result==null) {
            result=staticMethod(Class.forName("appeng.util.Platform"),"poweredExtraction",Class.forName("appeng.api.storage.data.IAEStack"),
                Class.forName("appeng.api.networking.energy.IEnergySource"),Class.forName("appeng.api.storage.IMEInventory"),
                Class.forName("appeng.api.storage.data.IAEStack"),Class.forName("appeng.api.networking.security.IActionSource"));
            extraction=result;
        }
        return result;
    }
}
