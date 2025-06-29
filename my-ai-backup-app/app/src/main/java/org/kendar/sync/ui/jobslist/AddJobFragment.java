package org.kendar.sync.ui.jobslist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.navigation.Navigation;

import org.kendar.sync.R;
import org.kendar.sync.model.Job;
import org.kendar.sync.util.JobsFileUtil;

import java.util.UUID;

public class AddJobFragment extends Fragment {

    private TextView uuidTextView;
    private EditText jobNameEditText;
    private EditText serverAddressEditText;
    private EditText serverPortEditText;
    private EditText uiServerPortEditText;
    private EditText loginEditText;
    private EditText passwordEditText;
    private EditText localSourceEditText;
    private Button localSourceButton;
    private EditText targetDestinationEditText;
    private Button targetDestinationButton;
    private EditText scheduleTimeEditText;
    private Button scheduleTimeButton;
    private Button saveButton;
    private Button cancelButton;

    private UUID jobUuid;
    private boolean isEditing = false; // Flag to check if we are editing an existing job

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        jobUuid = UUID.randomUUID();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_job, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        isEditing=false;
        initViews(view);
        setupListeners();

        serverPortEditText.setText("8090");
        uiServerPortEditText.setText("8089");
        serverAddressEditText.setText("192.168.1.20");
        loginEditText.setText("admin");
        passwordEditText.setText("admin");

        // Display UUID (not editable)
        uuidTextView.setText(jobUuid.toString());

        String jobId = null;

