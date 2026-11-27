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
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fabularium; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */
package com.luxlunae.glk.view.window;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Point;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import com.luxlunae.glk.GLKActivity;
import com.luxlunae.glk.GLKConstants;
import com.luxlunae.glk.GLKLogger;
import com.luxlunae.glk.GLKUtils;
import com.luxlunae.glk.controller.GLKController;
import com.luxlunae.glk.controller.GLKEvent;
import com.luxlunae.glk.model.stream.window.GLKPairM;
import com.luxlunae.glk.model.stream.window.GLKTextBufferM;
import com.luxlunae.glk.model.stream.window.GLKTextWindowM;
import com.luxlunae.glk.model.stream.window.GLKWindowM;
import com.luxlunae.glk.model.style.GLKHyperlinkSpan;
import com.luxlunae.glk.model.style.GLKStyleSpan;
import com.luxlunae.glk.model.html.TableSpan;

import java.util.ArrayList;

import static com.luxlunae.glk.model.stream.window.GLKWindowM.ModifierFlag.FLAG_BG_COLOR_CHANGED;
import static com.luxlunae.glk.model.stream.window.GLKWindowM.ModifierFlag.FLAG_CLEARED;

/*
 * IME FIX summary (search this file for "IME FIX" to see every change):
 *  - isAcceptingLineInput() / setInputBuffer(): hooks GLKInputConnection uses to mirror the
 *    system keyboard's text into mModel.mInput (replace, never append).
 *  - updateInputState(): restarts the system keyboard when a line request starts or ends, so
 *    it re-reads the (new, usually empty) input line instead of keeping stale text.
 *  - onDoubleTap(), insertWordAt(), processTADSLink(): restart the system keyboard after
 *    changing mModel.mInput directly, so the keyboard sees the inserted word.
 *  - TAP FIX: single-tap and double-tap share appendTappedWord(), which also adds a space
 *    AFTER the word ("go" + "north" -> "go north ").
 *  - resetInputConnection(): now delegates to GLKTextWindowV.restartSystemIme().
 *  - setComposing(): no longer used (it let the old IME code force input open after a submit,
 *    which caused the repeated "go southgo south..." submissions). Kept, deprecated, as a no-op
 *    in case anything else still calls it.
 *
 * SWIPE: fast single-finger fling sends a compass direction + Enter.
 * TAP: single or double tap on a word inserts it into the input line (no Enter).
 * LONG PRESS: toggles the system keyboard (show if hidden, hide if shown).
 */
@SuppressLint("ViewConstructor")
public class GLKTextBufferV extends GLKTextWindowV implements HVScrollView.ScrollChild {

    private static final int ID_PASTE = android.R.id.paste;

    // ------------------------------------------------------------------
    // INPUT state
    // ------------------------------------------------------------------
    @NonNull
    private final Path mHighlightPath = new Path();
    @NonNull
    private final GLKTextBufferM mModel;
    @NonNull
    private final GLKScreen mScreen;
    private final boolean mSyncBGColor;
    @NonNull
    private ArrayList<Integer> mBufferedInput = new ArrayList<>();
    private boolean mComposing = false;

    // ------------------------------------------------------------------
    // OUTPUT state
    // ------------------------------------------------------------------
    @NonNull
    private SpannableStringBuilder mOutput = new SpannableStringBuilder();
    @Nullable
    private DynamicLayout mLayout;
    private int mVOffset;
    private int mCurLineEvent = -1;
    private boolean mAcceptingBufferedInput = true;
    @Nullable
    private String mLastFlush;

    // ------------------------------------------------------------------
    // SWIPE: fast fling sends a compass direction + Enter
    // ------------------------------------------------------------------

    /** Minimum swipe distance (dp) so slow drags aren't mistaken for commands. */
    private static final float SWIPE_MIN_DISTANCE_DP = 80f;
    /** Minimum swipe velocity (dp/s) so slow drags aren't mistaken for commands. */
    private static final float SWIPE_MIN_VELOCITY_DP = 300f;

