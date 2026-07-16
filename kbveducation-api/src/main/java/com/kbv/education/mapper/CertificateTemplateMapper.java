package com.kbv.education.mapper;

import com.kbv.education.dto.certificate.CertificateTemplateResponse;
import com.kbv.education.entity.CertificateTemplate;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CertificateTemplateMapper {

    CertificateTemplateResponse toResponse(CertificateTemplate template);
}
