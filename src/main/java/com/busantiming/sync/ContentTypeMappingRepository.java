package com.busantiming.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface ContentTypeMappingRepository extends JpaRepository<ContentTypeMapping, Integer> {

    @Query("SELECT c.contentTypeId FROM ContentTypeMapping c")
    Set<Integer> findAllContentTypeIds();
}
