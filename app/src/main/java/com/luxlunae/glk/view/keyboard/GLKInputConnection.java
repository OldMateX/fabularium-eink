/*
 * Copyright (C) 2018 Tim Cadogan-Cowper.
 *
 * This file is part of Fabularium.
 *
 * Fabularium is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fabularium; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.luxlunae.glk.view.keyboard;

import android.content.Context;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import com.luxlunae.glk.GLKActivity;
import com.luxlunae.glk.GLKUtils;
import com.luxlunae.glk.view.window.GLKTextWindowV;

/**
 * Connects the user's system keyboard (Gboard, FUTO, ...) to a GLK text window, for when
 * the user chooses not to use the built-in keyboard.
 *
 * <h3>Why this file is so much shorter than the old one</h3>
 * The old GLKInputConnection "implements InputConnection" and contained a hand-copied
 * version of Android's BaseInputConnection (~30 methods: composing spans, surrogate-safe
 * deletes, getTextBeforeCursor, ...). This version EXTENDS BaseInputConnection instead,
 * so the framework supplies all of that, and this class only adds the GLK-specific parts.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li><b>One editable.</b> All IME edits go into {@link #mEditable} via BaseInputConnection.
 *       It is seeded from the window's current input line when the connection is created, so
 *       after every {@code restartInput()} the IME and the game agree on the text.</li>
 *   <li><b>Line input.</b> After every IME edit (end of the outermost batch edit) mEditable is
 *       MIRRORED into the window's input line - replaced, never appended. That is what stops
 *       the old "ggogo go sgo so..." accumulation. The IME is told the new cursor position
 *       (updateSelection), which the old code never did.</li>
 *   <li><b>Enter.</b> Mirrors once more, then sends Return through
 *       {@link GLKActivity#sendKeyCodes} - the same router the built-in keyboard uses - which
 *       writes the GLK buffer AND posts the line event. (Calling the window's sendKey() directly,
 *       as the old syncInputBuffer did, writes the buffer but never posts the event.)</li>
 *   <li><b>Duplicate Enter.</b> One press can arrive as performEditorAction + sendKeyEvent(ENTER);
 *       the second is ignored by a short debounce. (The old mSyncing flag only blocked nested
 *       calls, not these two back-to-back calls.)</li>
 *   <li><b>Char input</b> (e.g. "press any key", menus): characters, Enter and Backspace go
 *       straight to the router as key presses, which posts char events.</li>
 *   <li><b>Resync.</b> After a submit or a char key the IME is restarted so it drops its stale
 *       composing state. The window also restarts it when a new line request starts and after
 *       double-tap / single-tap word insertion and TADS links (see GLKTextBufferV).</li>
 *   <li><b>Stale connections.</b> After a restart the keyboard may still send a call or two to
 *       the old connection (typically finishComposingText). Those are ignored, otherwise the old
 *       connection's out-of-date text would overwrite the line (e.g. hiding a tapped word).</li>
 * </ul>
 */
public class GLKInputConnection extends BaseInputConnection {

    private static final String TAG = "Fab";

    /** Set to true and run "adb logcat -s Fab" to trace every IME call. */
    private static final boolean DEBUG_INPUT_CONNECTION = false;

    /** Raw key codes the router expects; GLKUtils.translateKeyToGLKCode maps 10 -> Return, 8 -> Delete. */
    private static final int RAW_RETURN = 10;
    private static final int RAW_BACKSPACE = 8;

    /** One physical Enter can arrive twice (editor action + key event) within a few ms. */
    private static final long ENTER_DEBOUNCE_MS = 400;

    /** Static on purpose: the duplicate Enter may land on a fresh connection after restartInput(). */
    private static long sLastEnterTime = 0;

    @NonNull
    private final GLKTextWindowV mView;

    /** The single text buffer the IME edits (line input only). */
    @NonNull
    private final Editable mEditable;

    /** Nesting depth of batch edits; the view is updated only when the outermost batch ends. */
    private int mBatchDepth = 0;

    /** Char-input mode: composing text already sent to the game, so only new characters are sent. */
    @NonNull
    private String mCharModeSent = "";

