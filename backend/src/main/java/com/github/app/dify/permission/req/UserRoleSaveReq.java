package com.github.app.dify.permission.req;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserRoleSaveReq {
    private List<Long> roleIds;
}
