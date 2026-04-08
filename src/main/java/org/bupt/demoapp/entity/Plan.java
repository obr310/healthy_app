package org.bupt.demoapp.entity;

import java.time.LocalDateTime;

/**
 * 用户的健康计划
 */
public class Plan {

    private Long planId;       // 计划唯一标识（Snowflake ID）
    private String userId;     // 所属用户ID
    private String planType;   // 计划类型，如：饮食计划、运动计划、睡眠计划
    private String planContent;// 计划正文内容
    private String planStatus; // 计划状态：DRAFT（草稿）/ ACTIVE（生效）/ COMPLETED（已完成）
    private LocalDateTime createTime;
    private LocalDateTime updateTime; // 最后一次更新时间，初始为 null

    public Long getPlanId() {
        return planId;
    }
    public void setPlanId(Long planId) {
        this.planId = planId;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getPlanType() {
        return planType;
    }
    public void setPlanType(String planType) {
        this.planType = planType;
    }
    public String getPlanContent() {
        return planContent;
    }
    public void setPlanContent(String planContent) {
        this.planContent = planContent;
    }
    public String getPlanStatus() {
        return planStatus;
    }
    public void setPlanStatus(String planStatus) {
        this.planStatus = planStatus;
    }
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}




















