package org.kendar.sync.ui.browser.remote;

import android.os.Bundle;
import android.util.Log;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.kendar.sync.R;

import org.json.JSONObject;
import org.kendar.sync.lib.model.ServerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RemoteTargetBrowserFragment extends Fragment implements RemotePathAdapter.OnPathSelectedListener {

    private static final Logger log = LoggerFactory.getLogger(RemoteTargetBrowserFragment.class);
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
        executorService = Executors.newFixedThreadPool(3);
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

    private void fakeFetchRemotePaths() {
        // This method is a placeholder for the actual network call.
        // In a real application, you would replace this with the actual network request.
        List<String> fakePaths = new ArrayList<>();
        fakePaths.add("First");
        fakePaths.add("Second");
        fakePaths.add("Third");

        adapter.updatePaths(fakePaths);
    }

    private boolean fakeApi= false;


    private ExecutorService executorService;

    private void fetchRemotePaths() {
        if (fakeApi) {
            fakeFetchRemotePaths();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);


        executorService.execute(()->{
            HttpURLConnection client = null;
            try {
                JSONObject requestBody = new JSONObject();
                // Add credentials to request if needed
                requestBody.put("username", login);
                requestBody.put("password", password);

                var url = new URL("http://" + serverAddress + ":" + serverPort+"/api/auth/login");
                client = (HttpURLConnection) url.openConnection();
                client.setRequestMethod("POST");
                client.setRequestProperty("Content-Type", "application/json");
                var data = requestBody.toString().getBytes("UTF-8");
                //client.setFixedLengthStreamingMode(data.length);
                //client.setChunkedStreamingMode(0);
                client.setDoOutput(true);
                var outputPost = new BufferedOutputStream(client.getOutputStream());


                outputPost.write(data);
                outputPost.flush();
                outputPost.close();

                BufferedReader br = null;
                if (client.getResponseCode()!= HttpURLConnection.HTTP_OK) {
                    this.getView().post(()->{
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Login failed", Toast.LENGTH_SHORT).show();
                    });
                    return;
                } else {
                    br = new BufferedReader(new InputStreamReader(client.getInputStream()));
                }
                var sb = new StringBuilder();
                String output;
                while ((output = br.readLine()) != null) {
                    sb.append(output);
                }
                var result = new JSONObject(sb.toString());
                var token = result.getString("token");

                url = new URL("http://" + serverAddress + ":" + serverPort+"/api/settings/folders");
                client = (HttpURLConnection) url.openConnection();
                client.setRequestMethod("GET");
                client.setRequestProperty("X-Auth-Token", token);
                client.setRequestProperty("Content-Type", "application/json");
                //client.setFixedLengthStreamingMode(0);
               // client.setChunkedStreamingMode(0);
               /* client.setDoOutput(true);
                outputPost = new BufferedOutputStream(client.getOutputStream());
                outputPost.flush();
                outputPost.close();
*/
                br = null;
                if (client.getResponseCode()!= HttpURLConnection.HTTP_OK) {
                    this.getView().post(()->{
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Login failed", Toast.LENGTH_SHORT).show();
                    });
                    return;
                } else {
                    br = new BufferedReader(new InputStreamReader(client.getInputStream()));
                }
                sb = new StringBuilder();
                while ((output = br.readLine()) != null) {
                    sb.append(output);
                }

                Gson gson = new GsonBuilder().create();
                var resultPaths = gson.fromJson(sb.toString(), ServerSettings.BackupFolder[].class);
                var paths = Arrays.stream(resultPaths).map(ServerSettings.BackupFolder::getVirtualName).toList();
                this.getView().post(()->{
                    adapter.updatePaths(paths);
                    progressBar.setVisibility(View.GONE);
                });

            }catch (Exception e){
                log.error("Error fetching remote paths", e);
                this.getView().post(()->{
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

            }finally {
                if (client != null) {
                    client.disconnect();
                }
            }
        });

    }

    @Override
    public void onPathSelected(String path) {
        selectedPath = path;
    }

    @Override
    public void onDestroy() {
        executorService.shutdown();
        super.onDestroy();
    }
}