    public GLKInputConnection(@NonNull GLKTextWindowV view) {
        super(view, true); // fullEditor = true: never fall back to sending key events
        mView = view;

        // Seed from the game's current input line (empty unless a line is being composed),
        // so the IME sees exactly what the player sees.
        String seed = view.isAcceptingLineInput() ? view.getInputBuffer() : "";
        mEditable = Editable.Factory.getInstance().newEditable(seed);
        Selection.setSelection(mEditable, mEditable.length());
        debug("new connection, seeded with '" + seed + "'");
    }

    // ------------------------------------------------------------------
    // BaseInputConnection hooks
    // ------------------------------------------------------------------

    /** BaseInputConnection performs every edit on this. */
    @Override
    public Editable getEditable() {
        return mEditable;
    }

    @Override
    public boolean beginBatchEdit() {
        mBatchDepth++;
        return true;
    }

    /**
     * BaseInputConnection wraps every edit (setComposingText, commitText, deleteSurroundingText,
     * finishComposingText, ...) in begin/endBatchEdit, and IMEs nest their own batches around
     * those. So the end of the outermost batch is the one place to push the result to the game
     * window and tell the IME where the cursor is.
     */
    @Override
    public boolean endBatchEdit() {
        if (mBatchDepth > 0) {
            mBatchDepth--;
        }
        if (mBatchDepth == 0) {
            pushToView();
            notifyImeSelection();
        }
        return mBatchDepth > 0;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        debug("setComposingText '" + text + "' before='" + mEditable + "'");
        if (mView.charRequested()) {
            sendCharModeText(text, false);
            return true;
        }
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        debug("commitText '" + text + "' before='" + mEditable + "'");
        if (mView.charRequested()) {
            sendCharModeText(text, true);
            return true;
        }
        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean setSelection(int start, int end) {
        boolean ok = super.setSelection(start, end);
        if (mBatchDepth == 0) {
            notifyImeSelection(); // the IME expects onUpdateSelection after moving the cursor
        }
        return ok;
    }

    /**
     * Any editor action (DONE, GO, SEND, unspecified) means "Enter" here.
     * (BaseInputConnection's default would send an ENTER key event instead.)
     */
    @Override
    public boolean performEditorAction(int actionCode) {
        enter("performEditorAction(" + actionCode + ")");
        return true;
    }

    /**
     * Some IMEs send Enter / Backspace / characters as raw key events (especially when the
     * text field reports no text). Handle them here rather than letting BaseInputConnection
     * dispatch them to the view hierarchy.
     */
    @Override
    public boolean sendKeyEvent(@NonNull KeyEvent event) {
        final int code = event.getKeyCode();
        final boolean isEnter = code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        debug("sendKeyEvent code=" + code + " action=" + event.getAction());

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return isEnter || code == KeyEvent.KEYCODE_DEL; // swallow the matching key-ups
        }
        if (isEnter) {
            enter("sendKeyEvent(ENTER)");
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL) {
            if (mView.charRequested()) {
                route(RAW_BACKSPACE);
                mView.restartSystemIme();
            } else {
                deleteBeforeCursor();
            }
            return true;
        }
        int c = event.getUnicodeChar();
        if (c != 0) {
            return commitText(new String(Character.toChars(c)), 1);
        }
        return false; // same as the old behaviour for other keys
    }

