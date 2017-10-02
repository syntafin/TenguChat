package de.tengu.chat.ui;

public interface UiInformableCallback<T> extends UiCallback<T> {
    void inform(String text);
}
