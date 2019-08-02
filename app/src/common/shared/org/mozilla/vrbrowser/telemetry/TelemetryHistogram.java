package org.mozilla.vrbrowser.telemetry;

import static java.lang.Math.toIntExact;

public class TelemetryHistogram {

    private int[] mHistogram;
    private int mNumBins;
    private int mBinSize;
    private long mMin;

    public TelemetryHistogram(int numBins, int binSize, long min) {
        mNumBins = numBins;
        mBinSize = binSize;
        mMin = min;
        mHistogram = new int[mNumBins];
    }

    public void addData(long data) {
        if (data < mMin) {
            return;
        }

        int bin = toIntExact(data / mBinSize);
        if (bin > (mNumBins - 2)) {
            bin = mNumBins - 1;

        } else if (bin < 0) {
            bin = 0;
        }

        mHistogram[bin]++;
    }

    public int[] getHistogram() {
        return mHistogram;
    }

    public int getBinSize() {
        return mBinSize;
    }

}
