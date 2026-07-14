package com.kbv.education.service;

import com.kbv.education.dto.scoreconfig.ScoreConfigResponse;
import com.kbv.education.dto.scoreconfig.UpdateScoreConfigRequest;

public interface ScoreConfigService {

    ScoreConfigResponse getActive();

    ScoreConfigResponse update(UpdateScoreConfigRequest request);
}
