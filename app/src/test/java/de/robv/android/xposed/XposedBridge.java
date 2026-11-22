package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Test-only hook registration and explicit callback dispatch. */
public final class XposedBridge {
    public static final LinkedHashMap<Member,List<XC_MethodHook>> hooks=new LinkedHashMap<>();
    public static String failAt="";
    public static XC_MethodHook.Unhook hookMethod(Member member,XC_MethodHook callback) {
        if(member.getName().equals(failAt)) throw new UnsupportedOperationException("test hook rejection");
        hooks.computeIfAbsent(member,m->new ArrayList<>()).add(callback); return callback.new Unhook(member);
    }
    static void remove(Member member,XC_MethodHook callback) { List<XC_MethodHook> list=hooks.get(member); if(list!=null) { list.remove(callback); if(list.isEmpty()) hooks.remove(member); } }
    public static void log(String message) { }
    public interface Original { Object call(Object[] args) throws Throwable; }
    public static Object dispatch(Member member,Object receiver,Object[] args,Original original) {
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam(); p.method=member; p.thisObject=receiver; p.args=args;
        List<XC_MethodHook> callbacks=new ArrayList<>(hooks.getOrDefault(member,List.of()));
        try {
            for(XC_MethodHook callback:callbacks) callback.beforeHookedMethod(p);
            if(!p.skip) p.result=original.call(p.args);
            for(int n=callbacks.size()-1;n>=0;n--) callbacks.get(n).afterHookedMethod(p);
            return p.result;
        } catch(Throwable e) { throw new AssertionError(e); }
    }
}
