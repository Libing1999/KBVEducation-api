package com.kbv.education.mapper;

import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.entity.Certificate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CertificateMapper {

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentName", source = "student.fullName")
    @Mapping(target = "cohortName", source = "cohort.name")
    @Mapping(target = "issuedAt", source = "createdAt")
    CertificateResponse toResponse(Certificate certificate);
}
