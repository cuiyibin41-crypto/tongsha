package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable t) { throwable = t; }
        public boolean hasThrowable() { return throwable != null; }
        public Object getResultOrThrowable() throws Throwable { if (throwable != null) throw throwable; return result; }
    }

    public class Unhook {
        public void unhook() {}
        public Member getHookedMethod() { return null; }
        public XC_MethodHook getCallback() { return XC_MethodHook.this; }
    }
}