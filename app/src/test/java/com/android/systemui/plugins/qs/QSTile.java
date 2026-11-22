package com.android.systemui.plugins.qs;
public interface QSTile {
    boolean isAvailable();
    boolean isTileReady();
    void setListening(Object owner, boolean listening);
    void refreshState();
    void destroy();
    State getState();
    CharSequence getTileLabel();
    String getTileSpec();
    class State {
        public CharSequence label, secondaryLabel;
        public Object icon;
        public java.util.function.Supplier<?> iconSupplier;
        public State copy() { State s = new State(); s.label=label; s.secondaryLabel=secondaryLabel; s.icon=icon; s.iconSupplier=iconSupplier; return s; }
    }
}
