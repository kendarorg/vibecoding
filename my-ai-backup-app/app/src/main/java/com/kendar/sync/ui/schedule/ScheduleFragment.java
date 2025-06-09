package com.kendar.sync.ui.schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.kendar.sync.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleFragment extends Fragment {

    private static final String[] SCHEDULE_TYPES = {"Periodic", "Daily", "Weekly", "Monthly", "On Startup"};
    private static final int TYPE_PERIODIC = 0;
    private static final int TYPE_DAILY = 1;
    private static final int TYPE_WEEKLY = 2;
    private static final int TYPE_MONTHLY = 3;
    private static final int TYPE_ON_STARTUP = 4;

    private Spinner scheduleTypeSpinner;
    private FrameLayout scheduleOptionsContainer;
    private EditText startDateEdit, startTimeEdit, endDateEdit, endTimeEdit;
    private EditText retryAttemptsEdit, retryHoursEdit, retryMinutesEdit;
    private TextView nextScheduleText;
    private Button saveButton, cancelButton;
    private View retryWaitContainer;

    // Current schedule components
    private Calendar startDateTime = Calendar.getInstance();
    private Calendar endDateTime = null;
    private int currentType = TYPE_PERIODIC;
    private String currentScheduleString = null;
    private boolean initializing = false;

    // Views for different schedule types
    private View periodicView, dailyView, weeklyView, monthlyView;
    private List<CheckBox> weekdayCheckboxes = new ArrayList<>();
    private List<CheckBox> monthDateCheckboxes = new ArrayList<>();
    private CheckBox onWifiCheckbox, onChargingCheckbox;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat fullDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        scheduleTypeSpinner = view.findViewById(R.id.spinner_schedule_type);
        scheduleOptionsContainer = view.findViewById(R.id.schedule_options_container);
        startDateEdit = view.findViewById(R.id.edit_start_date);
        startTimeEdit = view.findViewById(R.id.edit_start_time);
        endDateEdit = view.findViewById(R.id.edit_end_date);
        endTimeEdit = view.findViewById(R.id.edit_end_time);
        retryAttemptsEdit = view.findViewById(R.id.edit_retry_attempts);
        retryHoursEdit = view.findViewById(R.id.edit_retry_hours);
        retryMinutesEdit = view.findViewById(R.id.edit_retry_minutes);
        retryWaitContainer = view.findViewById(R.id.retry_wait_container);
        nextScheduleText = view.findViewById(R.id.text_next_schedule);
        saveButton = view.findViewById(R.id.button_save);
        cancelButton = view.findViewById(R.id.button_cancel);
        onWifiCheckbox = view.findViewById(R.id.checkbox_on_wifi);
        onChargingCheckbox= view.findViewById(R.id.checkbox_on_charging);

        // Initialize schedule type spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, SCHEDULE_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scheduleTypeSpinner.setAdapter(adapter);

        // Initialize schedule option views
        initializeScheduleOptionViews(view);

        // Set up date/time pickers
        setupDateTimePickers();

        // Set up retry attempts
        retryAttemptsEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int attempts = s.toString().isEmpty() ? 0 : Integer.parseInt(s.toString());
                    retryWaitContainer.setVisibility(attempts > 0 ? View.VISIBLE : View.GONE);
                } catch (NumberFormatException e) {
                    retryAttemptsEdit.setText("0");
                    retryWaitContainer.setVisibility(View.GONE);
                }
                updateNextScheduleText();
            }
        });

        // Set up spinner change listener
        scheduleTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initializing) {
                    updateScheduleOptionsView(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                scheduleOptionsContainer.removeAllViews();
            }
        });

        // Set up buttons
        saveButton.setOnClickListener(v -> saveSchedule());
        cancelButton.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("scheduleTime", null);
            getParentFragmentManager().setFragmentResult("scheduleResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        });

        // Initialize with existing schedule if provided
        if (getArguments() != null && getArguments().containsKey("currentSchedule")) {
            String scheduleString = getArguments().getString("currentSchedule");
            if (scheduleString != null && !scheduleString.isEmpty()) {
                initializing = true;
                parseScheduleString(scheduleString);
                initializing = false;
            }
        }

        // Set default values
        if (startDateTime == null) {
            startDateTime = Calendar.getInstance();
        }
        updateDateTimeEditTexts();
        updateNextScheduleText();
    }

    private void initializeScheduleOptionViews(View rootView) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        // Periodic view
        periodicView = inflater.inflate(R.layout.layout_schedule_periodic, null);

        // Daily view
        dailyView = inflater.inflate(R.layout.layout_schedule_daily, null);

        // Weekly view
        weeklyView = inflater.inflate(R.layout.layout_schedule_weekly, null);
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_sunday));
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_monday));
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_tuesday));
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_wednesday));
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_thursday));
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_friday));
        weekdayCheckboxes.add(weeklyView.findViewById(R.id.checkbox_saturday));

        // Monthly view
        monthlyView = inflater.inflate(R.layout.layout_schedule_monthly, null);
        GridLayout gridLayout = monthlyView.findViewById(R.id.grid_monthly_dates);

        // Create 31 checkboxes for each day of the month
        for (int i = 1; i <= 31; i++) {
            CheckBox checkBox = new CheckBox(requireContext());
            checkBox.setText(String.valueOf(i));
            checkBox.setTag(i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            gridLayout.addView(checkBox, params);
            monthDateCheckboxes.add(checkBox);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> updateNextScheduleText());
        }

        // Set change listeners for all time fields
        setupTimeChangeListeners(periodicView, R.id.edit_periodic_hours, R.id.edit_periodic_minutes);
        setupTimeChangeListeners(dailyView, R.id.edit_daily_hours, R.id.edit_daily_minutes);
        setupTimeChangeListeners(weeklyView, R.id.edit_weekly_hours, R.id.edit_weekly_minutes);
        setupTimeChangeListeners(monthlyView, R.id.edit_monthly_hours, R.id.edit_monthly_minutes);

        // Set change listeners for weekday checkboxes
        for (CheckBox checkBox : weekdayCheckboxes) {
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> updateNextScheduleText());
        }

        // Show the default schedule type view
        updateScheduleOptionsView(currentType);
    }

    private void setupTimeChangeListeners(View view, int hoursId, int minutesId) {
        EditText hoursEdit = view.findViewById(hoursId);
        EditText minutesEdit = view.findViewById(minutesId);

        TextWatcher timeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateNextScheduleText();
            }
        };

        hoursEdit.addTextChangedListener(timeWatcher);
        minutesEdit.addTextChangedListener(timeWatcher);
    }

    private void setupDateTimePickers() {
        // Start date picker
        startDateEdit.setOnClickListener(v -> {
            Calendar cal = startDateTime != null ? startDateTime : Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        if (startDateTime == null) {
                            startDateTime = Calendar.getInstance();
                        }
                        startDateTime.set(Calendar.YEAR, year);
                        startDateTime.set(Calendar.MONTH, month);
                        startDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateTimeEditTexts();
                        updateNextScheduleText();
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Start time picker
        startTimeEdit.setOnClickListener(v -> {
            Calendar cal = startDateTime != null ? startDateTime : Calendar.getInstance();
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    requireContext(),
                    (view, hourOfDay, minute) -> {
                        if (startDateTime == null) {
                            startDateTime = Calendar.getInstance();
                        }
                        startDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        startDateTime.set(Calendar.MINUTE, minute);
                        startDateTime.set(Calendar.SECOND, 0);
                        updateDateTimeEditTexts();
                        updateNextScheduleText();
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
            );
            timePickerDialog.show();
        });

        // End date picker
        endDateEdit.setOnClickListener(v -> {
            Calendar cal = endDateTime != null ? endDateTime : Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        if (endDateTime == null) {
                            endDateTime = Calendar.getInstance();
                            endDateTime.set(Calendar.HOUR_OF_DAY, 23);
                            endDateTime.set(Calendar.MINUTE, 59);
                            endDateTime.set(Calendar.SECOND, 59);
                        }
                        endDateTime.set(Calendar.YEAR, year);
                        endDateTime.set(Calendar.MONTH, month);
                        endDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        updateDateTimeEditTexts();
                        updateNextScheduleText();
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // End time picker
        endTimeEdit.setOnClickListener(v -> {
            Calendar cal = endDateTime != null ? endDateTime : Calendar.getInstance();
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    requireContext(),
                    (view, hourOfDay, minute) -> {
                        if (endDateTime == null) {
                            endDateTime = Calendar.getInstance();
                        }
                        endDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        endDateTime.set(Calendar.MINUTE, minute);
                        endDateTime.set(Calendar.SECOND, 0);
                        updateDateTimeEditTexts();
                        updateNextScheduleText();
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
            );
            timePickerDialog.show();
        });
    }

    private void updateScheduleOptionsView(int scheduleType) {
        currentType = scheduleType;
        scheduleOptionsContainer.removeAllViews();

        switch (scheduleType) {
            case TYPE_PERIODIC:
                scheduleOptionsContainer.addView(periodicView);
                break;
            case TYPE_DAILY:
                scheduleOptionsContainer.addView(dailyView);
                break;
            case TYPE_WEEKLY:
                scheduleOptionsContainer.addView(weeklyView);
                break;
            case TYPE_MONTHLY:
                scheduleOptionsContainer.addView(monthlyView);
                break;
            case TYPE_ON_STARTUP:
                // No options needed for on startup
                break;
        }

        updateNextScheduleText();
    }

    private void updateDateTimeEditTexts() {
        if (startDateTime != null) {
            startDateEdit.setText(dateFormat.format(startDateTime.getTime()));
            startTimeEdit.setText(timeFormat.format(startDateTime.getTime()));
        } else {
            startDateEdit.setText("");
            startTimeEdit.setText("");
        }

        if (endDateTime != null) {
            endDateEdit.setText(dateFormat.format(endDateTime.getTime()));
            endTimeEdit.setText(timeFormat.format(endDateTime.getTime()));
        } else {
            endDateEdit.setText("");
            endTimeEdit.setText("");
        }
    }

    private void updateNextScheduleText() {
        Calendar nextRun = calculateNextScheduledRun();
        if (nextRun != null) {
            nextScheduleText.setText(fullDateTimeFormat.format(nextRun.getTime()));
        } else {
            nextScheduleText.setText("Not scheduled yet");
        }
    }

    private Calendar calculateNextScheduledRun() {
        // This is a simplified calculation for demonstration purposes
        // A real implementation would need to accurately calculate the next run time
        // based on the schedule parameters

        if (startDateTime == null) {
            return null;
        }

        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) startDateTime.clone();

        if (next.before(now)) {
            switch (currentType) {
                case TYPE_PERIODIC:
                    // Get hours and minutes from the periodic view
                    String hoursStr = ((EditText) periodicView.findViewById(R.id.edit_periodic_hours)).getText().toString();
                    String minsStr = ((EditText) periodicView.findViewById(R.id.edit_periodic_minutes)).getText().toString();

                    int hours = hoursStr.isEmpty() ? 0 : Integer.parseInt(hoursStr);
                    int mins = minsStr.isEmpty() ? 0 : Integer.parseInt(minsStr);

                    if (hours == 0 && mins == 0) {
                        return null;
                    }

                    // Calculate how many periods have passed
                    long diffMillis = now.getTimeInMillis() - next.getTimeInMillis();
                    long periodMillis = (hours * 60L + mins) * 60 * 1000;
                    long periods = diffMillis / periodMillis + 1;

                    next.setTimeInMillis(next.getTimeInMillis() + periods * periodMillis);
                    break;

                case TYPE_DAILY:
                    // Set next run to today with the specified time
                    next.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    next.set(Calendar.MONTH, now.get(Calendar.MONTH));
                    next.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));

                    // Get time from the daily view
                    String dailyHoursStr = ((EditText) dailyView.findViewById(R.id.edit_daily_hours)).getText().toString();
                    String dailyMinsStr = ((EditText) dailyView.findViewById(R.id.edit_daily_minutes)).getText().toString();

                    int dailyHours = dailyHoursStr.isEmpty() ? 0 : Integer.parseInt(dailyHoursStr);
                    int dailyMins = dailyMinsStr.isEmpty() ? 0 : Integer.parseInt(dailyMinsStr);

                    next.set(Calendar.HOUR_OF_DAY, dailyHours);
                    next.set(Calendar.MINUTE, dailyMins);

                    // If the time has already passed today, move to tomorrow
                    if (next.before(now)) {
                        next.add(Calendar.DAY_OF_MONTH, 1);
                    }
                    break;

                case TYPE_WEEKLY:
                    // Find the next selected day of week
                    boolean anyDaySelected = false;
                    int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK);

                    // Get time from the weekly view
                    String weeklyHoursStr = ((EditText) weeklyView.findViewById(R.id.edit_weekly_hours)).getText().toString();
                    String weeklyMinsStr = ((EditText) weeklyView.findViewById(R.id.edit_weekly_minutes)).getText().toString();

                    int weeklyHours = weeklyHoursStr.isEmpty() ? 0 : Integer.parseInt(weeklyHoursStr);
                    int weeklyMins = weeklyMinsStr.isEmpty() ? 0 : Integer.parseInt(weeklyMinsStr);

                    next.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    next.set(Calendar.MONTH, now.get(Calendar.MONTH));
                    next.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));
                    next.set(Calendar.HOUR_OF_DAY, weeklyHours);
                    next.set(Calendar.MINUTE, weeklyMins);

                    // Check if today is selected and time hasn't passed yet
                    if (weekdayCheckboxes.get(currentDayOfWeek - 1).isChecked() && next.after(now)) {
                        anyDaySelected = true;
                    } else {
                        // Find the next selected day
                        for (int i = 1; i <= 7; i++) {
                            int dayToCheck = (currentDayOfWeek - 1 + i) % 7 + 1; // 1=Sunday, 7=Saturday
                            if (weekdayCheckboxes.get(dayToCheck - 1).isChecked()) {
                                next.add(Calendar.DAY_OF_MONTH, i);
                                anyDaySelected = true;
                                break;
                            }
                        }
                    }

                    if (!anyDaySelected) {
                        return null;
                    }
                    break;

                case TYPE_MONTHLY:
                    // Get time from the monthly view
                    String monthlyHoursStr = ((EditText) monthlyView.findViewById(R.id.edit_monthly_hours)).getText().toString();
                    String monthlyMinsStr = ((EditText) monthlyView.findViewById(R.id.edit_monthly_minutes)).getText().toString();

                    int monthlyHours = monthlyHoursStr.isEmpty() ? 0 : Integer.parseInt(monthlyHoursStr);
                    int monthlyMins = monthlyMinsStr.isEmpty() ? 0 : Integer.parseInt(monthlyMinsStr);

                    int currentDay = now.get(Calendar.DAY_OF_MONTH);
                    int currentMonth = now.get(Calendar.MONTH);
                    int currentYear = now.get(Calendar.YEAR);

                    next.set(Calendar.YEAR, currentYear);
                    next.set(Calendar.MONTH, currentMonth);
                    next.set(Calendar.HOUR_OF_DAY, monthlyHours);
                    next.set(Calendar.MINUTE, monthlyMins);

                    // Check if any dates are selected
                    boolean anyDateSelected = false;

                    // Check if current date is selected and time hasn't passed
                    if (currentDay <= 31 && monthDateCheckboxes.get(currentDay - 1).isChecked()) {
                        next.set(Calendar.DAY_OF_MONTH, currentDay);
                        if (next.after(now)) {
                            anyDateSelected = true;
                        }
                    }

                    if (!anyDateSelected) {
                        // Find the next selected date in this month
                        for (int i = currentDay + 1; i <= 31; i++) {
                            if (i <= monthDateCheckboxes.size() && monthDateCheckboxes.get(i - 1).isChecked()) {
                                try {
                                    next.set(Calendar.DAY_OF_MONTH, i);
                                    anyDateSelected = true;
                                    break;
                                } catch (IllegalArgumentException e) {
                                    // Skip invalid dates (e.g., Feb 30)
                                    continue;
                                }
                            }
                        }
                    }

                    if (!anyDateSelected) {
                        // Move to the first selected date in the next month
                        Calendar temp = (Calendar) next.clone();
                        temp.add(Calendar.MONTH, 1);
                        temp.set(Calendar.DAY_OF_MONTH, 1);

                        for (int i = 1; i <= 31; i++) {
                            if (monthDateCheckboxes.get(i - 1).isChecked()) {
                                try {
                                    temp.set(Calendar.DAY_OF_MONTH, i);
                                    next = temp;
                                    anyDateSelected = true;
                                    break;
                                } catch (IllegalArgumentException e) {
                                    // Skip invalid dates
                                    continue;
                                }
                            }
                        }
                    }

                    if (!anyDateSelected) {
                        return null;
                    }
                    break;

                case TYPE_ON_STARTUP:
                    // On startup has no next scheduled time
                    return null;
            }
        }

        // Check if the next run is after the end date
        if (endDateTime != null && next.after(endDateTime)) {
            return null;
        }

        return next;
    }

    private void parseScheduleString(String scheduleString) {
        try {
            // Example: M:DAILY W:12:30 P:2023-05-01 12:00/2023-12-31 23:59 R:true/01:00 WIFI CHARGE
            Pattern modePattern = Pattern.compile("M:([A-Z]+)");
            Pattern timePattern = Pattern.compile("W:([0-9:]+)(?:/([0-9,]+))?");
            Pattern periodPattern = Pattern.compile("P:([0-9-: ]+)(?:/([0-9-: ]+))?");
            Pattern retryPattern = Pattern.compile("R:(true|false)/([0-9:]+)");
            Pattern wifiPattern = Pattern.compile("WIFI");
            Pattern chargePattern = Pattern.compile("CHARGE");

            //WIFI and CHARGE
            Matcher wifiMatcher = wifiPattern.matcher(scheduleString);
            if(wifiMatcher.find()){
                this.onWifiCheckbox.setChecked(true);
            }
            Matcher chargeMatcher = chargePattern.matcher(scheduleString);
            if(chargeMatcher.find()){
                this.onChargingCheckbox.setChecked(true);
            }
            // Parse mode
            Matcher modeMatcher = modePattern.matcher(scheduleString);
            if (modeMatcher.find()) {
                String mode = modeMatcher.group(1);
                switch (mode) {
                    case "PERIODIC":
                        currentType = TYPE_PERIODIC;
                        break;
                    case "DAILY":
                        currentType = TYPE_DAILY;
                        break;
                    case "WEEKLY":
                        currentType = TYPE_WEEKLY;
                        break;
                    case "MONTHLY":
                        currentType = TYPE_MONTHLY;
                        break;
                    case "ONSTARTUP":
                        currentType = TYPE_ON_STARTUP;
                        break;
                }
                scheduleTypeSpinner.setSelection(currentType);
                updateScheduleOptionsView(currentType);
            }

            // Parse time and days
            Matcher timeMatcher = timePattern.matcher(scheduleString);
            if (timeMatcher.find()) {
                String timeStr = timeMatcher.group(1);
                String[] timeParts = timeStr.split(":");

                switch (currentType) {
                    case TYPE_PERIODIC:
                        if (timeParts.length >= 2) {
                            ((EditText) periodicView.findViewById(R.id.edit_periodic_hours)).setText(timeParts[0]);
                            ((EditText) periodicView.findViewById(R.id.edit_periodic_minutes)).setText(timeParts[1]);
                        }
                        break;

                    case TYPE_DAILY:
                        if (timeParts.length >= 2) {
                            ((EditText) dailyView.findViewById(R.id.edit_daily_hours)).setText(timeParts[0]);
                            ((EditText) dailyView.findViewById(R.id.edit_daily_minutes)).setText(timeParts[1]);
                        }
                        break;

                    case TYPE_WEEKLY:
                        if (timeParts.length >= 2) {
                            ((EditText) weeklyView.findViewById(R.id.edit_weekly_hours)).setText(timeParts[0]);
                            ((EditText) weeklyView.findViewById(R.id.edit_weekly_minutes)).setText(timeParts[1]);
                        }

                        String daysStr = timeMatcher.group(2);
                        if (daysStr != null && !daysStr.isEmpty()) {
                            String[] days = daysStr.split(",");
                            for (String day : days) {
                                try {
                                    int dayIndex = Integer.parseInt(day);
                                    if (dayIndex >= 1 && dayIndex <= 7) {
                                        weekdayCheckboxes.get(dayIndex - 1).setChecked(true);
                                    }
                                } catch (NumberFormatException e) {
                                    // Skip invalid day
                                }
                            }
                        }
                        break;

                    case TYPE_MONTHLY:
                        if (timeParts.length >= 2) {
                            ((EditText) monthlyView.findViewById(R.id.edit_monthly_hours)).setText(timeParts[0]);
                            ((EditText) monthlyView.findViewById(R.id.edit_monthly_minutes)).setText(timeParts[1]);
                        }

                        String datesStr = timeMatcher.group(2);
                        if (datesStr != null && !datesStr.isEmpty()) {
                            String[] dates = datesStr.split(",");
                            for (String date : dates) {
                                try {
                                    int dateIndex = Integer.parseInt(date);
                                    if (dateIndex >= 1 && dateIndex <= 31) {
                                        monthDateCheckboxes.get(dateIndex - 1).setChecked(true);
                                    }
                                } catch (NumberFormatException e) {
                                    // Skip invalid date
                                }
                            }
                        }
                        break;
                }
            }

            // Parse period (start/end dates)
            Matcher periodMatcher = periodPattern.matcher(scheduleString);
            if (periodMatcher.find()) {
                String startDateStr = periodMatcher.group(1);
                if (startDateStr != null && !startDateStr.isEmpty()) {
                    try {
                        Date startDate = fullDateTimeFormat.parse(startDateStr);
                        if (startDate != null) {
                            startDateTime = Calendar.getInstance();
                            startDateTime.setTime(startDate);
                        }
                    } catch (Exception e) {
                        // Use current time if parsing fails
                        startDateTime = Calendar.getInstance();
                    }
                }

                String endDateStr = periodMatcher.group(2);
                if (endDateStr != null && !endDateStr.isEmpty()) {
                    try {
                        Date endDate = fullDateTimeFormat.parse(endDateStr);
                        if (endDate != null) {
                            endDateTime = Calendar.getInstance();
                            endDateTime.setTime(endDate);
                        }
                    } catch (Exception e) {
                        endDateTime = null;
                    }
                }

                updateDateTimeEditTexts();
            }

            // Parse retry settings
            Matcher retryMatcher = retryPattern.matcher(scheduleString);
            if (retryMatcher.find()) {
                boolean retryEnabled = Boolean.parseBoolean(retryMatcher.group(1));
                String retryWaitStr = retryMatcher.group(2);

                if (retryEnabled) {
                    retryAttemptsEdit.setText("3"); // Default value

                    if (retryWaitStr != null && !retryWaitStr.isEmpty()) {
                        String[] waitParts = retryWaitStr.split(":");
                        if (waitParts.length >= 2) {
                            retryHoursEdit.setText(waitParts[0]);
                            retryMinutesEdit.setText(waitParts[1]);
                        }
                    }
                } else {
                    retryAttemptsEdit.setText("0");
                }
            }

            currentScheduleString = scheduleString;

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Invalid schedule format", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildScheduleString() {
        StringBuilder sb = new StringBuilder();

        // Add mode
        switch (currentType) {
            case TYPE_PERIODIC:
                sb.append("M:PERIODIC");
                break;
            case TYPE_DAILY:
                sb.append("M:DAILY");
                break;
            case TYPE_WEEKLY:
                sb.append("M:WEEKLY");
                break;
            case TYPE_MONTHLY:
                sb.append("M:MONTHLY");
                break;
            case TYPE_ON_STARTUP:
                sb.append("M:ONSTARTUP");
                break;
        }

        // Add time and days/dates if applicable
        switch (currentType) {
            case TYPE_PERIODIC:
                String periodicHours = ((EditText) periodicView.findViewById(R.id.edit_periodic_hours)).getText().toString();
                String periodicMins = ((EditText) periodicView.findViewById(R.id.edit_periodic_minutes)).getText().toString();

                periodicHours = periodicHours.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(periodicHours));
                periodicMins = periodicMins.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(periodicMins));

                sb.append(" W:").append(periodicHours).append(":").append(periodicMins);
                break;

            case TYPE_DAILY:
                String dailyHours = ((EditText) dailyView.findViewById(R.id.edit_daily_hours)).getText().toString();
                String dailyMins = ((EditText) dailyView.findViewById(R.id.edit_daily_minutes)).getText().toString();

                dailyHours = dailyHours.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(dailyHours));
                dailyMins = dailyMins.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(dailyMins));

                sb.append(" W:").append(dailyHours).append(":").append(dailyMins);
                break;

            case TYPE_WEEKLY:
                String weeklyHours = ((EditText) weeklyView.findViewById(R.id.edit_weekly_hours)).getText().toString();
                String weeklyMins = ((EditText) weeklyView.findViewById(R.id.edit_weekly_minutes)).getText().toString();

                weeklyHours = weeklyHours.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(weeklyHours));
                weeklyMins = weeklyMins.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(weeklyMins));

                sb.append(" W:").append(weeklyHours).append(":").append(weeklyMins);

                // Add selected days
                List<Integer> selectedDays = new ArrayList<>();
                for (int i = 0; i < weekdayCheckboxes.size(); i++) {
                    if (weekdayCheckboxes.get(i).isChecked()) {
                        selectedDays.add(i + 1); // 1=Sunday, 7=Saturday
                    }
                }

                if (!selectedDays.isEmpty()) {
                    sb.append("/");
                    for (int i = 0; i < selectedDays.size(); i++) {
                        sb.append(selectedDays.get(i));
                        if (i < selectedDays.size() - 1) {
                            sb.append(",");
                        }
                    }
                }
                break;

            case TYPE_MONTHLY:
                String monthlyHours = ((EditText) monthlyView.findViewById(R.id.edit_monthly_hours)).getText().toString();
                String monthlyMins = ((EditText) monthlyView.findViewById(R.id.edit_monthly_minutes)).getText().toString();

                monthlyHours = monthlyHours.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(monthlyHours));
                monthlyMins = monthlyMins.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(monthlyMins));

                sb.append(" W:").append(monthlyHours).append(":").append(monthlyMins);

                // Add selected dates
                List<Integer> selectedDates = new ArrayList<>();
                for (int i = 0; i < monthDateCheckboxes.size(); i++) {
                    if (monthDateCheckboxes.get(i).isChecked()) {
                        selectedDates.add(i + 1);
                    }
                }

                if (!selectedDates.isEmpty()) {
                    sb.append("/");
                    for (int i = 0; i < selectedDates.size(); i++) {
                        sb.append(selectedDates.get(i));
                        if (i < selectedDates.size() - 1) {
                            sb.append(",");
                        }
                    }
                }
                break;

            case TYPE_ON_STARTUP:
                sb.append(" W:00:00");
                break;
        }

        // Add period (start/end dates)
        sb.append(" P:");
        if (startDateTime != null) {
            sb.append(fullDateTimeFormat.format(startDateTime.getTime()));
        } else {
            sb.append(fullDateTimeFormat.format(Calendar.getInstance().getTime()));
        }

        if (endDateTime != null) {
            sb.append("/").append(fullDateTimeFormat.format(endDateTime.getTime()));
        }

        // Add retry settings
        String retryAttemptsStr = retryAttemptsEdit.getText().toString();
        int retryAttempts = retryAttemptsStr.isEmpty() ? 0 : Integer.parseInt(retryAttemptsStr);

        sb.append(" R:").append(retryAttempts > 0);

        if (retryAttempts > 0) {
            String retryHours = retryHoursEdit.getText().toString();
            String retryMins = retryMinutesEdit.getText().toString();

            retryHours = retryHours.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(retryHours));
            retryMins = retryMins.isEmpty() ? "00" : String.format(Locale.US, "%02d", Integer.parseInt(retryMins));

            sb.append("/").append(retryHours).append(":").append(retryMins);
        } else {
            sb.append("/00:00");
        }


        if(this.onWifiCheckbox.isChecked()){
            sb.append(" WIFI");
        }
        if(this.onChargingCheckbox.isChecked()){
            sb.append(" CHARGE");
        }

        return sb.toString();
    }

    private boolean validateSchedule() {
        boolean isValid = true;
        String errorMessage = null;

        // Validate based on schedule type
        switch (currentType) {
            case TYPE_PERIODIC:
                String periodicHours = ((EditText) periodicView.findViewById(R.id.edit_periodic_hours)).getText().toString();
                String periodicMins = ((EditText) periodicView.findViewById(R.id.edit_periodic_minutes)).getText().toString();

                if ((periodicHours.isEmpty() || "0".equals(periodicHours)) &&
                        (periodicMins.isEmpty() || "0".equals(periodicMins))) {
                    isValid = false;
                    errorMessage = "Time between executions must be greater than zero";
                }
                break;

            case TYPE_WEEKLY:
                boolean anyDaySelected = false;
                for (CheckBox checkbox : weekdayCheckboxes) {
                    if (checkbox.isChecked()) {
                        anyDaySelected = true;
                        break;
                    }
                }

                if (!anyDaySelected) {
                    isValid = false;
                    errorMessage = "At least one day of the week must be selected";
                }
                break;

            case TYPE_MONTHLY:
                boolean anyDateSelected = false;
                for (CheckBox checkbox : monthDateCheckboxes) {
                    if (checkbox.isChecked()) {
                        anyDateSelected = true;
                        break;
                    }
                }

                if (!anyDateSelected) {
                    isValid = false;
                    errorMessage = "At least one date must be selected";
                }
                break;
        }

        // Validate retry settings
        String retryAttemptsStr = retryAttemptsEdit.getText().toString();
        if (!retryAttemptsStr.isEmpty()) {
            try {
                int retryAttempts = Integer.parseInt(retryAttemptsStr);
                if (retryAttempts > 0) {
                    String retryHours = retryHoursEdit.getText().toString();
                    String retryMins = retryMinutesEdit.getText().toString();

                    if ((retryHours.isEmpty() || "0".equals(retryHours)) &&
                            (retryMins.isEmpty() || "0".equals(retryMins))) {
                        isValid = false;
                        errorMessage = "Wait time between retries must be greater than zero";
                    }
                }
            } catch (NumberFormatException e) {
                isValid = false;
                errorMessage = "Invalid retry attempts value";
            }
        }

        if (!isValid && errorMessage != null) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        }

        return isValid;
    }

    private void saveSchedule() {
        if (!validateSchedule()) {
            return;
        }

        String scheduleString = buildScheduleString();

        Bundle result = new Bundle();
        result.putString("scheduleTime", scheduleString);
        getParentFragmentManager().setFragmentResult("scheduleResult", result);
        Navigation.findNavController(requireView()).navigateUp();
    }
}