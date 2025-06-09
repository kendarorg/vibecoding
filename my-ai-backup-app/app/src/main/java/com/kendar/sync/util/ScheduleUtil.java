package com.kendar.sync.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleUtil {
    /**
     * Utility function to calculate the next scheduled time based on a schedule string.
     * Can be used by both the ScheduleFragment and any scheduler service.
     *
     * @param scheduleString The schedule string in format:
     *                       M:[MODE] W:[TIME]/[DAY1,DAY2...] P:[STARTDATE]/[ENDDATE] R:[RETRYTRUE_FALSE]/[RETRYWAIT]
     * @return Calendar representing the next scheduled run time, or null if no valid time exists
     */
    public static Calendar getNextScheduledTime(String scheduleString) {
        if (scheduleString == null || scheduleString.isEmpty()) {
            return null;
        }

        try {
            // Parse schedule components
            Pattern modePattern = Pattern.compile("M:([A-Z]+)");
            Pattern timePattern = Pattern.compile("W:([0-9:]+)(?:/([0-9,]+))?");
            Pattern periodPattern = Pattern.compile("P:([0-9-: ]+)(?:/([0-9-: ]+))?");

            // Initialize variables
            String mode = null;
            String timeSpec = null;
            String daysSpec = null;
            Calendar startDateTime = null;
            Calendar endDateTime = null;

            // Extract mode
            Matcher modeMatcher = modePattern.matcher(scheduleString);
            if (modeMatcher.find()) {
                mode = modeMatcher.group(1);
            } else {
                return null;
            }

            // Extract time and days/dates
            Matcher timeMatcher = timePattern.matcher(scheduleString);
            if (timeMatcher.find()) {
                timeSpec = timeMatcher.group(1);
                daysSpec = timeMatcher.group(2);
            } else {
                return null;
            }

            // Extract period (start/end dates)
            Matcher periodMatcher = periodPattern.matcher(scheduleString);
            if (periodMatcher.find()) {
                String startDateStr = periodMatcher.group(1);
                if (startDateStr != null && !startDateStr.isEmpty()) {
                    try {
                        SimpleDateFormat fullDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                        Date date = fullDateTimeFormat.parse(startDateStr);
                        startDateTime = Calendar.getInstance();
                        startDateTime.setTime(date);
                    } catch (Exception e) {
                        // Invalid start date format
                        return null;
                    }
                }

                String endDateStr = periodMatcher.group(2);
                if (endDateStr != null && !endDateStr.isEmpty()) {
                    try {
                        SimpleDateFormat fullDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                        Date date = fullDateTimeFormat.parse(endDateStr);
                        endDateTime = Calendar.getInstance();
                        endDateTime.setTime(date);
                    } catch (Exception e) {
                        // Invalid end date format
                    }
                }
            }

            // If we don't have a start date, can't calculate next time
            if (startDateTime == null) {
                return null;
            }

            // Calculate next run time based on mode
            Calendar now = Calendar.getInstance();
            Calendar next = (Calendar) startDateTime.clone();

            // If the start time is in the future, that's our next run
            if (next.after(now)) {
                return next;
            }

            // Calculate based on mode
            switch (mode) {
                case "PERIODIC":
                    // Parse the time between executions
                    String[] timeParts = timeSpec.split(":");
                    if (timeParts.length != 2) return null;

                    int hours = Integer.parseInt(timeParts[0]);
                    int mins = Integer.parseInt(timeParts[1]);

                    if (hours == 0 && mins == 0) {
                        return null;
                    }

                    // Calculate how many periods have passed
                    long periodMillis = (hours * 60L + mins) * 60 * 1000;
                    long diffMillis = now.getTimeInMillis() - startDateTime.getTimeInMillis();
                    long periods = diffMillis / periodMillis + 1;

                    next.setTimeInMillis(startDateTime.getTimeInMillis() + periods * periodMillis);
                    break;

                case "DAILY":
                    // Set to today with the specified time
                    String[] dailyTimeParts = timeSpec.split(":");
                    if (dailyTimeParts.length != 2) return null;

                    int dailyHours = Integer.parseInt(dailyTimeParts[0]);
                    int dailyMins = Integer.parseInt(dailyTimeParts[1]);

                    next.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    next.set(Calendar.MONTH, now.get(Calendar.MONTH));
                    next.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));
                    next.set(Calendar.HOUR_OF_DAY, dailyHours);
                    next.set(Calendar.MINUTE, dailyMins);
                    next.set(Calendar.SECOND, 0);

                    // If today's time has passed, move to tomorrow
                    if (next.before(now)) {
                        next.add(Calendar.DAY_OF_MONTH, 1);
                    }
                    break;

                case "WEEKLY":
                    // Parse time and selected days
                    String[] weeklyTimeParts = timeSpec.split(":");
                    if (weeklyTimeParts.length != 2 || daysSpec == null) return null;

                    int weeklyHours = Integer.parseInt(weeklyTimeParts[0]);
                    int weeklyMins = Integer.parseInt(weeklyTimeParts[1]);

                    // Parse selected days (0=Sunday to 6=Saturday)
                    String[] daysArray = daysSpec.split(",");
                    boolean[] selectedDays = new boolean[7];
                    for (String day : daysArray) {
                        try {
                            int dayIndex = Integer.parseInt(day);
                            if (dayIndex >= 0 && dayIndex < 7) {
                                selectedDays[dayIndex] = true;
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid day
                        }
                    }

                    // Find the next selected day
                    int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1; // Convert to 0-based (0=Sunday)
                    next.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    next.set(Calendar.MONTH, now.get(Calendar.MONTH));
                    next.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));
                    next.set(Calendar.HOUR_OF_DAY, weeklyHours);
                    next.set(Calendar.MINUTE, weeklyMins);
                    next.set(Calendar.SECOND, 0);

                    // Check if today is selected and time hasn't passed
                    if (selectedDays[currentDayOfWeek] && next.after(now)) {
                        // Today is fine
                    } else {
                        // Find the next selected day
                        boolean foundDay = false;
                        for (int i = 1; i <= 7; i++) {
                            int dayToCheck = (currentDayOfWeek + i) % 7;
                            if (selectedDays[dayToCheck]) {
                                next.add(Calendar.DAY_OF_MONTH, i);
                                foundDay = true;
                                break;
                            }
                        }

                        if (!foundDay) {
                            return null;
                        }
                    }
                    break;

                case "MONTHLY":
                    // Parse time and selected dates
                    String[] monthlyTimeParts = timeSpec.split(":");
                    if (monthlyTimeParts.length != 2 || daysSpec == null) return null;

                    int monthlyHours = Integer.parseInt(monthlyTimeParts[0]);
                    int monthlyMins = Integer.parseInt(monthlyTimeParts[1]);

                    // Parse selected dates (1-31)
                    String[] datesArray = daysSpec.split(",");
                    List<Integer> selectedDates = new ArrayList<>();
                    for (String date : datesArray) {
                        try {
                            int dateVal = Integer.parseInt(date);
                            if (dateVal >= 1 && dateVal <= 31) {
                                selectedDates.add(dateVal);
                            }
                        } catch (NumberFormatException e) {
                            // Skip invalid date
                        }
                    }

                    if (selectedDates.isEmpty()) {
                        return null;
                    }

                    // Sort dates for easier processing
                    Collections.sort(selectedDates);

                    int currentDay = now.get(Calendar.DAY_OF_MONTH);
                    int currentMonth = now.get(Calendar.MONTH);
                    int currentYear = now.get(Calendar.YEAR);

                    next.set(Calendar.YEAR, currentYear);
                    next.set(Calendar.MONTH, currentMonth);
                    next.set(Calendar.HOUR_OF_DAY, monthlyHours);
                    next.set(Calendar.MINUTE, monthlyMins);
                    next.set(Calendar.SECOND, 0);

                    // Find next valid date
                    boolean foundDate = false;

                    // Check if current date is in the list and time hasn't passed
                    if (selectedDates.contains(currentDay)) {
                        next.set(Calendar.DAY_OF_MONTH, currentDay);
                        if (next.after(now)) {
                            foundDate = true;
                        }
                    }

                    if (!foundDate) {
                        // Find next date in current month
                        for (int date : selectedDates) {
                            if (date > currentDay) {
                                try {
                                    next.set(Calendar.DAY_OF_MONTH, date);
                                    foundDate = true;
                                    break;
                                } catch (IllegalArgumentException e) {
                                    // Skip invalid date (e.g., Feb 30)
                                }
                            }
                        }
                    }

                    if (!foundDate) {
                        // Move to first selected date in next month
                        next.add(Calendar.MONTH, 1);
                        next.set(Calendar.DAY_OF_MONTH, 1);

                        for (int i = 0; i < 12; i++) { // Try for up to a year
                            for (int date : selectedDates) {
                                try {
                                    next.set(Calendar.DAY_OF_MONTH, date);
                                    foundDate = true;
                                    break;
                                } catch (IllegalArgumentException e) {
                                    // Skip invalid date
                                }
                            }

                            if (foundDate) {
                                break;
                            }

                            next.add(Calendar.MONTH, 1);
                        }
                    }

                    if (!foundDate) {
                        return null;
                    }
                    break;

                case "ONSTARTUP":
                    // On startup has no next scheduled time
                    return null;

                default:
                    return null;
            }

            // Check if the next run is after the end date
            if (endDateTime != null && next.after(endDateTime)) {
                return null;
            }

            return next;
        }catch (Exception e){
            return null;
        }
    }

    public static boolean isOnWifiOnly(String scheduleTime) {
        return scheduleTime.indexOf("WIFI")>0 || scheduleTime.indexOf("wifi")>0;
    }

    public static boolean isOnChargeOnly(String scheduleTime) {
        return scheduleTime.indexOf("CHARGE")>0 || scheduleTime.indexOf("charge")>0;
    }

    public static boolean isOnStartup(String scheduleTime) {
        Pattern modePattern = Pattern.compile("M:([A-Z]+)");

        String mode = null;
        Matcher modeMatcher = modePattern.matcher(scheduleTime);
        if (modeMatcher.find()) {
            mode = modeMatcher.group(1);
        } else {
            return false;
        }
        return mode.equalsIgnoreCase("ONSTARTUP" );
    }
}
