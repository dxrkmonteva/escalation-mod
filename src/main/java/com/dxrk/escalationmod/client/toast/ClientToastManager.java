package com.dxrk.escalationmod.client.toast;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** Клиентский синглтон-менеджер очереди тостов (несколько легендарок одновременно — норма, раздел 6). */
public final class ClientToastManager {

    public static final ClientToastManager INSTANCE = new ClientToastManager();

    private static final int MAX_VISIBLE = 6;

    private final Deque<ActiveEscalationToast> toasts = new ArrayDeque<>();

    private ClientToastManager() {
    }

    public void addToast(ActiveEscalationToast toast) {
        toasts.addLast(toast);
    }

    public void tick() {
        Iterator<ActiveEscalationToast> it = toasts.iterator();
        while (it.hasNext()) {
            if (it.next().isFinished()) {
                it.remove();
            }
        }
    }

    public Iterable<ActiveEscalationToast> visibleToasts() {
        int skip = Math.max(0, toasts.size() - MAX_VISIBLE);
        Deque<ActiveEscalationToast> result = new ArrayDeque<>();
        int i = 0;
        for (ActiveEscalationToast t : toasts) {
            if (i++ >= skip) {
                result.addLast(t);
            }
        }
        return result;
    }

    public boolean skipOldestActive() {
        for (ActiveEscalationToast t : toasts) {
            if (t.getPhase() == ToastPhase.GROWING || t.getPhase() == ToastPhase.HOLDING) {
                t.requestSkip();
                return true;
            }
        }
        return false;
    }

    public boolean hasAny() {
        return !toasts.isEmpty();
    }
}
