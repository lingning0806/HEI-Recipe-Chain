package mezz.jei.nova;

import mezz.jei.nova.ae2.ExactMethod;
import java.io.*;
import java.lang.invoke.MethodHandle;

/** Reproduces the installed AE Platform's absent optional GregTech method signature. */
public final class OptionalLinkageChecks {
    public static class AbsentMod {}
    public static class Library {
        public static String available(String value) {return "ok:"+value;}
        public static AbsentMod unrelated(AbsentMod value) {return value;}
    }
    public static void main(String[] args) throws Exception {
        ClassLoader loader=new ClassLoader(OptionalLinkageChecks.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.equals(AbsentMod.class.getName()))throw new ClassNotFoundException("Optional mod deliberately absent");
                if(name.equals(Library.class.getName())) {
                    Class<?> loaded=findLoadedClass(name);
                    if(loaded==null)try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                        while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
                        byte[] code=bytes.toByteArray();loaded=defineClass(name,code,0,code.length);
                    } catch(IOException ex) {throw new ClassNotFoundException(name,ex);}
                    if(resolve)resolveClass(loaded);return loaded;
                }
                return super.loadClass(name,resolve);
            }
        };
        Class<?> library=Class.forName(Library.class.getName(),true,loader);
        boolean reproduced=false;
        try {library.getMethod("available",String.class);}catch(NoClassDefFoundError expected){reproduced=true;}
        if(!reproduced)throw new AssertionError("Missing optional signature did not reproduce reflection failure");
        MethodHandle exact=ExactMethod.staticMethod(library,"available",String.class,String.class);
        if(!"ok:AE".equals(ExactMethod.invoke(exact,"AE")))throw new AssertionError("Exact signature invocation failed");
        try {ExactMethod.staticMethod(library,"missing",String.class,String.class);throw new AssertionError();}catch(NoSuchMethodException expected){}
        System.out.println("PASS optional linkage: reflection failure reproduced, exact lookup ignores absent integration, missing required method rejected");
    }
}
