package org.bupt.demoapp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.bupt.demoapp.entity.Plan;

@Mapper
public interface PlanMapper {

    /**
     * 插入一条新计划
     * @param plan 计划对象（planId 由调用方通过 Snowflake 生成）
     * @return 影响的行数，成功为 1
     */
    int insert(Plan plan);

    /**
     * 根据计划ID查询计划
     * @param planId 计划ID
     * @return 计划对象，不存在则返回 null
     */
    Plan findById(@Param("planId") Long planId);

    /**
     * 更新计划内容和状态
     * @param plan 包含 planId、planContent、planStatus、updateTime 的计划对象
     * @return 影响的行数，成功为 1
     */
    int update(Plan plan);
}

