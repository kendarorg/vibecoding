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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.BR;
import com.kendar.sync.R;
import com.kendar.sync.viewmodel.RemoteTargetExplorerViewModel;
import com.google.android.material.appbar.MaterialToolbar;

public abstract class ActivityRemoteTargetExplorerBinding extends ViewDataBinding {
    @NonNull
    public final ConstraintLayout constraintLayout;
    @NonNull
    public final MaterialToolbar toolbar;
    @NonNull
    public final Button buttonConfirmTarget;
    @NonNull
    public final RecyclerView recyclerViewTargets;
    @NonNull
    public final TextView textViewEmptyState;
    @NonNull
    public final ProgressBar progressBar;
    @NonNull
    public final TextView textViewError;
    @NonNull
    public final Button buttonRetry;

    protected long mDirtyFlags = -1;

    protected ActivityRemoteTargetExplorerBinding(Object _bindingComponent, View _root, int _localFieldCount,
                                                ConstraintLayout constraintLayout, MaterialToolbar toolbar,
                                                Button buttonConfirmTarget, RecyclerView recyclerViewTargets,
                                                TextView textViewEmptyState, ProgressBar progressBar,
                                                TextView textViewError, Button buttonRetry) {
        super(_bindingComponent, _root, _localFieldCount);
        this.constraintLayout = constraintLayout;
        this.toolbar = toolbar;
        this.buttonConfirmTarget = buttonConfirmTarget;
        this.recyclerViewTargets = recyclerViewTargets;
        this.textViewEmptyState = textViewEmptyState;
        this.progressBar = progressBar;
        this.textViewError = textViewError;
        this.buttonRetry = buttonRetry;
    }

    public abstract void setViewModel(@Nullable RemoteTargetExplorerViewModel viewModel);

    @Nullable
    public abstract RemoteTargetExplorerViewModel getViewModel();

    @NonNull
    public static ActivityRemoteTargetExplorerBinding inflate(@NonNull LayoutInflater inflater,
                                                            @Nullable ViewGroup parent,
                                                            boolean attachToParent) {
        return inflate(inflater, parent, attachToParent, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityRemoteTargetExplorerBinding inflate(@NonNull LayoutInflater inflater,
                                                            @Nullable ViewGroup parent,
                                                            boolean attachToParent,
                                                            @Nullable Object component) {
        return DataBindingUtil.<ActivityRemoteTargetExplorerBinding>inflate(
                inflater, R.layout.activity_remote_target_explorer, parent, attachToParent, component);
    }

    @NonNull
    public static ActivityRemoteTargetExplorerBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityRemoteTargetExplorerBinding inflate(@NonNull LayoutInflater inflater,
                                                            @Nullable Object component) {
        return DataBindingUtil.<ActivityRemoteTargetExplorerBinding>inflate(
                inflater, R.layout.activity_remote_target_explorer, null, false, component);
    }

    public static ActivityRemoteTargetExplorerBinding bind(@NonNull View view) {
        return bind(view, DataBindingUtil.getDefaultComponent());
    }

    public static ActivityRemoteTargetExplorerBinding bind(@NonNull View view, @Nullable Object component) {
        return DataBindingUtil.<ActivityRemoteTargetExplorerBinding>bind(
                component, view, R.layout.activity_remote_target_explorer);
    }
}