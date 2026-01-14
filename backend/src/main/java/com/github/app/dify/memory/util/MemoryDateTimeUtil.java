package com.github.app.dify.memory.util;

import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.memory.domain.UserMemory;

import java.util.Date;

public class MemoryDateTimeUtil {

    public static void setCreateAndUpdateTime(UserMemory memory) {
        Date now = DateTimeUtil.now();
        memory.setCreateTime(now);
        memory.setUpdateTime(now);
    }

    public static void setUpdateTime(UserMemory memory) {
        memory.setUpdateTime(DateTimeUtil.now());
    }
}

