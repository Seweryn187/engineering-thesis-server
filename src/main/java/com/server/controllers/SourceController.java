package com.server.controllers;

import com.server.entities.Source;
import com.server.utility.Utility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.server.services.SourceService;

import java.util.List;

@RestController
public class SourceController {
    private final SourceService sourceService;

    @Autowired
    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @CrossOrigin(origins = Utility.serverUrl, maxAge = 3600)
    @GetMapping("/sources")
    public List<Source> getAllSources() {
        return sourceService.getAllSources();
    }
}
