package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectById(@Param("id") Long id);

    int insert(User user);

    int updatePasswordById(@Param("id") Long id, @Param("password") String password);

    int updateUsernameById(@Param("id") Long id, @Param("username") String username);

    int updateAvatarUrlById(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}