    /**
     * Compass commands by 45-degree sector, anticlockwise from "right":
     * E, NE, N, NW, W, SW, S, SE.
     */
    private static final String[] SWIPE_COMMANDS = {"e", "ne", "n", "nw", "w", "sw", "s", "se"};

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public GLKTextBufferV(@NonNull Context c, @NonNull GLKTextBufferM model, @NonNull GLKScreen screen,
                          @Nullable int[] vScrollbarColors, @Nullable int[] hScrollbarColors,
                          int vScrollbarWidth, int hScrollbarHeight, boolean syncBGColor) {
        super(c, model);
        mModel = model;
        mScreen = screen;
        mSyncBGColor = syncBGColor;
        setScrollX(0);
        setScrollY(0);
        setScrollChild(this);  // enable scrolling

        // Set the scrollbar colors based on input style, unless
        // overridden by user or config file
        Resources res = screen.getContext().getResources();
        GLKStyleSpan sty = model.mWindowStyles[GLKConstants.style_Input];
        int[] colors = new int[2];
        colors[0] = colors[1] = sty.mTextColor;
        setScrollbarColors(res, true, (vScrollbarColors != null) ? vScrollbarColors : colors);
        setScrollbarColors(res, false, (hScrollbarColors != null) ? hScrollbarColors : colors);
        setScrollbarSize(res, true, vScrollbarWidth);
        setScrollbarSize(res, false, hScrollbarHeight);
        setContentDescription("");
    }

    // ------------------------------------------------------------------
    // Hyperlink lookup (recursive, handles TableSpan nesting)
    // ------------------------------------------------------------------

    /**
     * Look for a hyperlink in layout "layout" at pixel coordinates (x, y).
     * This may require traversing tablespans which in themselves contain
     * layouts (one for each cell), which may contain further tablespans, etc.
     */
    private static GLKHyperlinkSpan findHyperlink(@NonNull Layout layout, int x, int y) {
        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);
        Spanned text = (Spanned) layout.getText();

