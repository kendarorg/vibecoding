package com.kendar.sync.databinding;

import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Group;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.BR;
import com.kendar.sync.R;
import com.kendar.sync.viewmodel.ScheduleEditViewModel;
import com.google.android.material.appbar.MaterialToolbar;

public abstract class ActivityScheduleEditBinding extends ViewDataBinding {
    @NonNull
    public final ConstraintLayout constraintLayout;
    @NonNull
    public final MaterialToolbar toolbar;
    @NonNull
    public final Button buttonConfirmSchedule;
    @NonNull
    public final RadioGroup radioGroupScheduleType;
    @NonNull
    public final RadioButton radioButtonMonthly;
    @NonNull
    public final RadioButton radioButtonWeekly;
    @NonNull
    public final Group groupMonthlySchedule;
    @NonNull
    public final TextView textViewMonthlyDays;
    @NonNull
    public final RecyclerView recyclerViewMonthDays;
    @NonNull
    public final Group groupWeeklySchedule;
    @NonNull
    public final TextView textViewWeeklyDays;
    @NonNull
    public final CheckBox checkBoxMonday;
    @NonNull
    public final CheckBox checkBoxTuesday;
    @NonNull
    public final CheckBox checkBoxWednesday;
    @NonNull
    public final CheckBox checkBoxThursday;
    @NonNull
    public final CheckBox checkBoxFriday;
    @NonNull
    public final CheckBox checkBoxSaturday;
    @NonNull
    public final CheckBox checkBoxSunday;
    @NonNull
    public final TextView textViewTimeSelection;
    @NonNull
    public final TimePicker timePicker;

    protected long mDirtyFlags = -1;

    protected ActivityScheduleEditBinding(Object _bindingComponent, View _root, int _localFieldCount,
                                         ConstraintLayout constraintLayout, MaterialToolbar toolbar,
                                         Button buttonConfirmSchedule, RadioGroup radioGroupScheduleType,
                                         RadioButton radioButtonMonthly, RadioButton radioButtonWeekly,
                                         Group groupMonthlySchedule, TextView textViewMonthlyDays,
                                         RecyclerView recyclerViewMonthDays, Group groupWeeklySchedule,
                                         TextView textViewWeeklyDays, CheckBox checkBoxMonday,
                                         CheckBox checkBoxTuesday, CheckBox checkBoxWednesday,
                                         CheckBox checkBoxThursday, CheckBox checkBoxFriday,
                                         CheckBox checkBoxSaturday, CheckBox checkBoxSunday,
                                         TextView textViewTimeSelection, TimePicker timePicker) {
        super(_bindingComponent, _root, _localFieldCount);
        this.constraintLayout = constraintLayout;
        this.toolbar = toolbar;
        this.buttonConfirmSchedule = buttonConfirmSchedule;
        this.radioGroupScheduleType = radioGroupScheduleType;
        this.radioButtonMonthly = radioButtonMonthly;
        this.radioButtonWeekly = radioButtonWeekly;
        this.groupMonthlySchedule = groupMonthlySchedule;
        this.textViewMonthlyDays = textViewMonthlyDays;
        this.recyclerViewMonthDays = recyclerViewMonthDays;
        this.groupWeeklySchedule = groupWeeklySchedule;
        this.textViewWeeklyDays = textViewWeeklyDays;
        this.checkBoxMonday = checkBoxMonday;
        this.checkBoxTuesday = checkBoxTuesday;
        this.checkBoxWednesday = checkBoxWednesday;
        this.checkBoxThursday = checkBoxThursday;
        this.checkBoxFriday = checkBoxFriday;
        this.checkBoxSaturday = checkBoxSaturday;
        this.checkBoxSunday = checkBoxSunday;
        this.textViewTimeSelection = textViewTimeSelection;
        this.timePicker = timePicker;
    }

    public abstract void setViewModel(@Nullable ScheduleEditViewModel viewModel);

    @Nullable
    public abstract ScheduleEditViewModel getViewModel();

    @NonNull
    public static ActivityScheduleEditBinding inflate(@NonNull LayoutInflater inflater,
                                                     @Nullable ViewGroup parent,
                                                     boolean attachToParent) {
        return inflate(inflater, parent, attachToParent, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityScheduleEditBinding inflate(@NonNull LayoutInflater inflater,
                                                     @Nullable ViewGroup parent,
                                                     boolean attachToParent,
                                                     @Nullable Object component) {
        return DataBindingUtil.<ActivityScheduleEditBinding>inflate(
                inflater, R.layout.activity_schedule_edit, parent, attachToParent, component);
    }

    @NonNull
    public static ActivityScheduleEditBinding inflate(@NonNull LayoutInflater inflater) {
        return inflate(inflater, DataBindingUtil.getDefaultComponent());
    }

    @NonNull
    public static ActivityScheduleEditBinding inflate(@NonNull LayoutInflater inflater,
                                                     @Nullable Object component) {
        return DataBindingUtil.<ActivityScheduleEditBinding>inflate(
                inflater, R.layout.activity_schedule_edit, null, false, component);
    }

    public static ActivityScheduleEditBinding bind(@NonNull View view) {
        return bind(view, DataBindingUtil.getDefaultComponent());
    }

    public static ActivityScheduleEditBinding bind(@NonNull View view, @Nullable Object component) {
        return DataBindingUtil.<ActivityScheduleEditBinding>bind(
                component, view, R.layout.activity_schedule_edit);
    }
}