package com.github.app.dify.memory.util;

import com.github.app.dify.common.util.EntityLifecycleUtil;
import com.github.app.dify.memory.domain.UserMemory;

public class MemoryDateTimeUtil {

    public static void setCreateAndUpdateTime(UserMemory memory) {
        EntityLifecycleUtil.setCreateAndUpdateTime(memory);
    }

    public static void setUpdateTime(UserMemory memory) {
        EntityLifecycleUtil.setUpdateTime(memory);
    }
}
