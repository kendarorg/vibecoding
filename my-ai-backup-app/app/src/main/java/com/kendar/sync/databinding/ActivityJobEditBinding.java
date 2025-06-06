package com.kendar.sync.databinding;

import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;

import com.kendar.sync.BR;
import com.kendar.sync.R;
import com.kendar.sync.viewmodel.JobEditViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public abstract class ActivityJobEditBinding extends ViewDataBinding {
    @NonNull
    public final NestedScrollView scrollView;
    @NonNull
    public final ConstraintLayout constraintLayout;
    @NonNull
    public final TextInputLayout textInputLayoutJobName;
    @NonNull
    public final TextInputEditText editTextJobName;
    @NonNull
    public final TextInputLayout textInputLayoutLocalDirectory;
    @NonNull
    public final TextInputEditText editTextLocalDirectory;
    @NonNull
    public final Button buttonExploreLocal;
    @NonNull
    public final TextInputLayout textInputLayoutRemoteAddress;
    @NonNull
    public final TextInputEditText editTextRemoteAddress;
    @NonNull
    public final TextInputLayout textInputLayoutRemotePort;
    @NonNull
    public final TextInputEditText editTextRemotePort;
    @NonNull
    public final TextInputLayout textInputLayoutRemoteTarget;
    @NonNull
    public final TextInputEditText editTextRemoteTarget;
    @NonNull
    public final Button buttonExploreRemote;
    @NonNull
    public final TextInputLayout textInputLayoutLogin;
    @NonNull
    public final TextInputEditText editTextLogin;
    @NonNull
    public final TextInputLayout textInputLayoutPassword;
    @NonNull
    public final TextInputEditText editTextPassword;
    @NonNull
    public final TextInputLayout textInputLayoutSchedule;
    @NonNull
    public final TextInputEditText editTextSchedule;
    @NonNull
    public final Button buttonEditSchedule;
    @NonNull
    public final CheckBox checkBoxWifiOnly;
    @NonNull
    public final CheckBox checkBoxChargingOnly;
    @NonNull
    public final Button buttonSaveJob;
    @NonNull
    public final Button buttonCancel;

    protected long mDirtyFlags = -1;

    protected ActivityJobEditBinding(Object _bindingComponent, View _root, int _localFieldCount,
                                    NestedScrollView scrollView, ConstraintLayout constraintLayout, 
                                    TextInputLayout textInputLayoutJobName, TextInputEditText editTextJobName,
                                    TextInputLayout textInputLayoutLocalDirectory, TextInputEditText editTextLocalDirectory,
                                    Button buttonExploreLocal, TextInputLayout textInputLayoutRemoteAddress,
                                    TextInputEditText editTextRemoteAddress, TextInputLayout textInputLayoutRemotePort,
                                    TextInputEditText editTextRemotePort, TextInputLayout textInputLayoutRemoteTarget,
                                    TextInputEditText editTextRemoteTarget, Button buttonExploreRemote,
                                    TextInputLayout textInputLayoutLogin, TextInputEditText editTextLogin,
                                    TextInputLayout textInputLayoutPassword, TextInputEditText editTextPassword,
                                    TextInputLayout textInputLayoutSchedule, TextInputEditText editTextSchedule,
                                    Button buttonEditSchedule, CheckBox checkBoxWifiOnly, CheckBox checkBoxChargingOnly,
                                    Button buttonSaveJob, Button buttonCancel) {
        super(_bindingComponent, _root, _localFieldCount);
        this.scrollView = scrollView;
        this.constraintLayout = constraintLayout;
        this.textInputLayoutJobName = textInputLayoutJobName;
        this.editTextJobName = editTextJobName;
        this.textInputLayoutLocalDirectory = textInputLayoutLocalDirectory;
        this.editTextLocalDirectory = editTextLocalDirectory;
        this.buttonExploreLocal = buttonExploreLocal;
        this.textInputLayoutRemoteAddress = textInputLayoutRemoteAddress;
        this.editTextRemoteAddress = editTextRemoteAddress;
        this.textInputLayoutRemotePort = textInputLayoutRemotePort;
        this.editTextRemotePort = editTextRemotePort;
        this.textInputLayoutRemoteTarget = textInputLayoutRemoteTarget;
        this.editTextRemoteTarget = editTextRemoteTarget;
        this.buttonExploreRemote = buttonExploreRemote;
        this.textInputLayoutLogin = textInputLayoutLogin;
        this.editTextLogin = editTextLogin;
        this.textInputLayoutPassword = textInputLayoutPassword;
        this.editTextPassword = editTextPassword;
        this.textInputLayoutSchedule = textInputLayoutSchedule;
        this.editTextSchedule = editTextSchedule;
        this.buttonEditSchedule = buttonEditSchedule;
        this.checkBoxWifiOnly = checkBoxWifiOnly;
        this.checkBoxChargingOnly = checkBoxChargingOnly;
        this.buttonSaveJob = buttonSaveJob;
        this.buttonCancel = buttonCancel;
    }

    public abstract void setViewModel(@Nullable JobEditViewModel viewModel);

    @Nullable
    public abstract JobEditViewModel getViewModel();

    @NonNull
    public static ActivityJobEditBinding inflate(@NonNull LayoutInflater inflater,
                                               @Nullable ViewGroup parent,
                                               boolean attachToParent) {
        return inflate(inflater, parent, attachToParent, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityJobEditBinding inflate(@NonNull LayoutInflater inflater,
                                               @Nullable ViewGroup parent,
                                               boolean attachToParent,
                                               @Nullable Object component) {
        return DataBindingUtil.<ActivityJobEditBinding>inflate(
                inflater, R.layout.activity_job_edit, parent, attachToParent, component);
    }

    @NonNull
    public static ActivityJobEditBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityJobEditBinding inflate(@NonNull LayoutInflater inflater,
                                               @Nullable Object component) {
        return DataBindingUtil.<ActivityJobEditBinding>inflate(
                inflater, R.layout.activity_job_edit, null, false, component);
    }

    public static ActivityJobEditBinding bind(@NonNull View view) {
        return bind(view, DataBindingUtil.getDefaultComponent());
    }

    public static ActivityJobEditBinding bind(@NonNull View view, @Nullable Object component) {
        return DataBindingUtil.<ActivityJobEditBinding>bind(component, view, R.layout.activity_job_edit);
    }
}