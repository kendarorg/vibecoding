package com.kendar.sync.databinding;

import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.BR;
import com.kendar.sync.R;
import com.kendar.sync.viewmodel.DirectoryExplorerViewModel;
import com.google.android.material.appbar.MaterialToolbar;

public abstract class ActivityDirectoryExplorerBinding extends ViewDataBinding {
    @NonNull
    public final MaterialToolbar toolbar;
    @NonNull
    public final Button buttonConfirm;
    @NonNull
    public final RecyclerView recyclerViewDirectories;
    @NonNull
    public final TextView textViewEmptyState;
    @NonNull
    public final ProgressBar progressBar;

    protected long mDirtyFlags = -1;

    protected ActivityDirectoryExplorerBinding(Object _bindingComponent, View _root, int _localFieldCount,
                                              MaterialToolbar toolbar, Button buttonConfirm,
                                              RecyclerView recyclerViewDirectories,
                                              TextView textViewEmptyState, ProgressBar progressBar) {
        super(_bindingComponent, _root, _localFieldCount);
        this.toolbar = toolbar;
        this.buttonConfirm = buttonConfirm;
        this.recyclerViewDirectories = recyclerViewDirectories;
        this.textViewEmptyState = textViewEmptyState;
        this.progressBar = progressBar;
    }

    public abstract void setViewModel(@Nullable DirectoryExplorerViewModel viewModel);

    @Nullable
    public abstract DirectoryExplorerViewModel getViewModel();

    @NonNull
    public static ActivityDirectoryExplorerBinding inflate(@NonNull LayoutInflater inflater,
                                                          @Nullable ViewGroup parent,
                                                          boolean attachToParent) {
        return inflate(inflater, parent, attachToParent, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityDirectoryExplorerBinding inflate(@NonNull LayoutInflater inflater,
                                                          @Nullable ViewGroup parent,
                                                          boolean attachToParent,
                                                          @Nullable Object component) {
        return DataBindingUtil.<ActivityDirectoryExplorerBinding>inflate(
                inflater, R.layout.activity_directory_explorer, parent, attachToParent, component);
    }

    @NonNull
    public static ActivityDirectoryExplorerBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityDirectoryExplorerBinding inflate(@NonNull LayoutInflater inflater,
                                                          @Nullable Object component) {
        return DataBindingUtil.<ActivityDirectoryExplorerBinding>inflate(
                inflater, R.layout.activity_directory_explorer, null, false, component);
    }

    public static ActivityDirectoryExplorerBinding bind(@NonNull View view) {
        return bind(view, DataBindingUtil.getDefaultComponent());
    }

    public static ActivityDirectoryExplorerBinding bind(@NonNull View view,
                                                       @Nullable Object component) {
        return DataBindingUtil.<ActivityDirectoryExplorerBinding>bind(
                component, view, R.layout.activity_directory_explorer);
    }
}