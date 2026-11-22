package de.robv.android.xposed;

/** Callback recorder for tests. No ART interception; never packaged. */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
    public static final class MethodHookParam {
        public Object thisObject; public Object[] args; public java.lang.reflect.Member method;
        Object result; Throwable throwable; boolean skip;
        final java.util.Map<String,Object> extras=new java.util.HashMap<>();
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result=result; skip=true; }
        public boolean hasThrowable() { return throwable!=null; }
        public void setObjectExtra(String key,Object value) { extras.put(key,value); }
        public Object getObjectExtra(String key) { return extras.get(key); }
    }
    public final class Unhook {
        final java.lang.reflect.Member member;
        Unhook(java.lang.reflect.Member member) { this.member=member; }
        public void unhook() { XposedBridge.remove(member,XC_MethodHook.this); }
    }
}
