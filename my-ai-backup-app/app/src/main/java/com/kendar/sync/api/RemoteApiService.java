package com.kendar.sync.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface RemoteApiService {
    @POST("/api/test")
    Call<List<String>> getRemotePaths(@Body String requestBody);
}