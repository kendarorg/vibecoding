package com.kendar.sync.ui.schedule;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.kendar.sync.dao.BackupJobDao;
import com.kendar.sync.databinding.ActivityScheduleEditBinding;
import com.kendar.sync.model.BackupJob;
import com.kendar.sync.model.Schedule;
import com.kendar.sync.util.AlarmScheduler;

import java.util.Date;

/**
 * Activity for editing backup schedule
 */
public class ScheduleEditActivity extends AppCompatActivity {
    private ActivityScheduleEditBinding binding;
    private BackupJobDao jobDao;
    private AlarmScheduler alarmScheduler;
    private long jobId;
    private BackupJob job;
    private Schedule schedule;
    private CheckBox[] dayOfMonthCheckboxes;
    private CheckBox[] dayOfWeekCheckboxes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityScheduleEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Edit Schedule");

        jobDao = new BackupJobDao(this);
        alarmScheduler = new AlarmScheduler(this);

        // Get job ID from intent
        jobId = getIntent().getLongExtra("job_id", -1);
        if (jobId == -1) {
            finish();
            return;
        }

        // Initialize day of month checkboxes
        dayOfMonthCheckboxes = new CheckBox[32]; // 1-31 (index 0 unused)
        for (int i = 1; i <= 31; i++) {
            int resId = getResources().getIdentifier("dayOfMonth" + i, "id", getPackageName());
            dayOfMonthCheckboxes[i] = findViewById(resId);
        }

        // Initialize day of week checkboxes
        dayOfWeekCheckboxes = new CheckBox[7]; // 0-6 for Sunday-Saturday
        String[] dayIds = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (int i = 0; i < 7; i++) {
            int resId = getResources().getIdentifier(dayIds[i] + "Checkbox", "id", getPackageName());
            dayOfWeekCheckboxes[i] = findViewById(resId);
        }

        // Set up radio button listeners
        binding.monthlyRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showMonthlyLayout();
                if (schedule != null) {
                    schedule.setType(Schedule.Type.MONTHLY);
                }
            }
        });

        binding.weeklyRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showWeeklyLayout();
                if (schedule != null) {
                    schedule.setType(Schedule.Type.WEEKLY);
                }
            }
        });

        binding.specificTimeRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showSpecificTimeLayout();
                if (schedule != null) {
                    schedule.setType(Schedule.Type.SPECIFIC_TIME);
                }
            }
        });

        // Set up time picker
        binding.timePicker.setIs24HourView(true);

        // Set up save button
        binding.saveButton.setOnClickListener(v -> saveSchedule());

        // Load job details
        loadJobSchedule();
    }

    private void loadJobSchedule() {
        job = jobDao.getJob(jobId);
        if (job == null) {
            finish();
            return;
        }

        schedule = job.getSchedule();
        if (schedule == null) {
            schedule = new Schedule();
            job.setSchedule(schedule);
        }

        // Set time
        binding.timePicker.setHour(schedule.getHourOfDay());
        binding.timePicker.setMinute(schedule.getMinuteOfHour());

        // Set schedule type
        switch (schedule.getType()) {
            case MONTHLY:
                binding.monthlyRadioButton.setChecked(true);
                showMonthlyLayout();
                break;

            case WEEKLY:
                binding.weeklyRadioButton.setChecked(true);
                showWeeklyLayout();
                break;

            case SPECIFIC_TIME:
                binding.specificTimeRadioButton.setChecked(true);
                showSpecificTimeLayout();
                break;
        }

        // Set days of month
        boolean[] daysOfMonth = schedule.getDaysOfMonth();
        for (int i = 1; i <= 31; i++) {
            if (dayOfMonthCheckboxes[i] != null) {
                dayOfMonthCheckboxes[i].setChecked(daysOfMonth[i]);
            }
        }

        // Set days of week
        boolean[] daysOfWeek = schedule.getDaysOfWeek();
        for (int i = 0; i < 7; i++) {
            if (dayOfWeekCheckboxes[i] != null) {
                dayOfWeekCheckboxes[i].setChecked(daysOfWeek[i]);
            }
        }
    }

    private void showMonthlyLayout() {
        binding.monthlyLayout.setVisibility(View.VISIBLE);
        binding.weeklyLayout.setVisibility(View.GONE);
        binding.specificTimeLayout.setVisibility(View.GONE);
    }

    private void showWeeklyLayout() {
        binding.monthlyLayout.setVisibility(View.GONE);
        binding.weeklyLayout.setVisibility(View.VISIBLE);
        binding.specificTimeLayout.setVisibility(View.GONE);
    }

    private void showSpecificTimeLayout() {
        binding.monthlyLayout.setVisibility(View.GONE);
        binding.weeklyLayout.setVisibility(View.GONE);
        binding.specificTimeLayout.setVisibility(View.VISIBLE);
    }

    private void saveSchedule() {
        // Set schedule type
        if (binding.monthlyRadioButton.isChecked()) {
            schedule.setType(Schedule.Type.MONTHLY);
        } else if (binding.weeklyRadioButton.isChecked()) {
            schedule.setType(Schedule.Type.WEEKLY);
        } else {
            schedule.setType(Schedule.Type.SPECIFIC_TIME);
        }

        // Set time
        schedule.setHourOfDay(binding.timePicker.getHour());
        schedule.setMinuteOfHour(binding.timePicker.getMinute());

        // Save days of month
        boolean[] daysOfMonth = new boolean[32]; // 0 unused, 1-31 are days
        for (int i = 1; i <= 31; i++) {
            if (dayOfMonthCheckboxes[i] != null) {
                daysOfMonth[i] = dayOfMonthCheckboxes[i].isChecked();
            }
        }
        schedule.setDaysOfMonth(daysOfMonth);

        // Save days of week
        boolean[] daysOfWeek = new boolean[7]; // 0-6 for Sunday-Saturday
        for (int i = 0; i < 7; i++) {
            if (dayOfWeekCheckboxes[i] != null) {
                daysOfWeek[i] = dayOfWeekCheckboxes[i].isChecked();
            }
        }
        schedule.setDaysOfWeek(daysOfWeek);

        // Validate based on schedule type
        boolean isValid = true;
        String errorMessage = "";

        switch (schedule.getType()) {
            case MONTHLY:
                boolean anyDaySelected = false;
                for (int i = 1; i <= 31; i++) {
                    if (daysOfMonth[i]) {
                        anyDaySelected = true;
                        break;
                    }
                }

                if (!anyDaySelected) {
                    isValid = false;
                    errorMessage = "Please select at least one day of the month";
                }
                break;

            case WEEKLY:
                anyDaySelected = false;
                for (int i = 0; i < 7; i++) {
                    if (daysOfWeek[i]) {
                        anyDaySelected = true;
                        break;
                    }
                }

                if (!anyDaySelected) {
                    isValid = false;
                    errorMessage = "Please select at least one day of the week";
                }
                break;

            case SPECIFIC_TIME:
                // Always valid
                break;
        }

        if (!isValid) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate next run time
        Date nextRun = schedule.getNextRunTime(new Date());
        job.setNextScheduledRun(nextRun);

        // Save to database
        jobDao.updateJob(job);

        // Schedule the job
        alarmScheduler.scheduleJob(job);

        Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
