package com.server.repositories;

import com.server.entities.Source;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceRepository extends CrudRepository<Source, Integer> {
}