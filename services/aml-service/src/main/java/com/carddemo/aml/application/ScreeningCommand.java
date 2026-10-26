package com.carddemo.aml.application;

/**
 * 筛查命令：姓名、国家、主体类型与可选客户号（命中时写入警报）。
 */
public record ScreeningCommand(String name, String country, String entityType, String customerId) {
}