    /**
     * Kept for compatibility with any caller of the old class: drops whatever the IME is
     * composing and makes it re-read the game's input line.
     */
    public void clearEditable() {
        mEditable.clear();
        mView.restartSystemIme();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Single entry point for Enter, whatever route it arrived by. */
    private void enter(String why) {
        long now = SystemClock.uptimeMillis();
        if (now - sLastEnterTime < ENTER_DEBOUNCE_MS) {
            debug("Enter ignored (duplicate) via " + why);
            return;
        }
        sLastEnterTime = now;

        if (mView.charRequested()) {
            debug("Enter via " + why + " -> char event");
            route(RAW_RETURN);
            mView.restartSystemIme();
            return;
        }
        if (!mView.isAcceptingLineInput()) {
            debug("Enter via " + why + " ignored: no line or char request");
            return;
        }

        if (!isStale()) {
            removeComposingSpans(mEditable); // finalise whatever the IME was composing
            pushToView();                     // window now holds exactly the IME text
            mEditable.clear();
        }
        // (A stale connection just submits the window's current line, which is up to date.)
        debug("submit via " + why + ": '" + mView.getInputBuffer() + "'");

        // Same path as the built-in keyboard: GLKActivity.sendKeyCodes() calls the window's
        // sendKey(Return), which copies the line into the GLK buffer, and then posts the
        // line event to the game.
        if (!route(RAW_RETURN)) {
            Log.w(TAG, "GLKInputConnection: router did not accept Return");
        }
        mView.restartSystemIme(); // IME forgets its stale composing state
    }

    /**
     * Char-input mode: send only characters not already sent during this composing session
     * (IMEs often re-send the whole composing word, e.g. "y" then "ye"), then restart the IME
     * so it does not carry composing state into a later line request.
     *
     * @param commit true for commitText (ends the composing session), false for setComposingText.
     */
    private void sendCharModeText(@NonNull CharSequence text, boolean commit) {
        String s = text.toString();
        String delta = s.startsWith(mCharModeSent) ? s.substring(mCharModeSent.length()) : s;
        mCharModeSent = commit ? "" : s;

        if (!delta.isEmpty()) {
            int[] codes = new int[delta.codePointCount(0, delta.length())];
            for (int i = 0, n = 0; i < delta.length(); n++) {
                int cp = delta.codePointAt(i);
                codes[n] = cp;
                i += Character.charCount(cp);
            }
            debug("char mode: sending '" + delta + "'");
            route(codes);
        }
        mEditable.clear();
        mView.restartSystemIme();
    }

    /** Send raw key codes through the same router the built-in keyboard uses. */
    private boolean route(int... codes) {
        GLKActivity act = GLKUtils.getActivityForView(mView);
        if (act != null) {
            return act.sendKeyCodes(mView, codes, null);
        }
        Log.w(TAG, "GLKInputConnection: no hosting GLKActivity, key dropped");
        return false;
    }

    /** Backspace delivered as a key event in line mode: edit mEditable like the IME would. */
    private void deleteBeforeCursor() {
        beginBatchEdit();
        int end = Selection.getSelectionEnd(mEditable);
        if (end < 0) end = mEditable.length();
        int start = Selection.getSelectionStart(mEditable);
        if (start >= 0 && start != end) {
            mEditable.delete(Math.min(start, end), Math.max(start, end));
        } else if (end > 0) {
            int prev = Character.offsetByCodePoints(mEditable, end, -1);
            mEditable.delete(prev, end);
        }
        endBatchEdit();
    }

    /**
     * True once a newer connection has replaced this one (after restartInput). The keyboard
     * may still send a few calls here, e.g. finishComposingText while switching over; this
     * connection's text is out of date by then, so it must not touch the input line.
     */
    private boolean isStale() {
        return !mView.isActiveInputConnection(this);
    }

    /** Mirror the IME's text into the game's input line (replace, never append). */
    private void pushToView() {
        if (isStale()) {
            debug("stale connection: not pushing '" + mEditable + "'");
            return;
        }
        if (!mView.isAcceptingLineInput()) {
            return; // no line request: nothing to show (window re-seeds us when one starts)
        }
        String text = mEditable.toString();
        if (!text.equals(mView.getInputBuffer())) {
            mView.setInputBuffer(text);
        }
    }

    /** Tell the IME where the cursor and composing region are (the old code never did). */
    private void notifyImeSelection() {
        if (isStale()) return; // the keyboard has moved on to a newer connection
        InputMethodManager imm = (InputMethodManager)
                mView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.updateSelection(mView,
                Selection.getSelectionStart(mEditable),
                Selection.getSelectionEnd(mEditable),
                getComposingSpanStart(mEditable),
                getComposingSpanEnd(mEditable));
    }

    private static void debug(String msg) {
        if (DEBUG_INPUT_CONNECTION) {
            Log.d(TAG, "GLKInputConnection: " + msg);
        }
    }
}
