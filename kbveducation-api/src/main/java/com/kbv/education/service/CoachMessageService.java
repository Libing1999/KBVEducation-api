package com.kbv.education.service;

import com.kbv.education.dto.message.CoachMessageResponse;
import com.kbv.education.dto.message.ParentMessageResponse;
import com.kbv.education.dto.message.SendMessageRequest;
import com.kbv.education.dto.message.StudentMessageResponse;
import com.kbv.education.dto.response.PageResponse;

import java.util.List;
import java.util.UUID;

public interface CoachMessageService {

    /** Staff compose/send (SUPER_ADMIN). Validates the target matches {@code targetType}. */
    CoachMessageResponse send(UUID senderId, SendMessageRequest request);

    /** Recently sent messages, newest first (admin panel). */
    PageResponse<CoachMessageResponse> adminList(int page, int size);

    /**
     * Messages addressed to this student individually, or to their active
     * cohort collectively — never another student's or another cohort's,
     * regardless of what the caller requests (there's no id parameter to
     * manipulate; the student is resolved from the authenticated principal).
     * Newest first, each with a read flag computed for this student.
     */
    List<StudentMessageResponse> listForStudent(UUID studentId);

    /** Marks one message read for this student. 404s if the message isn't addressed to them. */
    void markReadForStudent(UUID studentId, UUID messageId);

    /**
     * Messages addressed to one of the parent's linked students (individually or via
     * their cohort), newest first, capped to a handful of recent notes. The student is
     * resolved and validated server-side against the parent's own links — a parent can
     * never fetch another student's messages. {@code requestedStudentId} selects which
     * child for a multi-child parent; null defaults to their first-linked child.
     */
    List<ParentMessageResponse> listForParent(UUID parentUserId, UUID requestedStudentId);
}
