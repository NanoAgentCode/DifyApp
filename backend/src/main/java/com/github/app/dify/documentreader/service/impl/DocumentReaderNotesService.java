package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.documentreader.domain.DocumentNotes;
import com.github.app.dify.documentreader.repository.DocumentNotesRepository;
import com.github.app.dify.documentreader.util.DocumentReaderDateTimeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentReaderNotesService {

    private final DocumentNotesRepository notesRepository;
    private final DocumentReaderAccessService accessService;

    DocumentReaderNotesService(DocumentNotesRepository notesRepository, DocumentReaderAccessService accessService) {
        this.notesRepository = notesRepository;
        this.accessService = accessService;
    }

    String getNotes(Long documentId, Long userId) {
        accessService.validateAccess(documentId, userId);
        return notesRepository.findByDocumentId(documentId).map(DocumentNotes::getContent).orElse("");
    }

    @Transactional
    void saveNotes(Long documentId, Long userId, String content) {
        accessService.validateAccess(documentId, userId);
        DocumentNotes notes = notesRepository.findByDocumentId(documentId).orElseGet(() -> {
            DocumentNotes created = new DocumentNotes();
            created.setDocumentId(documentId);
            DocumentReaderDateTimeUtil.setCreateAndUpdateTime(created);
            return created;
        });
        notes.setContent(content);
        DocumentReaderDateTimeUtil.setUpdateTime(notes);
        notesRepository.save(notes);
    }
}
