package com.kbv.education.mapper;

import com.kbv.education.dto.auth.AuthUserResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.entity.User;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for {@link User}. Entities are never exposed directly.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", source = "role.name")
    AuthUserResponse toAuthUser(User user);

    @Mapping(target = "role", source = "role.name")
    UserResponse toUserResponse(User user);
}
