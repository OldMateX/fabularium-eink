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
package com.luxlunae.glk.view.window;

import android.content.Context;
import android.os.Build;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.luxlunae.glk.GLKUtils;
import com.luxlunae.glk.model.stream.window.GLKTextBufferM;
import com.luxlunae.glk.model.stream.window.GLKTextWindowM;
import com.luxlunae.glk.view.keyboard.GLKInputConnection;

public abstract class GLKTextWindowV extends GLKNonPairV {
    @NonNull
    private final GLKTextWindowM mModel;
    protected boolean mShowSystemKeyboard = false;
    @Nullable
    private int[] mCustomLineTerminators = null;        // custom line terminators (in addition to the default of [ENTER])

    /**
     * IME FIX: the connection most recently handed to the system keyboard. After a
     * restartInput() the keyboard may still send a few calls (e.g. finishComposingText) to
     * the previous connection; those must not overwrite the input line with stale text.
     */
    @Nullable
    private GLKInputConnection mActiveInputConnection = null;

    GLKTextWindowV(@NonNull Context c, @NonNull GLKTextWindowM model) {
        super(c, model);
        mModel = model;
    }

    public abstract int sendKey(int key);

    public abstract void clearInputBuffer();

    /**
     * Returns the current input buffer contents as a string (for IME sync).
     *
     * IME FIX: the old version cast mModel to GLKTextBufferM unconditionally, which would
     * throw a ClassCastException for a text grid window. Grid windows now get "" unless
     * GLKTextGridV overrides this.
     */
    @NonNull
    public String getInputBuffer() {
        if (mModel instanceof GLKTextBufferM) {
            return ((GLKTextBufferM) mModel).mInput.toString();
        }
        return "";
    }

    // ------------------------------------------------------------------
    // IME FIX: hooks used by GLKInputConnection (system keyboard support)
    // ------------------------------------------------------------------

    /**
     * IME FIX: true while a line-input request is active and the system keyboard may edit
     * the input line. GLKTextBufferV overrides this to use its own composing state.
     */
    public boolean isAcceptingLineInput() {
        return lineRequested();
    }

    /**
     * IME FIX: replace the in-progress input line with {@code text}. GLKInputConnection calls
     * this after every system-keyboard edit, so the line always mirrors the keyboard's text
     * (replace, never append). GLKTextBufferV overrides this with a direct buffer replace;
     * this default (used by text grids) clears the line and re-sends it key by key.
     */
    public void setInputBuffer(@NonNull CharSequence text) {
        clearInputBuffer();
        String s = text.toString();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            sendKey(cp);
            i += Character.charCount(cp);
        }
    }

    /**
     * IME FIX: make the system keyboard re-read the input line. Call this whenever the line
     * changes outside the keyboard's own edits (submit, double-tap word insertion, TADS links,
     * a new line request, ...). It creates a fresh GLKInputConnection seeded from the current
     * input line. Posted, because it is often called from inside an InputConnection callback.
     */
    public void restartSystemIme() {
        if (!mShowSystemKeyboard) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    // "If the input method is currently connected to the given view,
                    //  restart it with its new contents."
                    imm.restartInput(GLKTextWindowV.this);
                }
            }
        });
    }

    /**
     * IME FIX: true if {@code ic} is the connection the system keyboard is currently using.
     * Older connections (from before the last restartInput) are stale and must not edit the line.
     */
    public boolean isActiveInputConnection(@NonNull GLKInputConnection ic) {
        return ic == mActiveInputConnection;
    }

    public boolean isTerminator(int key) {
        return GLKUtils.isTerminator(key, mCustomLineTerminators);
    }

    public void setShowSystemKeyboardOnTouch(boolean on) {
        mShowSystemKeyboard = on;
    }

    /**
     * Check whether the called view is a text editor, in which case it would make sense
     * to automatically display a soft input window for it. Subclasses should override
     * this if they implement onCreateInputConnection(EditorInfo) to return true if a
     * call on that method would return a non-null InputConnection, and they are really
     * a first-class editor that the user would normally start typing on when they go into
     * a window containing your view.
     * <p>
     * The default implementation always returns false. This does not mean that its
     * onCreateInputConnection(EditorInfo) will not be called or the user can not otherwise
     * perform edits on your view; it is just a hint to the system that this is not the primary
     * purpose of this view.
     *
     * @return Returns true if this view is a text editor, else false.
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return mShowSystemKeyboard;
    }

    @CallSuper
    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent arg0) {
        // Show the system keyboard if appropriate
        requestFocus();
        if (!mShowSystemKeyboard) {
            return false;
        }
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
        return true;
    }

    @CallSuper
    @Override
    public void updateContents() {
        super.updateContents();
        mCustomLineTerminators = mModel.getTerminators();
    }

    /**
     * Create a new InputConnection for an InputMethod to interact with the view. The default
     * implementation returns null, since it doesn't support input methods. You can override
     * this to implement such support. This is only needed for views that take focus and text input.
     *
     * When implementing this, you probably also want to implement onCheckIsTextEditor() to indicate
     * you will return a non-null InputConnection.
     *
     * Also, take good care to fill in the EditorInfo object correctly and in its entirety, so that
     * the connected IME can rely on its values. For example, initialSelStart and initialSelEnd members
     * must be filled in with the correct cursor position for IMEs to work correctly with your application.
     *
     * @param outAttrs - Fill in with attribute information about the connection.
     *
     * @return the new InputConnection.
     */
    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        if (!mShowSystemKeyboard) {
            mActiveInputConnection = null;
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            // null or an empty array means that
            // commitContent(InputContentInfo, int, Bundle) is
            // not supported in this editor.
            outAttrs.contentMimeTypes = null;
        }
        outAttrs.initialCapsMode = 0;   // don't start editing in caps mode

        // The text offset of the start and end of the selection at the time editing begins; -1 if
        // not known. Keep in mind that, without knowing the cursor position, many IMEs
        // will not be able to offer their full feature set and may behave in
        // unpredictable ways: pass the actual cursor position here if possible at all.
        //
        // IME FIX: was always 0. The new GLKInputConnection is seeded with the current input
        // line (when a line is being composed), so the cursor starts at the end of that text.
        int len = isAcceptingLineInput() ? getInputBuffer().length() : 0;
        outAttrs.initialSelStart = len;
        outAttrs.initialSelEnd = len;

        // A label to show to the user describing the text they are writing.
        outAttrs.label = "Fabularium input";

        // The content type of the text box.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT;

        // Extended type information for the editor, to help the IME better integrate with it.
        outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;

        // IME FIX: never switch to the IME's fullscreen "extract" editor (e.g. in landscape).
        // It needs getExtractedText(), which we don't support, and would hide the game text.
        outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;

        // IME FIX: remember which connection is current, so stale ones can be ignored.
        mActiveInputConnection = new GLKInputConnection(this);
        return mActiveInputConnection;
    }
}
