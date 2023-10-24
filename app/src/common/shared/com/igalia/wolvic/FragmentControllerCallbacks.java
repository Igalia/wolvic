package com.igalia.wolvic;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.OnConfigurationChangedProvider;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentHostCallback;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class FragmentControllerCallbacks extends FragmentHostCallback implements OnConfigurationChangedProvider {
    private VRBrowserActivity mActivity;
    private List<Consumer<Configuration>> mListener;

    public FragmentControllerCallbacks(@NonNull Context context, @NonNull Handler handler, int windowAnimations) {
        super(context, handler, windowAnimations);
        mActivity = (VRBrowserActivity) context;
        mListener = new ArrayList<>();
    }

    @Override
    public void onDump(@NonNull String prefix, @Nullable FileDescriptor fd, @NonNull PrintWriter writer, @Nullable String[] args) {
        super.onDump(prefix, fd, writer, args);
    }

    @Override
    public boolean onShouldSaveFragmentState(@NonNull Fragment fragment) {
        return super.onShouldSaveFragmentState(fragment);
    }

    @NonNull
    @Override
    public LayoutInflater onGetLayoutInflater() {
        return mActivity.getLayoutInflater().cloneInContext(mActivity);
    }

    @Nullable
    @Override
    public Object onGetHost() {
        return mActivity;
    }

    @Override
    public void onSupportInvalidateOptionsMenu() {
        super.onSupportInvalidateOptionsMenu();
    }

    @Override
    public void onStartActivityFromFragment(@NonNull Fragment fragment, Intent intent, int requestCode) {
        super.onStartActivityFromFragment(fragment, intent, requestCode);
    }

    @Override
    public void onStartActivityFromFragment(@NonNull Fragment fragment, Intent intent, int requestCode, @Nullable Bundle options) {
        super.onStartActivityFromFragment(fragment, intent, requestCode, options);
    }

    @Override
    public boolean onShouldShowRequestPermissionRationale(@NonNull String permission) {
        return super.onShouldShowRequestPermissionRationale(permission);
    }

    @Override
    public boolean onHasWindowAnimations() {
        return super.onHasWindowAnimations();
    }

    @Override
    public int onGetWindowAnimations() {
        return super.onGetWindowAnimations();
    }

    @Nullable
    @Override
    public View onFindViewById(int id) {
        return mActivity.getWidgetContainer().findViewById(id);
    }

    @Override
    public boolean onHasView() {
        return super.onHasView();
    }

    @Override
    public void addOnConfigurationChangedListener(@NonNull Consumer<Configuration> listener) {
        mListener.add(listener);
    }

    @Override
    public void removeOnConfigurationChangedListener(@NonNull Consumer<Configuration> listener) {
        mListener.remove(listener);
    }
}
