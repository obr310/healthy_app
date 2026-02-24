package org.bupt.demoapp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.bupt.demoapp.entity.User;
@Mapper
public interface UserMapper {
    /**
     * 根据用户名查找密码
     * @param username
     * @return
     */

    User findByUsername(@Param("username") String username);

    /**
     * 插入用户信息
     * @param user
     * @return
     */
    int insert(User user);

}
