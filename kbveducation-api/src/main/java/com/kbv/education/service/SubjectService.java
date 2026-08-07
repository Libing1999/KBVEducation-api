package com.kbv.education.service;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.subject.SubjectRequest;
import com.kbv.education.dto.subject.SubjectResponse;

import java.util.List;
import java.util.UUID;

/** Admin configuration of the subjects offered in the practice log Subject dropdown. */
public interface SubjectService {

    List<SubjectResponse> listAll();

    List<SubjectResponse> listEnabled();

    SubjectResponse create(SubjectRequest request);

    SubjectResponse update(UUID id, SubjectRequest request);

    SubjectResponse setEnabled(UUID id, boolean enabled);

    void reorder(ReorderRequest request);

    void delete(UUID id);
}
