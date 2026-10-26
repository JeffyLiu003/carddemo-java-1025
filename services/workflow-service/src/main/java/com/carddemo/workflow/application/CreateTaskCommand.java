package com.carddemo.workflow.application;

/**
 * 建任务命令：definitionCode 必须对应已存在的 active 定义，businessRef/assignee 必填。
 */
public record CreateTaskCommand(String definitionCode, String businessRef, String assignee) {
}
