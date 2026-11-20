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
package com.luxlunae.fabularium;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.LruCache;   
import android.widget.TextView;
import com.luxlunae.fabularium.play.IFictionDbHelper;  
import com.luxlunae.fabularium.R;    
import com.luxlunae.glk.GLKConstants;
import com.luxlunae.glk.GLKUtils;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * The AsyncLoaders class is a utility class that provides a collection of asynchronous loaders other classes can
 * use when they have expensive work that they want performed on a background thread (e.g. loading bitmaps, downloading
 * data from the internet, loading files).
 */
public class AsyncLoaders {
    private static final java.util.HashMap<String, android.graphics.drawable.BitmapDrawable> sCache = new java.util.HashMap<>();

    // Asynchronous loading of cover images from the file system
    // Adapted from sample code on developer.android.com
    // Temporarily sets text view to display the default icon at its top
    // until the asynchronous task has completed (and then that
    // icon is overwritten with the loaded icon)
	public static void loadBitmap(@NonNull String ifid, @NonNull String filePath, @NonNull TextView tv, @NonNull Context c, @NonNull Bitmap defaultIcon, int w, int h) {
		if (cancelPotentialWork(ifid, tv)) {
			// Check cache first — if hit, set directly (no flash on e-ink)
			String folderName = new File(filePath).getName().toLowerCase();
			try {
				String gamedataPath = GLKConstants.getDir(c, GLKConstants.SUBDIR.GAMEDATA) + "/" + folderName + "/";
				File einkCover = new File(gamedataPath + "EinkCover.jpg");
				File cover = new File(gamedataPath + "cover.png");
				File target = einkCover.exists() ? einkCover : cover;
				if (target.exists()) {
					BitmapDrawable cached = sCache.get(target.getAbsolutePath());
					if (cached != null) {
						tv.setCompoundDrawablesWithIntrinsicBounds(null, cached, null, null);
						return;
					}
				}
			} catch (IOException e) { /* fall through to async */ }

			// Cache miss — use async with default icon placeholder
			final BitmapWorkerTask task = new BitmapWorkerTask(c, tv, w, h);
			final AsyncDrawable asyncDrawable =
					new AsyncDrawable(c.getResources(), defaultIcon, task);
			tv.setCompoundDrawablesWithIntrinsicBounds(null, asyncDrawable, null, null);
			task.execute(ifid, filePath);
		}
	}   

    public static void loadBitmap(@NonNull TextView tv, @NonNull Context c, @NonNull Bitmap icon, int w, int h) {
        final BitmapWorkerTask task = new BitmapWorkerTask(c, tv, w, h);
        final AsyncDrawable asyncDrawable =
                new AsyncDrawable(c.getResources(), icon, task);
        tv.setCompoundDrawablesWithIntrinsicBounds(null, asyncDrawable, null, null);
    }

    /**
     * Checks if another running task is already associated with the TextView. If so,
     * it attempts to cancel the previous task by calling cancel(). In a small number of
     * cases, the new task ifid matches the existing task and nothing further needs to happen.
     *
     * @param ifid - the unique ID for this story
     * @param tv   - the text view to associate the image with.
     * @return true if no task associated with view, or an existing task has successfully been cancelled.
     */
    private static boolean cancelPotentialWork(@NonNull String ifid, @NonNull TextView tv) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(tv);

        if (bitmapWorkerTask != null) {
            final String bitmapIFID = bitmapWorkerTask.ifid;
            if (bitmapIFID == null || !bitmapIFID.equals(ifid)) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieve the task associated with a particular TextView.
     *
     * @param tv - text view to check.
     * @return the task, or NULL if no task found.
     */
    @Nullable
    private static BitmapWorkerTask getBitmapWorkerTask(@Nullable TextView tv) {
        if (tv != null) {
            final Drawable drawable = tv.getCompoundDrawables()[1];  // get the top drawable
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    private static class AsyncDrawable extends BitmapDrawable {
        @NonNull
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        AsyncDrawable(@NonNull Resources res, @NonNull Bitmap bitmap,
                      @NonNull BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<>(bitmapWorkerTask);
        }

        @Nullable
        BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    private static class BitmapWorkerTask extends AsyncTask<String, Void, BitmapDrawable> {
        @NonNull
        private final WeakReference<TextView> tvRef;
        @NonNull
        private final WeakReference<Context> mContext;
        private final int width;
        private final int height;
        private String ifid;
        private String filePath;

        BitmapWorkerTask(@NonNull Context c, @NonNull TextView tv, int w, int h) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mContext = new WeakReference<>(c);
            tvRef = new WeakReference<>(tv);
            width = w;
            height = h;
        }

// Decode image in background.
@Nullable
@Override
protected BitmapDrawable doInBackground(String... params) {
    filePath = params[0];
    ifid = params[1];
    Context c = mContext.get();
    if (c != null) {
        try {
            String folderName = new File(filePath).getName().toLowerCase();
            String gamedataPath = GLKConstants.getDir(c, GLKConstants.SUBDIR.GAMEDATA) + "/" + folderName + "/";
            File einkCover = new File(gamedataPath + "EinkCover.jpg");
            File cover = new File(gamedataPath + "cover.png");
            File target = einkCover.exists() ? einkCover : cover;
            if (target.exists()) {
                String cacheKey = target.getAbsolutePath();
                BitmapDrawable cached = sCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }
                BitmapDrawable drawable = new BitmapDrawable(c.getResources(), GLKUtils.getBitmapFromFile(target.getAbsolutePath(), width, height));
                sCache.put(cacheKey, drawable);
                return drawable;
            }   
        } catch (IOException | SecurityException e) {
            FabLogger.error("AsyncLoader: Cannot access game data directory.");
            return null;
        }
    }
    return null;
}      

        // Once complete, see if TextView is still around and set bitmap.
        @Override
        protected void onPostExecute(@Nullable BitmapDrawable bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            if (bitmap != null) {
                final TextView tv = tvRef.get();
                final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(tv);
                if (this == bitmapWorkerTask) {
					// Scale cover art to 120dp square to match the widened grid columns
                    // (original used intrinsic bounds, which rendered images too small)
                    int size = (int)(tv.getResources().getDisplayMetrics().density * 120);
                    bitmap.setBounds(0, 0, size, size);
                    tv.setCompoundDrawables(null, bitmap, null, null);
                }
            }
        }
    }
}   