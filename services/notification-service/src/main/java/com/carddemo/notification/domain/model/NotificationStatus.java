package com.carddemo.notification.domain.model;

/**
 * 通知状态：创建即 PENDING，投递成功 SENT、失败 FAILED；SENT/FAILED 为终态，重复发送不再改变。
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
