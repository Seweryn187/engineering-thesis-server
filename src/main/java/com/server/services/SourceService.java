package com.server.services;

import com.server.entities.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.server.repositories.SourceRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class SourceService {
    private final SourceRepository sourceRepository;

    @Autowired
    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public List<Source> getAllSources() {
        List<Source> sourcesList = new ArrayList<>();
        sourceRepository.findAll().forEach(sourcesList::add);
        return sourcesList;
    }
}
