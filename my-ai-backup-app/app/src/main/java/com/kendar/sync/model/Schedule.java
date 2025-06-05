package com.kendar.sync.model;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

/**
 * Data model class representing a backup schedule
 */
public class Schedule implements Serializable {
    public enum Type {
        MONTHLY,
        WEEKLY,
        SPECIFIC_TIME
    }

    private Type type;
    private boolean[] daysOfMonth; // For monthly, indexes 1-31
    private boolean[] daysOfWeek;  // For weekly, indexes 0-6 (Sunday-Saturday)
    private int hourOfDay;        // 0-23
    private int minuteOfHour;     // 0-59

    public Schedule() {
        // Default values
        type = Type.WEEKLY;
        daysOfMonth = new boolean[32]; // 0 is unused, 1-31 are days
        daysOfWeek = new boolean[7];   // 0-6 are days (Calendar.SUNDAY to Calendar.SATURDAY)
        daysOfWeek[Calendar.MONDAY] = true; // Default to Monday
        hourOfDay = 3; // 3 AM default
        minuteOfHour = 0;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean[] getDaysOfMonth() {
        return daysOfMonth;
    }

    public void setDaysOfMonth(boolean[] daysOfMonth) {
        this.daysOfMonth = daysOfMonth;
    }

    public boolean[] getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(boolean[] daysOfWeek) {
        this.daysOfWeek = daysOfWeek;
    }

    public int getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(int hourOfDay) {
        this.hourOfDay = hourOfDay;
    }

    public int getMinuteOfHour() {
        return minuteOfHour;
    }

    public void setMinuteOfHour(int minuteOfHour) {
        this.minuteOfHour = minuteOfHour;
    }

    /**
     * Calculates the next run time based on the current schedule
     * @param from The date to calculate from
     * @return The next scheduled run time
     */
    public Date getNextRunTime(Date from) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Always move at least one minute forward from the starting point
        cal.add(Calendar.MINUTE, 1);

        switch (type) {
            case SPECIFIC_TIME:
                // Find the next occurrence of the specific time
                if (cal.get(Calendar.HOUR_OF_DAY) > hourOfDay || 
                    (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && cal.get(Calendar.MINUTE) >= minuteOfHour)) {
                    // If we've already passed today's time, move to tomorrow
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minuteOfHour);
                break;

            case WEEKLY:
                // Find the next day of week that is scheduled
                int currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0-based for our array
                int daysToAdd = 0;

                // Check if we've passed today's time
                boolean passedTodayTime = cal.get(Calendar.HOUR_OF_DAY) > hourOfDay || 
                                        (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && 
                                         cal.get(Calendar.MINUTE) >= minuteOfHour);

                // If we haven't passed today's time and today is scheduled
                if (!passedTodayTime && daysOfWeek[currentDayOfWeek]) {
                    // Set today's time
                    daysToAdd = 0;
                } else {
                    // Find next scheduled day
                    for (int i = 1; i <= 7; i++) {
                        int nextDay = (currentDayOfWeek + i) % 7;
                        if (daysOfWeek[nextDay]) {
                            daysToAdd = i;
                            break;
                        }
                    }
                }

                cal.add(Calendar.DAY_OF_MONTH, daysToAdd);
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                cal.set(Calendar.MINUTE, minuteOfHour);
                break;

            case MONTHLY:
                // Find the next day of month that is scheduled
                int currentDay = cal.get(Calendar.DAY_OF_MONTH);

                // Check if we've passed today's time
                boolean passedToday = cal.get(Calendar.HOUR_OF_DAY) > hourOfDay || 
                                     (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && 
                                      cal.get(Calendar.MINUTE) >= minuteOfHour);

                // If today is scheduled and we haven't passed today's time
                if (daysOfMonth[currentDay] && !passedToday) {
                    // Set today's time
                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    cal.set(Calendar.MINUTE, minuteOfHour);
                } else {
                    // Check remaining days in this month
                    boolean foundInThisMonth = false;
                    int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

                    for (int i = currentDay + 1; i <= daysInMonth; i++) {
                        if (daysOfMonth[i]) {
                            cal.set(Calendar.DAY_OF_MONTH, i);
                            cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                            cal.set(Calendar.MINUTE, minuteOfHour);
                            foundInThisMonth = true;
                            break;
                        }
                    }

                    // If no days found in this month, move to next month
                    if (!foundInThisMonth) {
                        cal.add(Calendar.MONTH, 1);
                        cal.set(Calendar.DAY_OF_MONTH, 1);

                        // Find first scheduled day in next month
                        boolean found = false;
                        daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

                        for (int i = 1; i <= daysInMonth; i++) {
                            if (daysOfMonth[i]) {
                                cal.set(Calendar.DAY_OF_MONTH, i);
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                cal.set(Calendar.MINUTE, minuteOfHour);
                                found = true;
                                break;
                            }
                        }

                        // If no scheduled days, move one more month
                        if (!found) {
                            return getNextRunTime(cal.getTime()); // Recursive call to find next month
                        }
                    }
                }
                break;
        }

        return cal.getTime();
    }
}