        GLKHyperlinkSpan[] links = text.getSpans(off, off, GLKHyperlinkSpan.class);
        if (links.length > 0) {
            return links[0];
        } else {
            TableSpan[] tables = text.getSpans(off, off, TableSpan.class);
            if (tables.length > 0) {
                Point cellPos = new Point();
                StaticLayout l = tables[0].getCellForScreenCoords(x, y, cellPos);
                if (l != null) {
                    return findHyperlink(l, x - cellPos.x, y - cellPos.y);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // sendKey: routes a single key code into the game's input buffer
    // ------------------------------------------------------------------

    @Override
    public int sendKey(int key) {
        Log.d("Fab", "sendKey(" + key + ") mComposing=" + mComposing);
        if (!mComposing) {
            return 0;
        }

        boolean lineDone = false;
        int len = mModel.mInput.length();
        if (key == GLKConstants.keycode_Delete) {
            // BACKSPACE: delete the character to the left of the cursor
            if (len > 0) {
                mModel.mInput.setLength(--len);
            }
        } else if (super.isTerminator(key)) {
            // ENTER (or custom terminator): flush the input buffer to the game
            if (mModel.mInputBB != null) {
                String input = mModel.mInput.toString();
                announceForAccessibility(input);
                mModel.echoString(input);
                len = mModel.mCharsetMgr.putGLKString(input, mModel.mInputBB, mModel.mIs32Bit, false);
            } else {
                len = 0;
            }
            lineDone = true;
        } else {
            // Normal character: append to the input buffer
            if (!mModel.mIs32Bit) {
                mModel.mInput.append(key > 255 ? (char) 0x3F : (char) key);
            } else {
                if (Character.isValidCodePoint(key)) {
                    mModel.mInput.appendCodePoint(key);
                } else {
                    GLKLogger.warn("GLKTextBuffer: received invalid codepoint (" + key + "), dropping.");
                }
            }
        }

        drawInput();
        scrollToEnd();
        if (lineDone) {
            stopComposing(true);
        }
        invalidate();
        return len;
    }

    // ------------------------------------------------------------------
    // updateContents: flushes new game output into the display
    // ------------------------------------------------------------------

    @Override
    public void updateContents() {
        if (mSyncBGColor && mModel.isFlagSet(FLAG_BG_COLOR_CHANGED)) {
            GLKPairM pm = mModel.getParent();
            boolean mainTextBuffer = false;

            if (pm != null) {
                if (pm.getOrientation() == LinearLayout.VERTICAL) {
                    mainTextBuffer = (pm.getChild2().getStreamId() == getStreamId());
                    mainTextBuffer = mainTextBuffer && pm.getParent() == null;
                }
            } else {
                mainTextBuffer = true;
            }

            if (mainTextBuffer) {
                int bgColor = mModel.getBGColor();
                mScreen.setBackgroundColor(bgColor);

                GLKWindowM sibM = mModel.getSibling();
                if (sibM != null) {
                    GLKWindowV sibV = GLKScreen.getViewForModel(sibM);
                    if (sibV != null && sibV instanceof GLKTextGridV) {
                        ((GLKTextGridV) sibV).setBackgroundColor(bgColor);
                    }
                }
            }
        }

        super.updateContents();

        // Resize the layout if necessary
        if (mLayout == null || mLayout.getWidth() != mModel.mWidthGLK_PX) {
            mLayout = new DynamicLayout(mOutput, mModel.mBaseTextPaint, mModel.mWidthGLK_PX,
                    Alignment.ALIGN_NORMAL, mModel.mLeadingMult, 0.0f, false);
        }

        SpannableStringBuilder mOutputBuffer = mModel.getBuffer();

        // Process clear operation
        if (mModel.isFlagSet(FLAG_CLEARED)) {
            mOutput = new SpannableStringBuilder();
            mLayout = new DynamicLayout(mOutput, mModel.mBaseTextPaint, mModel.mWidthGLK_PX,
                    Alignment.ALIGN_NORMAL, mModel.mLeadingMult, 0.0f, false);
            setScrollX(0);
            setScrollY(0);
            mModel.clearFlag(FLAG_CLEARED);
        }

        // If there's nothing to flush, then return
        if (mOutputBuffer.length() == 0) {
            return;
        }

        // If the text being flushed is interrupting a composing session,
        // temporarily suspend that composition
        boolean composing = mComposing;
        if (composing) {
            stopComposing(false);
        }

        // Finish off the last style block
        mModel.endCurrentStyle();

        // Flush the output buffer and scroll as appropriate
        int y1 = mLayout.getLineTop(Math.max(mLayout.getLineCount() - 2, 0));
        mOutput.append(mOutputBuffer);
        int y2 = mLayout.getLineBottom(mLayout.getLineCount() - 1);

        // For GLK text layout gravity is BOTTOM, but when in TADS HTML mode it
        // should be TOP...
        if (mModel.hasShownHTML()) {
            mVOffset = 0;
        } else {
            mVOffset = Math.max(0, mModel.getHeight() - getPaddingTop() - getPaddingBottom() - mLayout.getHeight());
        }

        if (y2 - getScrollY() > mHeightLessPadding) {
            setScrollY(Math.min(y1, y2 - mHeightLessPadding));
        }

        if (mModel.lineRequested() || mModel.charRequested()) {
            mLastFlush = mOutputBuffer.toString().trim();
            mLastFlush = mLastFlush.replaceAll(">\\Z", "What now?");
            if (mLastFlush != null) {
                Context c = getContext();
                if (c instanceof GLKActivity) {
                    ((GLKActivity) c).speakText(mLastFlush);
                }
                announceForAccessibility(mLastFlush);
            }
        }

        // Clear the pending text buffer and get ready for next output chunk
        mOutputBuffer.clear();
        mModel.startNewStyle();

        // If the text just flushed interrupted a composing session,
        // restart composition from the end of the appended text
        if (composing) {
            startComposing();
            updateCursor();
        }

        invalidate();
    }

    // ------------------------------------------------------------------
    // updateInputState: reacts to line-request start/end from the game
    // ------------------------------------------------------------------

    @Override
    public void updateInputState() {
        if (mModel.lineRequested()) {
            if (mModel.mLineEventCount != mCurLineEvent) {
                mCurLineEvent = mModel.mLineEventCount;
                if (mComposing) {
                    stopComposing(false);
                }
                startComposing();
                drawInput();
                invalidate();

                // IME FIX: a new line request has started. Make the system keyboard re-read
                // the input line, so it starts from what the game has (usually empty) rather
                // than whatever it held when the previous line was submitted.
                restartSystemIme();
            }
            processBufferedInputIfAny();
        } else {
            if (mComposing) {
                stopComposing(false);
                invalidate();

                // IME FIX: line input has ended; drop the keyboard's copy of the old line.
                restartSystemIme();
            }
            mCurLineEvent = -1;
        }
    }

    // ------------------------------------------------------------------
    // IME FIX: input buffer accessors used by GLKInputConnection
    // ------------------------------------------------------------------

    /**
     * Get the current contents of the input buffer.
     */
    @NonNull
    @Override
    public String getInputBuffer() {
        return mModel.mInput.toString();
    }

    /**
     * Clear the underlying input buffer and update the composing input text
     * currently displayed on-screen.
     */
    @Override
    public void clearInputBuffer() {
        mModel.mInput.setLength(0);
        drawInput();
        invalidate();
    }

    /**
     * IME FIX: the system keyboard may only edit the line while we are composing it.
     */
    @Override
    public boolean isAcceptingLineInput() {
        return mComposing;
    }

    /**
     * IME FIX: replace the whole input line with the system keyboard's text. Called by
     * GLKInputConnection after every keyboard edit. Replacing (rather than appending via
     * sendKey, as the old syncInputBuffer did) is what keeps the line from accumulating
     * "ggogo go s...". Applies the same 8-bit / 32-bit rules as sendKey().
     */
    @Override
    public void setInputBuffer(@NonNull CharSequence text) {
        if (!mComposing) {
            return;
        }
        mModel.mInput.setLength(0);
        String s = text.toString();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (!mModel.mIs32Bit) {
                mModel.mInput.append(cp > 255 ? (char) 0x3F : (char) cp);
            } else if (Character.isValidCodePoint(cp)) {
                mModel.mInput.appendCodePoint(cp);
            }
        }
        drawInput();
        scrollToEnd();
        invalidate();
    }

    // ------------------------------------------------------------------
    // Composing state management
    // ------------------------------------------------------------------

    /**
     * Clear any composing input text currently displayed on-screen.
     */
    private void clearInput() {
        int len = mOutput.length();
        int pos = mOutput.getSpanStart(InputMarker.class);
        if (pos < len && pos >= 0) {
            mOutput.delete(pos, len);
        }
    }

    /**
     * Update the composing input text currently displayed on-screen, to reflect the
     * current contents of the input buffer. The cursor position is also updated to
     * appear at the end of the text.
     */
    private void drawInput() {
        clearInput();

        if (mComposing) {
            int ilen = mModel.mInput.length();
            if (ilen > 0) {
                int len = mOutput.length();
                mOutput.append(mModel.mInput);
                mOutput.setSpan(CharacterStyle.wrap(mModel.mWindowStyles[GLKConstants.style_Input]),
                        len, len + ilen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            updateCursor();
        }
    }

    private void updateCursor() {
        mHighlightPath.reset();
        if (mLayout != null) {
            mLayout.getCursorPath(mOutput.length(), mHighlightPath, mOutput);
        }
    }

    /**
     * IME FIX: no longer used. The old GLKInputConnection called setComposing(true) before
     * every sync, which re-opened input after the line had already been submitted and caused
     * duplicate submissions. Composing state is now owned solely by this class
     * (startComposing / stopComposing). Kept as a no-op so any leftover caller still compiles.
     *
     * @deprecated composing state is controlled by line requests only.
     */
    @Deprecated
    public void setComposing(boolean composing) {
        // intentionally does nothing
    }

    /**
     * Allow user to compose text on-screen, commencing at the end of the
     * currently displayed text.
     */
    private void startComposing() {
        mComposing = true;
        int len = mOutput.length();
        mOutput.setSpan(InputMarker.class, len, len, Spanned.SPAN_MARK_MARK);
    }

    /**
     * Prevent user from composing text on screen.
     *
     * @param userTerminated - whether the user terminated input or our GLK library did.
     */
    private void stopComposing(boolean userTerminated) {
        mComposing = false;
        if (mModel.showCompletedInput()) {
            int len = mOutput.length();
            int pos = mOutput.getSpanStart(InputMarker.class);
            if ((pos >= 0 && pos < len) || userTerminated) {
                mOutput.append('\n');
                mModel.echoString("\n");
            }
        } else {
            clearInput();
        }
        mOutput.removeSpan(InputMarker.class);
    }

    // ------------------------------------------------------------------
    // Scrolling
    // ------------------------------------------------------------------

    private void scrollToEnd() {
        if (mLayout != null) {
            int y2 = mLayout.getLineBottom(mLayout.getLineCount() - 1);
            if (y2 - getScrollY() > mHeightLessPadding) {
                scrollTo(0, Math.max(0, y2 - mHeightLessPadding));
            }
        }
    }

    // ------------------------------------------------------------------
    // Clipboard / buffered input
    // ------------------------------------------------------------------

    private boolean onTextContextMenuItem(int id) {
        switch (id) {
            case ID_PASTE:
                pasteInput();
                return true;
        }
        return false;
    }

    private void pasteInput() {
        if (!mComposing) {
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                CharSequence paste = clip.getItemAt(i).coerceToStyledText(getContext());
                if (paste != null) {
                    for (int j = 0, sz = paste.length(); j < sz; j++) {
                        mBufferedInput.add((int) paste.charAt(j));
                    }
                }
            }
        }
        processBufferedInputIfAny();
    }

    public void appendBufferedInput(@NonNull int[] codes, int offset, int length) {
        if (mAcceptingBufferedInput) {
            for (int i = offset, k = 0, sz = codes.length;
                 k < length && i < sz; i++, k++) {
                mBufferedInput.add(codes[i]);
            }
        }
    }

    private void processBufferedInputIfAny() {
        int sz = mBufferedInput.size();

        if (sz == 0) {
            return;
        }

        if (mBufferedInput.get(0) == '>') {
            mBufferedInput.remove(0);
            sz--;
        }

        int[] codes = GLKUtils.getPrimitiveIntArray(mBufferedInput);
        int[] keysSent = new int[1];

        mAcceptingBufferedInput = false;
        GLKActivity act = GLKUtils.getActivityForView(this);
        if (act != null) {
            act.sendKeyCodes(this, codes, keysSent);
        }
        mAcceptingBufferedInput = true;

        int n = keysSent[0];
        if (n > 0 && n < sz) {
            mBufferedInput.subList(0, n).clear();
        } else {
            mBufferedInput.clear();
        }

        resetInputConnection();
    }

    /**
     * IME FIX: delegates to GLKTextWindowV.restartSystemIme().
     */
    private void resetInputConnection() {
        restartSystemIme();
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout != null) {
            try {
                int line = mLayout.getLineForVertical(getScrollY());
                int start = mLayout.getLineStart(line);
                int end = mLayout.getLineEnd(mLayout.getLineForVertical(getScrollY() + mHeightLessPadding));
                if (end > start && end <= mLayout.getText().length()) {
                    canvas.save();
                    canvas.clipRect(getScrollX() + mPaddingLeft, getScrollY() + mPaddingTop,
                            getScrollX() + getWidth() - mPaddingRight, getScrollY() + getHeight() - mPaddingBottom);
                    canvas.translate(mPaddingLeft, mPaddingTop + mVOffset);
                    if (mComposing) {
                        mLayout.draw(canvas, mHighlightPath, mModel.mHighlightPaint, 0);
                    } else {
                        mLayout.draw(canvas);
                    }
                    canvas.restore();
                }
            } catch (@NonNull IndexOutOfBoundsException | IllegalArgumentException e1) {
                GLKLogger.error("Error: Couldn't render text layout: " + e1.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // TADS hyperlink handling
    // ------------------------------------------------------------------

    private void processTADSLink(@NonNull GLKHyperlinkSpan link) {
        if (!mComposing) {
            return;
        }

        String r = link.getUrl();
        if (!link.mAppend) {
            mModel.mInput.setLength(0);
        }

        mModel.mInput.append(r);

        if (!link.mNoEnter) {
            if (mModel.mInputBB != null) {
                mModel.mCharsetMgr.putGLKString(mModel.mInput.toString(), mModel.mInputBB, mModel.mIs32Bit, false);
            }
            drawInput();
            scrollToEnd();
            stopComposing(false);

            GLKEvent ev = new GLKEvent();
            ev.lineEvent(mModel.getStreamId(), mModel.mInput.length(), 0);
            GLKController.postEvent(ev);
        }

        // IME FIX: the input line changed outside the system keyboard - make it re-read it.
        restartSystemIme();
    }

    // ------------------------------------------------------------------
    // TAP: single-tap or double-tap on a word inserts it into the input
    // ------------------------------------------------------------------

    /**
     * Find the word under the touch point and insert it into the input line.
     * Used by both single-tap (onSingleTapConfirmed) and double-tap (onDoubleTap).
     */
    private boolean insertWordAt(@NonNull MotionEvent e) {
        if (!mComposing || mLayout == null) return false;

        float x = e.getX() + getScrollX() - mPaddingLeft;
        float y = e.getY() + getScrollY() - mPaddingTop - mVOffset;
        int line = mLayout.getLineForVertical((int) y);
        int offset = mLayout.getOffsetForHorizontal(line, (int) x);

        int i1, i2;
        CharSequence txt = mLayout.getText();
        for (i1 = offset - 1; i1 >= 0; i1--) {
            if (!Character.isLetterOrDigit(txt.charAt(i1))) { i1++; break; }
        }
        i1 = Math.max(i1, 0);
        for (i2 = offset + 1; i2 < txt.length(); i2++) {
            if (!Character.isLetterOrDigit(txt.charAt(i2))) break;
        }
        i2 = Math.min(i2, txt.length() - 1);

        int len = i2 - i1;
        if (len <= 0 || len > 200) return false;

        String word = txt.subSequence(i1, i2).toString().trim();
        appendTappedWord(word);
        return true;
    }

    /**
     * TAP FIX: add a tapped word to the input line.
     *
     * Adds a space before the word if the line doesn't already end in whitespace,
     * and ALWAYS a space after it, so tapping "go" then "north" gives "go north "
     * rather than "gonorth" (the trailing space is harmless to the parser).
     *
     * IME FIX: the word goes straight into mModel.mInput, so the system keyboard doesn't
     * know about it. restartSystemIme() creates a new connection seeded with the full line
     * (including the word), so further typing continues after it.
     */
    private void appendTappedWord(@NonNull String word) {
        if (word.isEmpty()) {
            return;
        }
        int lenBuf = mModel.mInput.length();
        if (lenBuf > 0 && !Character.isWhitespace(mModel.mInput.charAt(lenBuf - 1))) {
            mModel.mInput.append(' ');
        }
        mModel.mInput.append(word);
        mModel.mInput.append(' ');
        if (mModel.mInputBB != null) {
            mModel.mCharsetMgr.putGLKString(mModel.mInput.toString(), mModel.mInputBB, mModel.mIs32Bit, false);
        }
        drawInput();
        scrollToEnd();
        invalidate();
        restartSystemIme();
    }

    // ------------------------------------------------------------------
    // LONG PRESS: toggle the system keyboard
    // ------------------------------------------------------------------

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    // ------------------------------------------------------------------
    // SWIPE: fast fling sends a compass direction + Enter
    // ------------------------------------------------------------------

    @Override
    public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        if (e1 != null && mComposing) {
            float density = getResources().getDisplayMetrics().density;
            float dx = e2.getX() - e1.getX();
            float dy = e1.getY() - e2.getY();  // screen Y grows downwards; flip so "up" is positive
            double dist = Math.hypot(dx, dy);
            double speed = Math.hypot(velocityX, velocityY);

            if (dist >= SWIPE_MIN_DISTANCE_DP * density
                    && speed >= SWIPE_MIN_VELOCITY_DP * density) {
                double angle = Math.toDegrees(Math.atan2(dy, dx));  // 0=right, 90=up, ±180=left
                int sector = ((int) Math.round(angle / 45.0) + 8) % 8;
                submitSwipeCommand(SWIPE_COMMANDS[sector]);
                return true;
            }
        }
        return super.onFling(e1, e2, velocityX, velocityY);  // not a command: keep existing behaviour
    }

    /**
     * Clear the current input line, send the compass command + Enter via the normal
     * key router, then resync the system keyboard.
     */
    private void submitSwipeCommand(@NonNull String cmd) {
        GLKActivity act = GLKUtils.getActivityForView(this);
        if (act == null) {
            return;
        }
        clearInputBuffer();  // a swipe is a whole command; discard anything half-typed
        int[] codes = new int[cmd.length() + 1];
        for (int i = 0; i < cmd.length(); i++) {
            codes[i] = cmd.charAt(i);
        }
        codes[cmd.length()] = 10;  // Enter (GLKUtils maps 10 -> keycode_Return)
        act.sendKeyCodes(this, codes, null);
        restartSystemIme();
    }

    // ------------------------------------------------------------------
    // Tap event handlers (from GestureDetector.OnDoubleTapListener / OnGestureListener)
    // ------------------------------------------------------------------

    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent e) {
        // Always consume. The parent's onSingleTapUp toggles the keyboard,
        // which causes flicker on the first tap of a double-tap.
        // The keyboard is shown via long-press instead.
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent arg0) {
        // We need to use onSingleTapConfirmed rather than onSingleTapUp as
        // we support double-tap events (see onDoubleTap below).
        if (mLayout != null) {
            GLKHyperlinkSpan link = findHyperlink(mLayout,
                    (int) arg0.getX() + getScrollX() - mPaddingLeft,
                    (int) arg0.getY() + getScrollY() - mPaddingTop - mVOffset);
            if (link != null) {
                int v = link.getVal();
                if (v != GLKConstants.NULL) {
                    // Generic GLK hyperlink
                    if (mModel.hyperlinkRequested()) {
                        GLKEvent ev = new GLKEvent();
                        ev.hyperlinkEvent(mModel.getStreamId(), v);
                        GLKController.postEvent(ev);
                    }
                } else {
                    // TADS-style hyperlink
                    GLKTextWindowM win = link.mInputWin;
                    if (win != null && win instanceof GLKTextBufferM) {
                        GLKTextBufferV winV = (GLKTextBufferV) GLKScreen.getViewForModel(win);
                        if (winV != null) {
                            winV.processTADSLink(link);
                        }
                    } else {
                        GLKLogger.error("GLKTextBuffer: Could not process TADS hyperlink '" + link.getUrl()
                                + "' as the attached window was invalid.");
                    }
                }
                return true;
            } else {
                // No hyperlink: try to insert the tapped word into the input line
                if (insertWordAt(arg0)) {
                    return true;
                }
                announceForAccessibility((mLastFlush != null) ?
                        "Text buffer " + getStreamId() + ". " + mLastFlush :
                        "Text buffer 1 is empty.");
            }
        }
        return super.onSingleTapConfirmed(arg0);
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        // Double-tap: same as single-tap — insert the word under the finger
        return insertWordAt(e);
    }

    // ------------------------------------------------------------------
    // HVScrollView.ScrollChild interface
    // ------------------------------------------------------------------

    @Override
    public int getChildWidth() {
        return (mLayout != null) ? mLayout.getWidth() : 0;
    }

    @Override
    public int getChildHeight() {
        return (mLayout != null) ? mLayout.getHeight() : 0;
    }

    // ------------------------------------------------------------------
    // Inner marker class (used as a span to track the input cursor position)
    // ------------------------------------------------------------------

    private static class InputMarker {
    }
}   