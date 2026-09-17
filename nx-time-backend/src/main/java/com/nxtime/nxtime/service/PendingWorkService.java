package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PendingWorkResponse;

/** Los contadores de las bandejas del panel de gestión. Ver {@link PendingWorkResponse}. */
public interface PendingWorkService {

    PendingWorkResponse contar(User actor);
}
