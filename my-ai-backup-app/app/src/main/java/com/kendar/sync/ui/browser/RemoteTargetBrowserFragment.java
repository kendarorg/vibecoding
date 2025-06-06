package com.kendar.sync.ui.browser;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;
import com.kendar.sync.api.RemoteApiService;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RemoteTargetBrowserFragment extends Fragment implements RemotePathAdapter.OnPathSelectedListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private Button okButton;
    private Button cancelButton;

    private RemotePathAdapter adapter;
    private String selectedPath;

    private String serverAddress;
    private String serverPort;
    private String login;
    private String password;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_remote_target_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_remote_paths);
        progressBar = view.findViewById(R.id.progress_bar);
        okButton = view.findViewById(R.id.button_ok);
        cancelButton = view.findViewById(R.id.button_cancel);

        // Get server info from arguments
        if (getArguments() != null) {
            serverAddress = getArguments().getString("serverAddress");
            serverPort = getArguments().getString("serverPort");
            login = getArguments().getString("login");
            password = getArguments().getString("password");
        }

        // Setup recycler view
        adapter = new RemotePathAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Fetch remote paths
        fetchRemotePaths();

        okButton.setOnClickListener(v -> {
            if (selectedPath != null) {
                Bundle result = new Bundle();
                result.putString("targetDestination", selectedPath);
                getParentFragmentManager().setFragmentResult("remoteTargetBrowserResult", result);
                Navigation.findNavController(requireView()).navigateUp();
            } else {
                Toast.makeText(requireContext(), "Please select a target path", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("targetDestination", null);
            getParentFragmentManager().setFragmentResult("remoteTargetBrowserResult", result);
            Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private void fetchRemotePaths() {
        progressBar.setVisibility(View.VISIBLE);

        // Create Retrofit instance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + serverAddress + ":" + serverPort)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RemoteApiService apiService = retrofit.create(RemoteApiService.class);

        try {
            JSONObject requestBody = new JSONObject();
            // Add credentials to request if needed
            requestBody.put("login", login);
            requestBody.put("password", password);

            apiService.getRemotePaths(requestBody.toString()).enqueue(new Callback<List<String>>() {
                @Override
                public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                    progressBar.setVisibility(View.GONE);

                    if (response.isSuccessful() && response.body() != null) {
                        adapter.updatePaths(response.body());
                    } else {
                        Toast.makeText(requireContext(), "Error fetching remote paths", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<List<String>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Failed to connect to server", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPathSelected(String path) {
        selectedPath = path;
    }
}