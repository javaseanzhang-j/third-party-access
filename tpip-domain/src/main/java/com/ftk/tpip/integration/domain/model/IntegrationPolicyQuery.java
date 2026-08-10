package com.ftk.tpip.integration.domain.model;
public record IntegrationPolicyQuery(Long bindingId,String keyword,PolicyStatus status,int offset,int limit){public IntegrationPolicyQuery{keyword=keyword==null||keyword.isBlank()?null:keyword.trim();if(offset<0||limit<1||limit>200)throw new IllegalArgumentException("invalid pagination");}}