        try {
            jobId = getArguments().getString("jobId");
        }catch (Exception ex){}
        if(jobId!=null && !jobId.isEmpty()) {
            // If editing an existing job, load its details
            JobsFileUtil jfu = new JobsFileUtil(requireContext());
            Job job = jfu.getJobById(UUID.fromString(jobId));
            if (job != null) {
                isEditing= true;
                jobUuid = job.getId();
                uuidTextView.setText(jobUuid.toString());
                jobNameEditText.setText(job.getName());
                serverAddressEditText.setText(job.getServerAddress());
                serverPortEditText.setText(String.valueOf(job.getServerPort()));
                uiServerPortEditText.setText(String.valueOf(job.getUiServerPort()));
                loginEditText.setText(job.getLogin());
                passwordEditText.setText(job.getPassword());
                localSourceEditText.setText(job.getLocalSource());
                targetDestinationEditText.setText(job.getTargetDestination());
                scheduleTimeEditText.setText(job.getScheduleTime());
            }
        }
    }

    private void initViews(View view) {
        uuidTextView = view.findViewById(R.id.text_job_uuid);
        jobNameEditText = view.findViewById(R.id.edit_job_name);
        serverAddressEditText = view.findViewById(R.id.edit_server_address);
        serverPortEditText = view.findViewById(R.id.edit_server_port);
        uiServerPortEditText = view.findViewById(R.id.edit_ui_server_port);
        loginEditText = view.findViewById(R.id.edit_login);
        passwordEditText = view.findViewById(R.id.edit_password);
        localSourceEditText = view.findViewById(R.id.edit_local_source);
        localSourceButton = view.findViewById(R.id.button_local_source);
        targetDestinationEditText = view.findViewById(R.id.edit_target_destination);
        targetDestinationButton = view.findViewById(R.id.button_target_destination);
        scheduleTimeEditText = view.findViewById(R.id.edit_schedule_time);
        scheduleTimeButton = view.findViewById(R.id.button_schedule_time);
        saveButton = view.findViewById(R.id.button_save);
        cancelButton = view.findViewById(R.id.button_cancel);
    }

    private void setupListeners() {
        localSourceButton.setOnClickListener(v -> {
            navigateToLocalSourceBrowser();
        });

        targetDestinationButton.setOnClickListener(v -> {
            if (isServerInfoValid()) {
                navigateToRemoteTargetBrowser();
            } else {
                Toast.makeText(requireContext(), "Please fill in server address, port, login, and password", Toast.LENGTH_SHORT).show();
            }
        });

        scheduleTimeButton.setOnClickListener(v -> {
            navigateToScheduleFragment();
        });

        saveButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveJob();
            }
        });

        cancelButton.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigateUp();
        });

        getParentFragmentManager().setFragmentResultListener("remoteTargetBrowserResult", this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                // We use a String here, but any type that can be put in a Bundle is supported.
                targetDestinationEditText.setText(bundle.getString("targetDestination"));
                // Do something with the result.
            }
        });

        getParentFragmentManager().setFragmentResultListener("directoryBrowserResult", this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                // We use a String here, but any type that can be put in a Bundle is supported.
                localSourceEditText.setText(bundle.getString("localSource"));
                // Do something with the result.
            }
        });

        getParentFragmentManager().setFragmentResultListener("scheduleResult", this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                // We use a String here, but any type that can be put in a Bundle is supported.
                scheduleTimeEditText.setText(bundle.getString("scheduleTime"));
                // Do something with the result.
            }
        });
    }

    private boolean isServerInfoValid() {
        return !serverAddressEditText.getText().toString().isEmpty() &&
                !serverPortEditText.getText().toString().isEmpty() &&
                !loginEditText.getText().toString().isEmpty() &&
                !passwordEditText.getText().toString().isEmpty();
    }

    private void navigateToLocalSourceBrowser() {
        Bundle args = new Bundle();
        args.putString("currentPath", localSourceEditText.getText().toString());
        Navigation.findNavController(requireView()).navigate(R.id.action_addJobFragment_to_directoryBrowserFragment, args);
    }

    private void navigateToRemoteTargetBrowser() {
        Bundle args = new Bundle();
        args.putString("serverAddress", serverAddressEditText.getText().toString());
        args.putString("serverPort", uiServerPortEditText.getText().toString());
        args.putString("login", loginEditText.getText().toString());
        args.putString("password", passwordEditText.getText().toString());
        Navigation.findNavController(requireView()).navigate(R.id.action_addJobFragment_to_remoteTargetBrowserFragment, args);
    }

    private void navigateToScheduleFragment() {
        Bundle args = new Bundle();
        args.putString("currentSchedule", scheduleTimeEditText.getText().toString());
        Navigation.findNavController(requireView()).navigate(R.id.action_addJobFragment_to_scheduleFragment, args);
    }

    private boolean validateInputs() {
        if (jobNameEditText.getText().toString().isEmpty()) {
            Toast.makeText(requireContext(), "Job name is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (localSourceEditText.getText().toString().isEmpty()) {
            Toast.makeText(requireContext(), "Local source is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (targetDestinationEditText.getText().toString().isEmpty()) {
            Toast.makeText(requireContext(), "Target destination is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void saveJob() {
        Job job = new Job();
        job.setId(jobUuid);
        job.setName(jobNameEditText.getText().toString());
        job.setServerAddress(serverAddressEditText.getText().toString());
        job.setServerPort(Integer.parseInt(serverPortEditText.getText().toString()));
        job.setLogin(loginEditText.getText().toString());
        job.setPassword(passwordEditText.getText().toString());
        job.setLocalSource(localSourceEditText.getText().toString());
        job.setTargetDestination(targetDestinationEditText.getText().toString());
        job.setScheduleTime(scheduleTimeEditText.getText().toString());

        var jfu = new JobsFileUtil(requireContext());
        if(isEditing){
            jfu.updateJob(job);
        }else {
            jfu.addJob(job);
        }

        // Navigate back to job list
        Navigation.findNavController(requireView()).navigateUp();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Check for results from other fragments
        if (getArguments() != null) {
            if (getArguments().containsKey("localSource")) {
                String localSource = getArguments().getString("localSource");
                if (localSource != null) {
                    localSourceEditText.setText(localSource);
                }
                getArguments().remove("localSource");
            }

            if (getArguments().containsKey("targetDestination")) {
                String targetDestination = getArguments().getString("targetDestination");
                if (targetDestination != null) {
                    targetDestinationEditText.setText(targetDestination);
                }
                getArguments().remove("targetDestination");
            }

            if (getArguments().containsKey("scheduleTime")) {
                String scheduleTime = getArguments().getString("scheduleTime");
                if (scheduleTime != null) {
                    scheduleTimeEditText.setText(scheduleTime);
                }
                getArguments().remove("scheduleTime");
            }
        }
    }
}