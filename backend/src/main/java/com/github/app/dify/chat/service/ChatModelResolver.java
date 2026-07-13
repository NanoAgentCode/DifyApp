package com.github.app.dify.chat.service;

import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatModelResolver {
    private final QAModelRepository repository;

    public ChatModelResolver(QAModelRepository repository) {
        this.repository = repository;
    }

    public QAModel resolve(Long modelId) {
        if (modelId != null) {
            QAModel model = repository.findById(modelId)
                    .filter(this::availableForChat)
                    .orElseThrow(() -> new IllegalStateException("指定的模型不可用或未启用"));
            return model;
        }
        Optional<QAModel> defaultModel = repository.findDefaultByUseFor("chat");
        if (defaultModel.isPresent() && Boolean.TRUE.equals(defaultModel.get().getEnabled())) {
            return defaultModel.get();
        }
        List<QAModel> models = repository.findByUseFor("chat");
        return models.isEmpty() ? null : models.get(0);
    }

    private boolean availableForChat(QAModel model) {
        return (model.getDeleted() == null || model.getDeleted() == 0)
                && Boolean.TRUE.equals(model.getEnabled())
                && ("chat".equals(model.getUseFor()) || "both".equals(model.getUseFor()));
    }
}
