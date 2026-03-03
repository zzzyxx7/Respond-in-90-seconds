package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;

public interface AuthService {

    Result<LoginResponse> login(LoginRequest request);
}

