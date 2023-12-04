package com.igalia.wolvic.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.util.LruCache;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.VRBrowserApplication;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class BitmapCache {
    private Context mContext;
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskCache;
    private Executor mIOExecutor;
    private Executor mMainThreadExecutor;
    private final Object mLock = new Object();
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 100; // 100MB
    private static final String LOGTAG = SystemUtils.createLogtag(BitmapCache.class);
    private SurfaceTexture mCaptureSurfaceTexture;
    private Surface mCaptureSurface;
    private boolean mCapturedAcquired;

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String hashKey(@NonNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            Log.e(LOGTAG, Objects.requireNonNull(e.getMessage()));
            return input;
        }
    }

    public static BitmapCache getInstance(Context aContext) {
        return ((VRBrowserApplication)aContext.getApplicationContext()).getBitmapCache();
    }

    public BitmapCache(@NonNull Context aContext, @NonNull Executor aIOExecutor, @NonNull Executor aMainThreadExecutor) {
        mContext = aContext;
        mIOExecutor = aIOExecutor;
        mMainThreadExecutor = aMainThreadExecutor;
    }

    public void onCreate() {
        initMemoryCache();
        initDiskCache();
    }

    void initMemoryCache() {
        // Get  available VM memory in KB.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // Use KB as the size of the item
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    void initDiskCache() {
        String path = mContext.getCacheDir() + File.separator + "snapshots";
        mIOExecutor.execute(() -> {
            try {
                mDiskCache = DiskLruCache.open(new File(path), 1, 1, DISK_CACHE_SIZE);
            }
            catch (Exception ex) {
                Log.e(LOGTAG, "Failed to initialize DiskLruCache:" + ex.getMessage());
            }
        });
    }

    public void addBitmap(@NonNull String aKey, @NonNull Bitmap aBitmap) {
        String finalKey = hashKey(aKey);
        mMemoryCache.put(finalKey, aBitmap);
        runIO(() -> {
            DiskLruCache.Editor editor = null;
            try {
                editor = mDiskCache.edit(finalKey);
                if (editor != null) {
                    aBitmap.compress(Bitmap.CompressFormat.PNG, 80, editor.newOutputStream(0));
                    editor.commit();
                }
            }
            catch (Exception ex) {
                Log.e(LOGTAG, "Failed to add Bitmap to DiskLruCache:" + ex.getMessage());
                if (editor != null) {
                    try {
                        editor.abort();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public @NonNull CompletableFuture<Bitmap> getBitmap(@NonNull String aKey) {
        String finalKey = hashKey(aKey);
        Bitmap cached = mMemoryCache.get(finalKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        } else {
            CompletableFuture<Bitmap> result = new CompletableFuture<>();
            runIO(() -> {
                try (DiskLruCache.Snapshot snapshot = mDiskCache.get(finalKey)){
                    if (snapshot != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(snapshot.getInputStream(0));
                        if (bitmap != null) {
                            mMainThreadExecutor.execute(() -> {
                                if (mMemoryCache.get(finalKey) == null) {
                                    // Do not update cache if it already contains a value
                                    // A tab could have saved a new image while we were loading the cached disk image.
                                    mMemoryCache.put(finalKey, bitmap);
                                }
                                result.complete(bitmap);
                            });

                            return;
                        }
                    }
                }
                catch (Exception ex) {
                    Log.e(LOGTAG, "Failed to get Bitmap from DiskLruCache:" + ex.getMessage());
                }

                mMainThreadExecutor.execute(() -> result.complete(null));

            });
            return result;
        }
    }

    public void removeBitmap(@NonNull String aKey) {
        String finalKey = hashKey(aKey);
        mMemoryCache.remove(finalKey);
        runIO(() -> {
            try {
                mDiskCache.remove(finalKey);
            } catch (Exception ex) {
                Log.e(LOGTAG, "Failed to remove Bitmap from DiskLruCache:" + ex.getMessage());
            }
        });
    }

    public boolean hasBitmap(@NonNull String aKey) {
        return mMemoryCache.get(hashKey(aKey)) != null;
    }

    private void runIO(Runnable aRunnable) {
        mIOExecutor.execute(() -> {
            if (mDiskCache != null) {
                synchronized (mLock) {
                    aRunnable.run();
                }
            }
        });
    }

    public CompletableFuture<Bitmap> scaleBitmap(Bitmap aBitmap, int aMaxWidth, int aMaxHeight) {
        int w = aBitmap.getWidth();
        int h = aBitmap.getHeight();
        if (w <= aMaxWidth && h <= aMaxHeight) {
            return CompletableFuture.completedFuture(aBitmap);
        }

        float aspect = (float)w / (float)h;

        if (w / aMaxWidth > h / aMaxHeight) {
            w = aMaxWidth;
            h = (int) (w / aspect);
        } else {
            h = aMaxHeight;
            w = (int)(h * aspect);
        }

        final int scaledW = w;
        final int scaleH = h;
        CompletableFuture<Bitmap> result = new CompletableFuture<>();

        runIO(() -> {
            Bitmap scaled = Bitmap.createScaledBitmap(aBitmap, scaledW, scaleH, true);
            if (scaled != null && scaled != aBitmap) {
                aBitmap.recycle();
                mMainThreadExecutor.execute(() -> result.complete(scaled));
            } else {
                mMainThreadExecutor.execute(() -> result.complete(aBitmap));
            }
        });

        return result;
    }

    public void setCaptureSurface(SurfaceTexture aSurfaceTexture) {
        mCaptureSurfaceTexture = aSurfaceTexture;
        mCaptureSurface = new Surface(aSurfaceTexture);
    }

    public @Nullable Surface acquireCaptureSurface(int width, int height) {
        if (mCapturedAcquired) {
            return null;
        }
        mCapturedAcquired = true;
        mCaptureSurfaceTexture.setDefaultBufferSize(width, height);
        return mCaptureSurface;
    }

    public void releaseCaptureSurface() {
        mCapturedAcquired = false;
    }

    public void onDestroy() {
        if (mDiskCache != null) {
            runIO(() -> {
                try {
                    mDiskCache.close();
                } catch (IOException ex) {
                    Log.e(LOGTAG, "Failed to close DiskLruCache:" + ex.getMessage());
                }
                mDiskCache = null;
            });
        }
        if (mCaptureSurface != null) {
            mCaptureSurface.release();
            mCaptureSurface = null;
        }
        if (mCaptureSurfaceTexture != null) {
            mCaptureSurfaceTexture.release();
            mCaptureSurfaceTexture = null;
        }
    }
}
