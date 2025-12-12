package com.github.app.dify.system.service;

import com.github.app.dify.system.req.DrawIOGenerateRequest;
import com.github.app.dify.system.req.DrawIOModifyRequest;
import com.github.app.dify.system.req.DrawIOSaveRequest;
import com.github.app.dify.system.req.DrawIOHistoryRequest;
import com.github.app.dify.system.resp.DrawIOGenerateResponse;
import com.github.app.dify.system.resp.DrawIODiagramResp;
import com.github.app.dify.system.resp.DrawIOHistoryResp;

import java.util.List;

/**
 * DrawIO 服务接口
 */
public interface DrawIOService {
    
    /**
     * 生成图表
     */
    DrawIOGenerateResponse generateDiagram(DrawIOGenerateRequest request, Long userId);
    
    /**
     * 修改图表
     */
    DrawIOGenerateResponse modifyDiagram(DrawIOModifyRequest request, Long userId);
    
    /**
     * 保存图表
     */
    DrawIODiagramResp saveDiagram(DrawIOSaveRequest request, Long userId);
    
    /**
     * 获取图表列表
     */
    List<DrawIODiagramResp> getDiagramList(Long userId);
    
    /**
     * 获取图表详情
     */
    DrawIODiagramResp getDiagramDetail(Long id, Long userId);
    
    /**
     * 删除图表
     */
    void deleteDiagram(Long id, Long userId);
    
    /**
     * 保存历史记录
     */
    DrawIOHistoryResp saveHistory(DrawIOHistoryRequest request, Long userId);
    
    /**
     * 获取历史记录列表
     */
    List<DrawIOHistoryResp> getHistoryList(Long userId);
    
    /**
     * 删除历史记录
     */
    void deleteHistory(Long id, Long userId);
}

