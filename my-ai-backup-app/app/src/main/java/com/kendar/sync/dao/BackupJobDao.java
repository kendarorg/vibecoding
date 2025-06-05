package com.kendar.sync.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.kendar.sync.model.BackupJob;
import com.kendar.sync.model.Schedule;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Database access object for BackupJob entities
 */
public class BackupJobDao extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "backup_jobs.db";
    private static final int DATABASE_VERSION = 1;

    // Table and column names
    private static final String TABLE_JOBS = "jobs";
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_LOCAL_DIR = "local_directory";
    private static final String COL_REMOTE_ADDR = "remote_address";
    private static final String COL_REMOTE_PORT = "remote_port";
    private static final String COL_REMOTE_TARGET = "remote_target";
    private static final String COL_LOGIN = "login";
    private static final String COL_PASSWORD = "password";
    private static final String COL_SCHEDULE_TYPE = "schedule_type";
    private static final String COL_DAYS_OF_MONTH = "days_of_month";
    private static final String COL_DAYS_OF_WEEK = "days_of_week";
    private static final String COL_HOUR = "hour";
    private static final String COL_MINUTE = "minute";
    private static final String COL_WIFI_ONLY = "wifi_only";
    private static final String COL_CHARGING_ONLY = "charging_only";
    private static final String COL_LAST_RUN = "last_run_time";
    private static final String COL_LAST_DURATION = "last_duration";
    private static final String COL_NEXT_RUN = "next_run_time";

    private static final String CREATE_TABLE_JOBS = "CREATE TABLE " + TABLE_JOBS + "("
            + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_NAME + " TEXT NOT NULL, "
            + COL_LOCAL_DIR + " TEXT, "
            + COL_REMOTE_ADDR + " TEXT, "
            + COL_REMOTE_PORT + " INTEGER, "
            + COL_REMOTE_TARGET + " TEXT, "
            + COL_LOGIN + " TEXT, "
            + COL_PASSWORD + " TEXT, "
            + COL_SCHEDULE_TYPE + " INTEGER, "
            + COL_DAYS_OF_MONTH + " TEXT, "
            + COL_DAYS_OF_WEEK + " TEXT, "
            + COL_HOUR + " INTEGER, "
            + COL_MINUTE + " INTEGER, "
            + COL_WIFI_ONLY + " INTEGER, "
            + COL_CHARGING_ONLY + " INTEGER, "
            + COL_LAST_RUN + " INTEGER, "
            + COL_LAST_DURATION + " INTEGER, "
            + COL_NEXT_RUN + " INTEGER)";

    public BackupJobDao(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_JOBS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrade in future versions
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_JOBS);
        onCreate(db);
    }

    /**
     * Insert a new backup job into the database
     */
    public long insertJob(BackupJob job) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = getContentValuesFromJob(job);

        long id = db.insert(TABLE_JOBS, null, values);
        job.setId(id);
        db.close();
        return id;
    }

    /**
     * Update an existing backup job
     */
    public int updateJob(BackupJob job) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = getContentValuesFromJob(job);

        int result = db.update(TABLE_JOBS, values, COL_ID + " = ?",
                new String[]{String.valueOf(job.getId())});
        db.close();
        return result;
    }

    /**
     * Delete a backup job
     */
    public void deleteJob(long jobId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_JOBS, COL_ID + " = ?", new String[]{String.valueOf(jobId)});
        db.close();
    }

    /**
     * Get all backup jobs
     */
    public List<BackupJob> getAllJobs() {
        List<BackupJob> jobList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_JOBS;

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                BackupJob job = getJobFromCursor(cursor);
                jobList.add(job);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return jobList;
    }

    /**
     * Get a specific backup job by ID
     */
    public BackupJob getJob(long jobId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_JOBS, null, COL_ID + " = ?",
                new String[]{String.valueOf(jobId)}, null, null, null);

        BackupJob job = null;
        if (cursor.moveToFirst()) {
            job = getJobFromCursor(cursor);
        }

        cursor.close();
        db.close();
        return job;
    }

    /**
     * Get all jobs that are due to run
     */
    public List<BackupJob> getDueJobs() {
        List<BackupJob> dueJobs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Get current time
        long currentTime = System.currentTimeMillis();

        // Query for jobs where next_run_time <= current time
        String query = "SELECT * FROM " + TABLE_JOBS + 
                       " WHERE " + COL_NEXT_RUN + " <= ? AND " + 
                       COL_NEXT_RUN + " > 0";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(currentTime)});

        if (cursor.moveToFirst()) {
            do {
                BackupJob job = getJobFromCursor(cursor);
                dueJobs.add(job);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return dueJobs;
    }

    /**
     * Update the last run information for a job
     */
    public void updateJobRunInfo(long jobId, Date lastRunTime, long duration, Date nextRun) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_LAST_RUN, lastRunTime != null ? lastRunTime.getTime() : 0);
        values.put(COL_LAST_DURATION, duration);
        values.put(COL_NEXT_RUN, nextRun != null ? nextRun.getTime() : 0);

        db.update(TABLE_JOBS, values, COL_ID + " = ?", new String[]{String.valueOf(jobId)});
        db.close();
    }

    /**
     * Helper method to convert cursor to BackupJob
     */
    private BackupJob getJobFromCursor(Cursor cursor) {
        BackupJob job = new BackupJob();
        job.setId(cursor.getLong(cursor.getColumnIndex(COL_ID)));
        job.setName(cursor.getString(cursor.getColumnIndex(COL_NAME)));
        job.setLocalDirectory(cursor.getString(cursor.getColumnIndex(COL_LOCAL_DIR)));
        job.setRemoteAddress(cursor.getString(cursor.getColumnIndex(COL_REMOTE_ADDR)));
        job.setRemotePort(cursor.getInt(cursor.getColumnIndex(COL_REMOTE_PORT)));
        job.setRemoteTarget(cursor.getString(cursor.getColumnIndex(COL_REMOTE_TARGET)));
        job.setLogin(cursor.getString(cursor.getColumnIndex(COL_LOGIN)));
        job.setPassword(cursor.getString(cursor.getColumnIndex(COL_PASSWORD)));

        // Set schedule
        Schedule schedule = new Schedule();
        int scheduleType = cursor.getInt(cursor.getColumnIndex(COL_SCHEDULE_TYPE));
        schedule.setType(Schedule.Type.values()[scheduleType]);

        String daysOfMonthStr = cursor.getString(cursor.getColumnIndex(COL_DAYS_OF_MONTH));
        if (daysOfMonthStr != null) {
            boolean[] daysOfMonth = new boolean[32];
            String[] parts = daysOfMonthStr.split(",");
            for (String part : parts) {
                try {
                    int day = Integer.parseInt(part);
                    if (day >= 1 && day <= 31) {
                        daysOfMonth[day] = true;
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid format
                }
            }
            schedule.setDaysOfMonth(daysOfMonth);
        }

        String daysOfWeekStr = cursor.getString(cursor.getColumnIndex(COL_DAYS_OF_WEEK));
        if (daysOfWeekStr != null) {
            boolean[] daysOfWeek = new boolean[7];
            String[] parts = daysOfWeekStr.split(",");
            for (String part : parts) {
                try {
                    int day = Integer.parseInt(part);
                    if (day >= 0 && day <= 6) {
                        daysOfWeek[day] = true;
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid format
                }
            }
            schedule.setDaysOfWeek(daysOfWeek);
        }

        schedule.setHourOfDay(cursor.getInt(cursor.getColumnIndex(COL_HOUR)));
        schedule.setMinuteOfHour(cursor.getInt(cursor.getColumnIndex(COL_MINUTE)));
        job.setSchedule(schedule);

        // Set job conditions
        job.setWifiOnly(cursor.getInt(cursor.getColumnIndex(COL_WIFI_ONLY)) == 1);
        job.setChargingOnly(cursor.getInt(cursor.getColumnIndex(COL_CHARGING_ONLY)) == 1);

        // Set run information
        long lastRunTime = cursor.getLong(cursor.getColumnIndex(COL_LAST_RUN));
        if (lastRunTime > 0) {
            job.setLastRunTime(new Date(lastRunTime));
        }

        job.setLastRunDuration(cursor.getLong(cursor.getColumnIndex(COL_LAST_DURATION)));

        long nextRunTime = cursor.getLong(cursor.getColumnIndex(COL_NEXT_RUN));
        if (nextRunTime > 0) {
            job.setNextScheduledRun(new Date(nextRunTime));
        }

        return job;
    }

    /**
     * Helper method to convert BackupJob to ContentValues
     */
    private ContentValues getContentValuesFromJob(BackupJob job) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, job.getName());
        values.put(COL_LOCAL_DIR, job.getLocalDirectory());
        values.put(COL_REMOTE_ADDR, job.getRemoteAddress());
        values.put(COL_REMOTE_PORT, job.getRemotePort());
        values.put(COL_REMOTE_TARGET, job.getRemoteTarget());
        values.put(COL_LOGIN, job.getLogin());
        values.put(COL_PASSWORD, job.getPassword());

        // Store schedule
        Schedule schedule = job.getSchedule();
        if (schedule != null) {
            values.put(COL_SCHEDULE_TYPE, schedule.getType().ordinal());

            // Store days of month as comma-separated list
            StringBuilder daysOfMonthStr = new StringBuilder();
            boolean[] daysOfMonth = schedule.getDaysOfMonth();
            for (int i = 1; i <= 31; i++) {
                if (daysOfMonth[i]) {
                    if (daysOfMonthStr.length() > 0) daysOfMonthStr.append(",");
                    daysOfMonthStr.append(i);
                }
            }
            values.put(COL_DAYS_OF_MONTH, daysOfMonthStr.toString());

            // Store days of week as comma-separated list
            StringBuilder daysOfWeekStr = new StringBuilder();
            boolean[] daysOfWeek = schedule.getDaysOfWeek();
            for (int i = 0; i < 7; i++) {
                if (daysOfWeek[i]) {
                    if (daysOfWeekStr.length() > 0) daysOfWeekStr.append(",");
                    daysOfWeekStr.append(i);
                }
            }
            values.put(COL_DAYS_OF_WEEK, daysOfWeekStr.toString());

            values.put(COL_HOUR, schedule.getHourOfDay());
            values.put(COL_MINUTE, schedule.getMinuteOfHour());
        }

        // Store conditions
        values.put(COL_WIFI_ONLY, job.isWifiOnly() ? 1 : 0);
        values.put(COL_CHARGING_ONLY, job.isChargingOnly() ? 1 : 0);

        // Store run information
        values.put(COL_LAST_RUN, job.getLastRunTime() != null ? job.getLastRunTime().getTime() : 0);
        values.put(COL_LAST_DURATION, job.getLastRunDuration());
        values.put(COL_NEXT_RUN, job.getNextScheduledRun() != null ? job.getNextScheduledRun().getTime() : 0);

        return values;
    }
}
