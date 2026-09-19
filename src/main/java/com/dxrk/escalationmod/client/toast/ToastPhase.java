package com.dxrk.escalationmod.client.toast;

/**
 * GROWING  — маленький бокс растёт под текст
 * HOLDING  — полный размер, идёт печатающийся текст
 * SHRINKING — сжатие перед исчезновением (по таймеру или Skip)
 * DONE     — можно удалять из очереди
 */
public enum ToastPhase {
    GROWING,
    HOLDING,
    SHRINKING,
    DONE
}
