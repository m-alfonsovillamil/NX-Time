package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import java.util.List;

public interface AbsenceService {

    AbsenceResponse createRequest(String email, AbsenceRequestDTO requestDTO);

    List<AbsenceResponse> getMyRequests(String email);

    List<AbsenceResponse> getPendingRequests(String managerEmail);

    AbsenceResponse changeRequestStatus(String managerEmail, long requestId, AbsenceStatus newStatus);

    List<AbsenceResponse> getHistory(String managerEmail);
}
