package com.github.app.dify.system.service;

public interface SkillAccessPolicyService {

    boolean canAccess(String skillKey, String role);
}
