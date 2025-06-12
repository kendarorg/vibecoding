package com.kendar.sync.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface RemoteApiService {
    @POST("/api/auth/login")
    Call<String> doLogin(@Body String requestBody);


    @GET("/api/settings/folders")
    Call<String> getRemotePaths(@Header("X-Auth-Token") String token);
}